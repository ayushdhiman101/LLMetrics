package com.llmgateway.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "pricing")
public class CostCalculator {

    private Map<String, ModelPricing> models = new HashMap<>();

    public BigDecimal calculate(String model, int inputTokens, int outputTokens) {
        ModelPricing pricing = models.get(model);
        if (pricing == null) return BigDecimal.ZERO;

        BigDecimal inputCost = pricing.getInputPer1k()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

        BigDecimal outputCost = pricing.getOutputPer1k()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }

    public Map<String, ModelPricing> getModels() { return models; }
    public void setModels(Map<String, ModelPricing> models) { this.models = models; }

    public static class ModelPricing {
        private BigDecimal inputPer1k = BigDecimal.ZERO;
        private BigDecimal outputPer1k = BigDecimal.ZERO;

        public BigDecimal getInputPer1k() { return inputPer1k; }
        public void setInputPer1k(BigDecimal inputPer1k) { this.inputPer1k = inputPer1k; }

        public BigDecimal getOutputPer1k() { return outputPer1k; }
        public void setOutputPer1k(BigDecimal outputPer1k) { this.outputPer1k = outputPer1k; }
    }
}
