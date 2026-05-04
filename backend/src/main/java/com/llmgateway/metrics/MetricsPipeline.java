package com.llmgateway.metrics;

import com.llmgateway.domain.UsageEvent;
import com.llmgateway.repository.SessionRepository;
import com.llmgateway.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MetricsPipeline {

    private static final Logger log = LoggerFactory.getLogger(MetricsPipeline.class);

    private final UsageEventRepository usageEventRepository;
    private final SessionRepository sessionRepository;
    private final CostCalculator costCalculator;

    public MetricsPipeline(UsageEventRepository usageEventRepository,
                           SessionRepository sessionRepository,
                           CostCalculator costCalculator) {
        this.usageEventRepository = usageEventRepository;
        this.sessionRepository = sessionRepository;
        this.costCalculator = costCalculator;
    }

    @Async
    public void record(UUID tenantId, UUID promptId, String promptVersion,
                       String provider, String model,
                       int inputTokens, int outputTokens,
                       long latencyMs) {
        try {
            BigDecimal cost = costCalculator.calculate(model, inputTokens, outputTokens);
            sessionRepository.findByTenantIdAndEndedAtIsNull(tenantId)
                    .map(s -> s.id())
                    .flatMap(sessionId -> save(tenantId, promptId, promptVersion, provider, model,
                            inputTokens, outputTokens, cost, latencyMs, sessionId))
                    .switchIfEmpty(Mono.defer(() -> save(tenantId, promptId, promptVersion, provider, model,
                            inputTokens, outputTokens, cost, latencyMs, null)))
                    .subscribe(
                            saved -> log.debug("Usage event saved: {}", saved.id()),
                            err -> log.error("Failed to save usage event", err)
                    );
        } catch (Exception e) {
            log.error("Error recording usage metrics", e);
        }
    }

    private Mono<UsageEvent> save(UUID tenantId, UUID promptId, String promptVersion,
                                  String provider, String model,
                                  int inputTokens, int outputTokens,
                                  BigDecimal cost, long latencyMs, UUID sessionId) {
        UsageEvent event = new UsageEvent(
                null, tenantId, promptId, promptVersion,
                provider, model, inputTokens, outputTokens,
                cost, (int) latencyMs, sessionId,
                LocalDateTime.now()
        );
        return usageEventRepository.save(event);
    }
}
