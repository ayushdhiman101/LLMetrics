package com.llmgateway.metrics;

import com.llmgateway.dto.UsageSummaryResponse;
import com.llmgateway.dto.UsageSummaryResponse.DailyBreakdown;
import com.llmgateway.dto.UsageSummaryResponse.ModelBreakdown;
import com.llmgateway.dto.UsageSummaryResponse.ProviderBreakdown;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UsageService {

    private final DatabaseClient db;

    public UsageService(DatabaseClient db) {
        this.db = db;
    }

    public Mono<UsageSummaryResponse> getSummary(UUID tenantId, LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        Mono<List<ProviderBreakdown>> byProvider = queryByProvider(tenantId, fromDt, toDt).collectList();
        Mono<List<ModelBreakdown>> byModel = queryByModel(tenantId, fromDt, toDt).collectList();
        Mono<List<DailyBreakdown>> byDay = queryByDay(tenantId, fromDt, toDt).collectList();

        return Mono.zip(byProvider, byModel, byDay)
                .map(t -> {
                    List<ProviderBreakdown> providers = t.getT1();
                    long totalRequests = providers.stream().mapToLong(ProviderBreakdown::requests).sum();
                    BigDecimal totalCost = providers.stream()
                            .map(ProviderBreakdown::costUsd)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new UsageSummaryResponse(
                            from.toString(), to.toString(),
                            totalRequests, totalCost,
                            providers, t.getT2(), t.getT3()
                    );
                });
    }

    private Flux<ProviderBreakdown> queryByProvider(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        return db.sql("""
                        SELECT provider,
                               COUNT(*)                      AS requests,
                               COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(cost_usd), 0)      AS cost_usd
                        FROM usage_events
                        WHERE tenant_id = :tenantId
                          AND created_at >= :from
                          AND created_at < :to
                        GROUP BY provider
                        ORDER BY cost_usd DESC
                        """)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map(row -> new ProviderBreakdown(
                        row.get("provider", String.class),
                        longVal(row.get("requests")),
                        longVal(row.get("input_tokens")),
                        longVal(row.get("output_tokens")),
                        decimalVal(row.get("cost_usd"))
                ))
                .all();
    }

    private Flux<ModelBreakdown> queryByModel(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        return db.sql("""
                        SELECT model,
                               provider,
                               COUNT(*)                      AS requests,
                               COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(cost_usd), 0)      AS cost_usd
                        FROM usage_events
                        WHERE tenant_id = :tenantId
                          AND created_at >= :from
                          AND created_at < :to
                        GROUP BY model, provider
                        ORDER BY cost_usd DESC
                        """)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map(row -> new ModelBreakdown(
                        row.get("model", String.class),
                        row.get("provider", String.class),
                        longVal(row.get("requests")),
                        longVal(row.get("input_tokens")),
                        longVal(row.get("output_tokens")),
                        decimalVal(row.get("cost_usd"))
                ))
                .all();
    }

    private Flux<DailyBreakdown> queryByDay(UUID tenantId, LocalDateTime from, LocalDateTime to) {
        return db.sql("""
                        SELECT DATE(created_at)              AS day,
                               COUNT(*)                      AS requests,
                               COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(cost_usd), 0)      AS cost_usd
                        FROM usage_events
                        WHERE tenant_id = :tenantId
                          AND created_at >= :from
                          AND created_at < :to
                        GROUP BY day
                        ORDER BY day ASC
                        """)
                .bind("tenantId", tenantId)
                .bind("from", from)
                .bind("to", to)
                .map(row -> new DailyBreakdown(
                        row.get("day").toString(),
                        longVal(row.get("requests")),
                        longVal(row.get("input_tokens")),
                        longVal(row.get("output_tokens")),
                        decimalVal(row.get("cost_usd"))
                ))
                .all();
    }

    private static long longVal(Object v) {
        if (v instanceof Number n) return n.longValue();
        return 0L;
    }

    private static BigDecimal decimalVal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
