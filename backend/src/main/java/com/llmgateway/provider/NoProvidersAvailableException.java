package com.llmgateway.provider;

public class NoProvidersAvailableException extends RuntimeException {
    public NoProvidersAvailableException(String message) {
        super(message);
    }

    public NoProvidersAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
