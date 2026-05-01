package com.llmgateway.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PromptInterpolator {

    public String interpolate(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
