package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.model.Tenant;
import org.zhengyan.ontology.platform.repository.TenantContentRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class TenantPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(TenantPersistenceService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TenantContentRepository tenantContentRepository;

    public TenantPersistenceService(JdbcTemplate jdbcTemplate, TenantContentRepository tenantContentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantContentRepository = tenantContentRepository;
    }

    public List<Tenant> findAll() {
        List<Tenant> tenants = jdbcTemplate.query("SELECT * FROM tenants", this::mapRow);
        tenants.forEach(this::loadContent);
        return tenants;
    }

    public Tenant findById(String id) {
        List<Tenant> results = jdbcTemplate.query(
                "SELECT * FROM tenants WHERE id = ?", this::mapRow, id);
        if (results.isEmpty()) return null;
        Tenant tenant = results.get(0);
        loadContent(tenant);
        return tenant;
    }

    private void loadContent(Tenant tenant) {
        TenantContentRepository.TenantContent content = tenantContentRepository.findByTenantId(tenant.getId());
        if (content != null) {
            tenant.setOwlContent(content.owlContent());
            tenant.setObdaContent(content.obdaContent());
        }
    }

    public void save(Tenant tenant) {
        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, jdbc_url, jdbc_driver, jdbc_username, jdbc_password, owl_path, obda_path) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tenant.getId(), tenant.getName(), tenant.getJdbcUrl(), tenant.getJdbcDriver(),
                tenant.getJdbcUsername(), tenant.getJdbcPassword(),
                tenant.getOwlPath(), tenant.getObdaPath());
        log.info("Persisted tenant [{}]", tenant.getId());
    }

    public void update(Tenant tenant) {
        jdbcTemplate.update(
                "UPDATE tenants SET name=?, jdbc_url=?, jdbc_driver=?, jdbc_username=?, jdbc_password=?, owl_path=?, obda_path=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",
                tenant.getName(), tenant.getJdbcUrl(), tenant.getJdbcDriver(),
                tenant.getJdbcUsername(), tenant.getJdbcPassword(),
                tenant.getOwlPath(), tenant.getObdaPath(),
                tenant.getId());
        log.info("Updated tenant [{}]", tenant.getId());
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", id);
        log.info("Deleted tenant [{}]", id);
    }

    private Tenant mapRow(ResultSet rs, int rowNum) throws SQLException {
        Tenant tenant = new Tenant();
        tenant.setId(rs.getString("id"));
        tenant.setName(rs.getString("name"));
        tenant.setJdbcUrl(rs.getString("jdbc_url"));
        tenant.setJdbcDriver(rs.getString("jdbc_driver"));
        tenant.setJdbcUsername(rs.getString("jdbc_username"));
        tenant.setJdbcPassword(rs.getString("jdbc_password"));
        tenant.setOwlPath(rs.getString("owl_path"));
        tenant.setObdaPath(rs.getString("obda_path"));
        return tenant;
    }
}
