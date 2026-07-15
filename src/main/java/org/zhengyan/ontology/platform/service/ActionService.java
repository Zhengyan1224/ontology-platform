package org.zhengyan.ontology.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.TenantConfig;
import org.zhengyan.ontology.platform.model.Action;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.ActionRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ActionService {

    private static final Logger log = LoggerFactory.getLogger(ActionService.class);

    private final ActionRepository actionRepository;
    private final TenantConfig tenantConfig;
    private final TenantPersistenceService tenantPersistenceService;
    private final SqlExecutionService sqlExecutionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ActionService(ActionRepository actionRepository,
                         TenantConfig tenantConfig,
                         TenantPersistenceService tenantPersistenceService,
                         SqlExecutionService sqlExecutionService,
                         ObjectMapper objectMapper) {
        this.actionRepository = actionRepository;
        this.tenantConfig = tenantConfig;
        this.tenantPersistenceService = tenantPersistenceService;
        this.sqlExecutionService = sqlExecutionService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Action> listActions(String tenantId) {
        return actionRepository.findByTenantId(tenantId);
    }

    public Action getAction(String id) {
        return actionRepository.findById(id);
    }

    public Action createAction(Action action) {
        actionRepository.save(action);
        return action;
    }

    public Action updateAction(String id, Action update) {
        Action existing = actionRepository.findById(id);
        if (existing == null) return null;
        existing.setName(update.getName());
        existing.setType(update.getType());
        existing.setConfigJson(update.getConfigJson());
        actionRepository.save(existing);
        return existing;
    }

    public boolean deleteAction(String id) {
        Action existing = actionRepository.findById(id);
        if (existing == null) return false;
        actionRepository.deleteById(id);
        return true;
    }

    public ActionResult execute(Action action, boolean dryRun) {
        return switch (action.getType()) {
            case "sql_exec" -> executeSql(action, dryRun);
            case "api_call" -> executeApi(action, dryRun);
            case "notification" -> executeNotification(action, dryRun);
            default -> new ActionResult(false, "Unknown action type: " + action.getType(), null);
        };
    }

    private ActionResult executeSql(Action action, boolean dryRun) {
        Map<String, Object> config = parseConfig(action.getConfigJson());
        String sql = (String) config.getOrDefault("sql", "");

        if (dryRun) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("sql", sql);
            preview.put("type", "sql_exec");
            preview.put("dryRun", true);
            String upper = sql.trim().toUpperCase();
            preview.put("isQuery", upper.startsWith("SELECT") || upper.startsWith("WITH"));
            return new ActionResult(true, "Dry-run preview", preview);
        }

        Tenant tenant = findTenant(action.getTenantId());
        if (tenant == null) {
            return new ActionResult(false, "Tenant not found: " + action.getTenantId(), null);
        }

        SqlExecutionService.SqlResult result = sqlExecutionService.execute(tenant, sql);
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("success", result.success());
        resultMap.put("columns", result.columns());
        resultMap.put("rowCount", result.rowCount());
        resultMap.put("executionTimeMs", result.executionTimeMs());
        resultMap.put("error", result.error());
        resultMap.put("rows", result.rows());

        log.info("SQL action [{}] executed: {} rows affected", action.getName(), result.rowCount());
        return new ActionResult(result.success(), result.success() ? "SQL executed" : result.error(), resultMap);
    }

    private ActionResult executeApi(Action action, boolean dryRun) {
        Map<String, Object> config = parseConfig(action.getConfigJson());
        String url = (String) config.getOrDefault("url", "");
        String method = (String) config.getOrDefault("method", "GET");
        String bodyTemplate = (String) config.getOrDefault("bodyTemplate", "");

        if (dryRun) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("url", url);
            preview.put("method", method);
            preview.put("type", "api_call");
            preview.put("dryRun", true);
            return new ActionResult(true, "Dry-run preview", preview);
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30));

            switch (method.toUpperCase()) {
                case "POST" -> builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyTemplate));
                case "PUT" -> builder.header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(bodyTemplate));
                case "DELETE" -> builder.DELETE();
                default -> builder.GET();
            }

            HttpResponse<String> resp = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("statusCode", resp.statusCode());
            resultMap.put("body", resp.body().length() > 1000 ? resp.body().substring(0, 1000) : resp.body());

            log.info("API action [{}] executed: {} {}", action.getName(), method, url);
            return new ActionResult(resp.statusCode() < 500, "HTTP " + resp.statusCode(), resultMap);
        } catch (Exception e) {
            log.warn("API action [{}] failed: {}", action.getName(), e.getMessage());
            return new ActionResult(false, "API call failed: " + e.getMessage(), null);
        }
    }

    private ActionResult executeNotification(Action action, boolean dryRun) {
        Map<String, Object> config = parseConfig(action.getConfigJson());
        String message = (String) config.getOrDefault("message", "");

        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("message", message);
        resultMap.put("type", "notification");

        log.info("Notification action [{}]: {}", action.getName(), message);
        return new ActionResult(true, "Notification logged", resultMap);
    }

    private Map<String, Object> parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) return Map.of();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(configJson, Map.class);
            return map;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse action config JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private Tenant findTenant(String tenantId) {
        return tenantConfig.getTenants().stream()
                .filter(t -> t.getId().equals(tenantId))
                .findFirst()
                .orElseGet(() -> tenantPersistenceService.findById(tenantId));
    }

    public record ActionResult(boolean success, String message, Map<String, Object> details) {}
}
