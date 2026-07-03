package org.zhengyan.ontology.platform;
public class OwlDebug {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:mem:db;" + System.currentTimeMillis() + ";DB_CLOSE_DELAY=-1";
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, "sa", "");
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE addresses (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE series (id INT PRIMARY KEY)");
            stmt.execute("CREATE TABLE species (id INT PRIMARY KEY)");
        }
        org.zhengyan.ontology.platform.model.Tenant tenant = new org.zhengyan.ontology.platform.model.Tenant();
        tenant.setId("test");
        tenant.setJdbcUrl(url);
        tenant.setJdbcUsername("sa");
        tenant.setJdbcPassword("");
        tenant.setJdbcDriver("org.h2.Driver");
        org.zhengyan.ontology.platform.service.OwlGeneratorService svc = 
            new org.zhengyan.ontology.platform.service.OwlGeneratorService(
                new org.zhengyan.ontology.platform.config.OwlGenerationProperties());
        String owl = svc.generateOwl(tenant);
        System.out.println(owl);
    }
}
