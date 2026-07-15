package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.model.Action;
import org.zhengyan.ontology.platform.model.Workflow;
import org.zhengyan.ontology.platform.repository.ActionRepository;
import org.zhengyan.ontology.platform.repository.WorkflowRepository;

import java.util.*;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final int MAX_NODES = 50;

    private final WorkflowRepository workflowRepository;
    private final ActionRepository actionRepository;
    private final ActionService actionService;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowRepository workflowRepository,
                           ActionRepository actionRepository,
                           ActionService actionService,
                           ObjectMapper objectMapper) {
        this.workflowRepository = workflowRepository;
        this.actionRepository = actionRepository;
        this.actionService = actionService;
        this.objectMapper = objectMapper;
    }

    public List<Workflow> listWorkflows(String tenantId) {
        return workflowRepository.findByTenantId(tenantId);
    }

    public Workflow getWorkflow(String id) {
        return workflowRepository.findById(id);
    }

    public Workflow createWorkflow(Workflow workflow) {
        String error = validateDag(workflow.getDagJson());
        if (error != null) {
            throw new IllegalArgumentException("Invalid DAG: " + error);
        }
        workflowRepository.save(workflow);
        return workflow;
    }

    public Workflow updateWorkflow(String id, Workflow update) {
        Workflow existing = workflowRepository.findById(id);
        if (existing == null) return null;
        String error = validateDag(update.getDagJson());
        if (error != null) {
            throw new IllegalArgumentException("Invalid DAG: " + error);
        }
        existing.setName(update.getName());
        existing.setDagJson(update.getDagJson());
        existing.setEnabled(update.isEnabled());
        workflowRepository.save(existing);
        return existing;
    }

    public boolean deleteWorkflow(String id) {
        Workflow existing = workflowRepository.findById(id);
        if (existing == null) return false;
        workflowRepository.deleteById(id);
        return true;
    }

    public WorkflowResult execute(Workflow workflow) {
        Dag dag = parseDag(workflow.getDagJson());
        if (dag == null) {
            return new WorkflowResult(false, "Invalid DAG JSON", List.of());
        }

        List<String> sorted;
        try {
            sorted = topologicalSort(dag);
        } catch (IllegalArgumentException e) {
            return new WorkflowResult(false, e.getMessage(), List.of());
        }

        List<StepResult> stepResults = new ArrayList<>();
        boolean overallSuccess = true;

        Set<String> executed = new HashSet<>();
        for (String nodeId : sorted) {
            DagNode node = dag.nodeMap().get(nodeId);
            if (node == null) continue;

            Action action = actionRepository.findById(node.actionId());
            if (action == null) {
                stepResults.add(new StepResult(nodeId, node.actionId(), false, "Action not found: " + node.actionId()));
                overallSuccess = false;
                break;
            }

            ActionService.ActionResult result = actionService.execute(action, false);
            stepResults.add(new StepResult(nodeId, node.actionId(), result.success(), result.message()));

            if (!result.success()) {
                overallSuccess = false;
                log.warn("Workflow [{}] step [{}] failed: {}", workflow.getName(), nodeId, result.message());
                break;
            }
            executed.add(nodeId);
        }

        log.info("Workflow [{}] executed: success={}, steps={}", workflow.getName(), overallSuccess, stepResults.size());
        return new WorkflowResult(overallSuccess, overallSuccess ? "Workflow completed" : "Workflow failed", stepResults);
    }

    public String validateDag(String dagJson) {
        if (dagJson == null || dagJson.isBlank()) {
            return "DAG JSON is empty";
        }
        Dag dag = parseDag(dagJson);
        if (dag == null) {
            return "Invalid JSON format";
        }
        if (dag.nodes().isEmpty()) {
            return "DAG must have at least one node";
        }
        if (dag.nodes().size() > MAX_NODES) {
            return "DAG exceeds maximum of " + MAX_NODES + " nodes";
        }
        try {
            topologicalSort(dag);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    private List<String> topologicalSort(Dag dag) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjacency = new HashMap<>();

        for (DagNode node : dag.nodes()) {
            inDegree.put(node.id(), 0);
            adjacency.put(node.id(), new ArrayList<>());
        }

        for (DagEdge edge : dag.edges()) {
            if (!adjacency.containsKey(edge.from()) || !inDegree.containsKey(edge.to())) {
                throw new IllegalArgumentException("Edge references unknown node: " + edge.from() + " -> " + edge.to());
            }
            adjacency.get(edge.from()).add(edge.to());
            inDegree.merge(edge.to(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String neighbor : adjacency.getOrDefault(node, List.of())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != dag.nodes().size()) {
            throw new IllegalArgumentException("Cycle detected in DAG");
        }

        return sorted;
    }

    private Dag parseDag(String dagJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(dagJson, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodesRaw = (List<Map<String, Object>>) map.getOrDefault("nodes", List.of());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> edgesRaw = (List<Map<String, Object>>) map.getOrDefault("edges", List.of());

            List<DagNode> nodes = nodesRaw.stream()
                    .map(n -> new DagNode(
                            (String) n.get("id"),
                            (String) n.get("actionId")))
                    .toList();

            Map<String, DagNode> nodeMap = new HashMap<>();
            for (DagNode n : nodes) {
                nodeMap.put(n.id(), n);
            }

            List<DagEdge> edges = edgesRaw.stream()
                    .map(e -> new DagEdge(
                            (String) e.get("from"),
                            (String) e.get("to")))
                    .toList();

            return new Dag(nodes, edges, nodeMap);
        } catch (Exception e) {
            return null;
        }
    }

    private record Dag(List<DagNode> nodes, List<DagEdge> edges, Map<String, DagNode> nodeMap) {}
    private record DagNode(String id, String actionId) {}
    private record DagEdge(String from, String to) {}

    public record WorkflowResult(boolean success, String message, List<StepResult> steps) {}
    public record StepResult(String nodeId, String actionId, boolean success, String message) {}
}
