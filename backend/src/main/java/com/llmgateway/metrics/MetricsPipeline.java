package com.llmgateway.metrics;

import com.llmgateway.domain.UsageEvent;
import com.llmgateway.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MetricsPipeline {

    private static final Logger log = LoggerFactory.getLogger(MetricsPipeline.class);

    private final UsageEventRepository usageEventRepository;
    private final CostCalculator costCalculator;

    public MetricsPipeline(UsageEventRepository usageEventRepository, CostCalculator costCalculator) {
        this.usageEventRepository = usageEventRepository;
        this.costCalculator = costCalculator;
    }

    @Async
    public void record(UUID tenantId, UUID promptId, String promptVersion,
                       String provider, String model,
                       int inputTokens, int outputTokens,
                       long latencyMs) {
        try {
            BigDecimal cost = costCalculator.calculate(model, inputTokens, outputTokens);
            UsageEvent event = new UsageEvent(
                    null, tenantId, promptId, promptVersion,
                    provider, model, inputTokens, outputTokens,
                    cost, (int) latencyMs,
                    LocalDateTime.now()
            );
            usageEventRepository.save(event).subscribe(
                    saved -> log.debug("Usage event saved: {}", saved.id()),
                    err -> log.error("Failed to save usage event", err)
            );
        } catch (Exception e) {
            log.error("Error recording usage metrics", e);
        }
    }
}
