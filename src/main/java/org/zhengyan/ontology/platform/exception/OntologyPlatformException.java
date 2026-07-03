package org.zhengyan.ontology.platform.exception;

/**
 * @author 郑炎 Zheng Yan
 */
public class OntologyPlatformException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;

    public OntologyPlatformException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public OntologyPlatformException(String message, int httpStatus, String errorCode, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }

    public static OntologyPlatformException tenantNotFound(String tenantId) {
        return new OntologyPlatformException("Tenant not found: " + tenantId, 404, "TENANT_NOT_FOUND");
    }

    public static OntologyPlatformException queryError(String message, Throwable cause) {
        return new OntologyPlatformException("Query error: " + message, 400, "QUERY_ERROR", cause);
    }

    public static OntologyPlatformException engineNotReady(String tenantId) {
        return new OntologyPlatformException("Engine not ready: " + tenantId, 503, "ENGINE_NOT_READY");
    }
}
