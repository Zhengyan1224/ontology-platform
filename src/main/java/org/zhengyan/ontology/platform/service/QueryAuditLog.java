package org.zhengyan.ontology.platform.service;

import java.time.LocalDateTime;

public class QueryAuditLog {

    private final long id;
    private final String tenantId;
    private final String queryType;
    private final String queryText;
    private final String generatedSparql;
    private final String translatedSql;
    private final long durationMs;
    private final boolean success;
    private final String errorMessage;
    private final int resultCount;
    private final LocalDateTime timestamp;

    public QueryAuditLog(long id, String tenantId, String queryType, String queryText,
                         String generatedSparql, String translatedSql,
                         long durationMs, boolean success, String errorMessage,
                         int resultCount) {
        this.id = id;
        this.tenantId = tenantId;
        this.queryType = queryType;
        this.queryText = queryText;
        this.generatedSparql = generatedSparql;
        this.translatedSql = translatedSql;
        this.durationMs = durationMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.resultCount = resultCount;
        this.timestamp = LocalDateTime.now();
    }

    public long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getQueryType() { return queryType; }
    public String getQueryText() { return queryText; }
    public String getGeneratedSparql() { return generatedSparql; }
    public String getTranslatedSql() { return translatedSql; }
    public long getDurationMs() { return durationMs; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public int getResultCount() { return resultCount; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
