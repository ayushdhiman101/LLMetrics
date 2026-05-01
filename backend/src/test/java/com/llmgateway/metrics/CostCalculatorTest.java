package com.llmgateway.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CostCalculatorTest {

    private CostCalculator calculator;

    @BeforeEach
    void setUp() {
        CostCalculator.ModelPricing gpt4oMini = new CostCalculator.ModelPricing();
        gpt4oMini.setInputPer1k(new BigDecimal("0.00015"));
        gpt4oMini.setOutputPer1k(new BigDecimal("0.0006"));

        CostCalculator.ModelPricing geminiFlash = new CostCalculator.ModelPricing();
        geminiFlash.setInputPer1k(new BigDecimal("0.000075"));
        geminiFlash.setOutputPer1k(new BigDecimal("0.0003"));

        calculator = new CostCalculator();
        calculator.setModels(Map.of(
                "gpt-4o-mini", gpt4oMini,
                "gemini-flash-latest", geminiFlash
        ));
    }

    @Test
    void knownModel_returnsCorrectCost() {
        // 1000 input @ $0.00015/1k  = $0.000150
        // 500  output @ $0.0006/1k  = $0.000300
        // total                      = $0.000450
        BigDecimal cost = calculator.calculate("gpt-4o-mini", 1000, 500);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.000450"));
    }

    @Test
    void unknownModel_returnsZero() {
        BigDecimal cost = calculator.calculate("nonexistent-model", 1000, 1000);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroTokens_returnsZero() {
        BigDecimal cost = calculator.calculate("gpt-4o-mini", 0, 0);
        assertThat(cost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cheaperModel_lowerCostThanExpensiveModel() {
        BigDecimal geminiCost = calculator.calculate("gemini-flash-latest", 1000, 1000);
        BigDecimal gptCost = calculator.calculate("gpt-4o-mini", 1000, 1000);
        assertThat(geminiCost).isLessThan(gptCost);
    }

    @Test
    void costHasSixDecimalPlaces() {
        // 1 input token on gemini-flash-latest: 0.000075 / 1000 = 0.000000075 → rounds to 0.000000
        // 1 output token: 0.0003 / 1000 = 0.0000003 → rounds to 0.000000
        // 100 output tokens: 0.0003 * 100 / 1000 = 0.000030 exactly
        BigDecimal cost = calculator.calculate("gemini-flash-latest", 0, 100);
        assertThat(cost.scale()).isEqualTo(6);
        assertThat(cost).isEqualByComparingTo(new BigDecimal("0.000030"));
    }
}
