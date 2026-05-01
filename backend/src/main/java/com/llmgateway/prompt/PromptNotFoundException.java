package com.llmgateway.prompt;

public class PromptNotFoundException extends RuntimeException {
    public PromptNotFoundException(String name, String version) {
        super("Prompt not found: " + name + (version != null ? " @ " + version : " (active)"));
    }
}
