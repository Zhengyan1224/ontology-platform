package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepositoryConnection;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQueryResult;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zhengyan.ontology.platform.model.SparqlQueryResult.QueryType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OntopEngine implements OntologyEngine {

    private static final Logger log = LoggerFactory.getLogger(OntopEngine.class);

    private final Tenant tenant;
    private Repository repo;
    private OntopSQLOWLAPIConfiguration config;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public OntopEngine(Tenant tenant) {
        this.tenant = tenant;
    }

    private final List<Path> tempFiles = new ArrayList<>();

    @Override
    public void initialize(Tenant tenant) throws Exception {
        if (initialized.get()) {
            throw new IllegalStateException("Engine already initialized for tenant: " + tenant.getId());
        }

        String owlPath = resolveContentOrPath(tenant.getOwlContent(), tenant.resolveOwlPath(), "-owl", ".ttl");
        String obdaPath = resolveContentOrPath(tenant.getObdaContent(), tenant.resolveObdaPath(), "-obda", ".obda");
        String propPath = createTempProperties(tenant);

        log.info("Initializing Ontop engine for tenant [{}]: owl={}, obda={}",
                tenant.getId(), owlPath, obdaPath);

        File owlFile = new File(owlPath);
        if (!owlFile.exists()) {
            log.warn("OWL file does not exist for tenant [{}]: {}", tenant.getId(), owlFile.getAbsolutePath());
        }
        File obdaFile = new File(obdaPath);
        if (!obdaFile.exists()) {
            log.warn("OBDA file does not exist for tenant [{}]: {}", tenant.getId(), obdaFile.getAbsolutePath());
        }

        try {
            config = OntopSQLOWLAPIConfiguration.defaultBuilder()
                    .ontologyFile(owlPath)
                    .nativeOntopMappingFile(obdaPath)
                    .propertyFile(new File(propPath).toURI().toString())
                    .enableTestMode()
                    .build();

            repo = OntopRepository.defaultRepository(config);
            repo.init();

            initialized.set(true);
            log.info("Ontop engine initialized successfully for tenant [{}]", tenant.getId());
        } catch (Exception e) {
            log.error("Ontop initialization failed for tenant [{}]: {}", tenant.getId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public SparqlQueryResult executeQuery(String sparql) throws Exception {
        assertInitialized();
        long start = System.currentTimeMillis();

        RepositoryConnection conn = repo.getConnection();
        QueryType queryType = getQueryType(sparql);
        try {
            return switch (queryType) {
                case BOOLEAN -> executeBooleanQuery(conn, sparql, start);
                case CONSTRUCT, DESCRIBE -> executeGraphQuery(conn, sparql, start, queryType);
                default -> executeTupleQuery(conn, sparql, start);
            };
        } finally {
            closeConnection(conn);
        }
    }

    private SparqlQueryResult executeGraphQuery(RepositoryConnection conn, String sparql, long start, SparqlQueryResult.QueryType queryType) throws Exception {
        GraphQueryResult graphResult = conn.prepareGraphQuery(QueryLanguage.SPARQL, sparql).evaluate();
        try {
            Model model = new TreeModel();
            while (graphResult.hasNext()) {
                model.add(graphResult.next());
            }
            long elapsed = System.currentTimeMillis() - start;
            SparqlQueryResult result = new SparqlQueryResult(queryType, model, elapsed);
            try {
                result.setTranslatedSql(translateToSql(sparql));
            } catch (Exception e) {
                result.setTranslatedSql("[CONSTRUCT query - SQL translation not available]");
            }
            return result;
        } finally {
            try { ((AutoCloseable) graphResult).close(); } catch (Exception ignored) { }
        }
    }

    private SparqlQueryResult executeTupleQuery(RepositoryConnection conn, String sparql, long start) throws Exception {
        List<String> variables = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        TupleQueryResult tupleResult = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparql).evaluate();
        try {
            variables.addAll(tupleResult.getBindingNames());
            while (tupleResult.hasNext()) {
                BindingSet bs = tupleResult.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (String vn : variables) {
                    Binding binding = bs.getBinding(vn);
                    row.put(vn, binding != null ? binding.getValue().stringValue() : null);
                }
                rows.add(row);
            }
        } finally {
            try { ((AutoCloseable) tupleResult).close(); } catch (Exception ignored) { }
        }

        long elapsed = System.currentTimeMillis() - start;
        SparqlQueryResult queryResult = new SparqlQueryResult(variables, rows, elapsed);
        queryResult.setTranslatedSql(translateToSql(sparql));
        return queryResult;
    }

    private SparqlQueryResult executeBooleanQuery(RepositoryConnection conn, String sparql, long start) throws Exception {
        BooleanQuery booleanQuery = conn.prepareBooleanQuery(QueryLanguage.SPARQL, sparql);
        boolean result;
        try {
            result = booleanQuery.evaluate();
        } finally {
            try { ((AutoCloseable) booleanQuery).close(); } catch (Exception ignored) { }
        }
        long elapsed = System.currentTimeMillis() - start;
        SparqlQueryResult queryResult = new SparqlQueryResult(result, elapsed);
        try {
            queryResult.setTranslatedSql(translateToSql(sparql));
        } catch (Exception e) {
            queryResult.setTranslatedSql("[ASK query - SQL translation not available]");
        }
        return queryResult;
    }

    private QueryType getQueryType(String sparql) {
        String trimmed = sparql.trim().toUpperCase();
        if (trimmed.startsWith("ASK")) return QueryType.BOOLEAN;
        if (trimmed.startsWith("CONSTRUCT")) return QueryType.CONSTRUCT;
        if (trimmed.startsWith("DESCRIBE")) return QueryType.DESCRIBE;
        return QueryType.SELECT;
    }

    @Override
    public String translateToSql(String sparql) throws Exception {
        assertInitialized();
        RepositoryConnection conn = repo.getConnection();
        try {
            return ((OntopRepositoryConnection) conn).reformulateIntoNativeQuery(sparql);
        } finally {
            closeConnection(conn);
        }
    }

    @Override
    public Map<String, Object> getOntologyInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tenantId", tenant.getId());
        info.put("tenantName", tenant.getName());
        info.put("owlPath", tenant.resolveOwlPath());
        info.put("obdaPath", tenant.resolveObdaPath());
        info.put("jdbcUrl", tenant.getJdbcUrl());
        info.put("jdbcDriver", tenant.getJdbcDriver());
        info.put("initialized", initialized.get());
        return info;
    }

    @Override
    public boolean isHealthy() {
        if (!initialized.get() || repo == null) return false;
        RepositoryConnection conn = null;
        try {
            conn = repo.getConnection();
            return conn.isOpen();
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) { /* connection already closed */ }
        }
    }

    @Override
    public void destroy() {
        if (repo != null) {
            repo.shutDown();
            repo = null;
        }
        for (Path tempFile : tempFiles) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception e) {
                log.warn("Failed to delete temp file: {}", tempFile, e);
            }
        }
        tempFiles.clear();
        initialized.set(false);
        log.info("Ontop engine destroyed for tenant [{}]", tenant.getId());
    }

    @Override
    public String getTenantId() {
        return tenant.getId();
    }

    public String checkHealth() {
        if (!initialized.get() || repo == null) return "not_initialized";
        RepositoryConnection conn = null;
        try {
            conn = repo.getConnection();
            return conn.isOpen() ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) { /* connection already closed */ }
        }
    }

    private void assertInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("Engine not initialized for tenant: " + tenant.getId());
        }
    }

    private String resolvePath(String path) {
        if (path == null || path.isBlank()) return path;
        File f = new File(path);
        if (!f.isAbsolute()) {
            f = new File("src/main/resources", path);
        }
        return f.toURI().toString();
    }

    private String resolveContentOrPath(String content, String fallbackPath, String suffix, String extension) throws Exception {
        if (content != null && !content.isBlank()) {
            Path tempFile = Files.createTempFile(tenant.getId() + suffix, extension);
            Files.writeString(tempFile, content);
            tempFiles.add(tempFile);
            return tempFile.toUri().toString();
        }
        return resolvePath(fallbackPath);
    }

    private String createTempProperties(Tenant tenant) throws Exception {
        Path tempFile = Files.createTempFile("ontop-" + tenant.getId() + "-", ".properties");
        try (OutputStream os = new FileOutputStream(tempFile.toFile())) {
            String content = String.format(
                    "jdbc.url=%s%njdbc.user=%s%njdbc.password=%s%njdbc.driver=%s%n",
                    tenant.getJdbcUrl(),
                    tenant.getJdbcUsername() != null ? tenant.getJdbcUsername() : "",
                    tenant.getJdbcPassword() != null ? tenant.getJdbcPassword() : "",
                    tenant.getJdbcDriver()
            );
            os.write(content.getBytes());
        }
        return tempFile.toAbsolutePath().toString();
    }

    private static void closeConnection(RepositoryConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
                log.warn("Error closing connection", ignored);
            }
        }
    }
}
