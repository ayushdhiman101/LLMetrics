package com.llmgateway.dto;

import java.math.BigDecimal;
import java.util.List;

public record UsageSummaryResponse(
        String from,
        String to,
        long totalRequests,
        BigDecimal totalCostUsd,
        List<ProviderBreakdown> byProvider,
        List<ModelBreakdown> byModel,
        List<DailyBreakdown> byDay
) {
    public record ProviderBreakdown(
            String provider,
            long requests,
            long inputTokens,
            long outputTokens,
            BigDecimal costUsd
    ) {}

    public record ModelBreakdown(
            String model,
            String provider,
            long requests,
            long inputTokens,
            long outputTokens,
            BigDecimal costUsd
    ) {}

    public record DailyBreakdown(
            String day,
            long requests,
            long inputTokens,
            long outputTokens,
            BigDecimal costUsd
    ) {}
}
