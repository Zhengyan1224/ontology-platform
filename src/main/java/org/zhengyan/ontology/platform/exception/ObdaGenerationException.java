package org.zhengyan.ontology.platform.exception;

public class ObdaGenerationException extends OntologyPlatformException {
    public ObdaGenerationException(String message, Throwable cause) {
        super(message, 500, "OBDA_GENERATION_FAILED", cause);
    }

    public ObdaGenerationException(String message) {
        super(message, 500, "OBDA_GENERATION_FAILED");
    }
}
