package org.zhengyan.ontology.platform.exception;

public class OwlGenerationException extends OntologyPlatformException {
    public OwlGenerationException(String message, Throwable cause) {
        super(message, 500, "OWL_GENERATION_FAILED", cause);
    }

    public OwlGenerationException(String message) {
        super(message, 500, "OWL_GENERATION_FAILED");
    }
}
