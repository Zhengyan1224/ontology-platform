package org.zhengyan.ontology.platform.engine;

import org.zhengyan.ontology.platform.model.SparqlQueryResult;
import org.zhengyan.ontology.platform.model.Tenant;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepositoryConnection;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void initialize(Tenant tenant) throws Exception {
        if (initialized.get()) {
            throw new IllegalStateException("Engine already initialized for tenant: " + tenant.getId());
        }

        String owlPath = resolvePath(tenant.resolveOwlPath());
        String obdaPath = resolvePath(tenant.resolveObdaPath());
        String propPath = createTempProperties(tenant);

        log.info("Initializing Ontop engine for tenant [{}]: owl={}, obda={}",
                tenant.getId(), owlPath, obdaPath);

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
    }

    @Override
    public SparqlQueryResult executeQuery(String sparql) throws Exception {
        assertInitialized();

        long start = System.currentTimeMillis();

        List<String> variables = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();

        RepositoryConnection conn = repo.getConnection();
        try {
            TupleQueryResult result = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparql).evaluate();
            variables.addAll(result.getBindingNames());

            while (result.hasNext()) {
                BindingSet bs = result.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (String var : variables) {
                    var binding = bs.getBinding(var);
                    row.put(var, binding != null ? binding.getValue().stringValue() : null);
                }
                rows.add(row);
            }
            ((AutoCloseable) result).close();
        } finally {
            closeConnection(conn);
        }

        long elapsed = System.currentTimeMillis() - start;

        SparqlQueryResult queryResult = new SparqlQueryResult(variables, rows, elapsed);
        queryResult.setTranslatedSql(translateToSql(sparql));
        return queryResult;
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
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public void destroy() {
        if (repo != null) {
            repo.shutDown();
            repo = null;
        }
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
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
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
