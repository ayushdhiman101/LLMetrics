package com.llmgateway.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptInterpolatorTest {

    private final PromptInterpolator interpolator = new PromptInterpolator();

    @Test
    void replacesAllVariables() {
        String result = interpolator.interpolate(
                "Hello {{name}}, you are a {{role}}.",
                Map.of("name", "Alice", "role", "engineer")
        );
        assertThat(result).isEqualTo("Hello Alice, you are a engineer.");
    }

    @Test
    void nullVariables_returnsTemplateUnchanged() {
        String template = "Hello {{name}}";
        assertThat(interpolator.interpolate(template, null)).isEqualTo(template);
    }

    @Test
    void emptyVariables_returnsTemplateUnchanged() {
        String template = "Hello {{name}}";
        assertThat(interpolator.interpolate(template, Map.of())).isEqualTo(template);
    }

    @Test
    void unknownVariable_leavesPlaceholderIntact() {
        String result = interpolator.interpolate("Hello {{name}}", Map.of("role", "engineer"));
        assertThat(result).isEqualTo("Hello {{name}}");
    }

    @Test
    void sameVariableAppearsMultipleTimes_allOccurrencesReplaced() {
        String result = interpolator.interpolate("{{x}} + {{x}} = two {{x}}s", Map.of("x", "apple"));
        assertThat(result).isEqualTo("apple + apple = two apples");
    }
}
