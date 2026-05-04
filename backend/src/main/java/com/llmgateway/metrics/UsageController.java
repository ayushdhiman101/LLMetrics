package com.llmgateway.metrics;

import com.llmgateway.domain.Tenant;
import com.llmgateway.dto.UsageSummaryResponse;
import com.llmgateway.repository.SessionRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/v1/usage")
public class UsageController {

    private final UsageService usageService;
    private final SessionRepository sessionRepository;

    public UsageController(UsageService usageService, SessionRepository sessionRepository) {
        this.usageService = usageService;
        this.sessionRepository = sessionRepository;
    }

    @GetMapping("/summary")
    public Mono<UsageSummaryResponse> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID sessionId
    ) {
        return Mono.deferContextual(ctx -> {
            Tenant tenant = ctx.get("tenant");

            if (sessionId != null) {
                return sessionRepository.findByIdAndTenantId(sessionId, tenant.id())
                        .flatMap(session -> {
                            LocalDate sessionFrom = session.startedAt().toLocalDate();
                            LocalDateTime endDt = session.endedAt() != null ? session.endedAt() : LocalDateTime.now();
                            LocalDate sessionTo = endDt.toLocalDate();
                            return usageService.getSummary(tenant.id(), sessionFrom, sessionTo, sessionId);
                        });
            }

            LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(30);
            LocalDate resolvedTo = to != null ? to : LocalDate.now();
            return usageService.getSummary(tenant.id(), resolvedFrom, resolvedTo);
        });
    }
}
