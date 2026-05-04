package com.llmgateway.metrics;

import com.llmgateway.domain.Session;
import com.llmgateway.dto.session.SessionResponse;
import com.llmgateway.repository.SessionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Mono<SessionResponse> startSession(UUID tenantId, String name) {
        String sessionName = (name != null && !name.isBlank()) ? name.trim() : "Session";
        return sessionRepository.findByTenantIdAndEndedAtIsNull(tenantId)
                .flatMap(active -> sessionRepository.save(new Session(
                        active.id(), active.tenantId(), active.name(),
                        active.startedAt(), LocalDateTime.now()
                )))
                .then(Mono.defer(() -> sessionRepository.save(new Session(
                        null, tenantId, sessionName, LocalDateTime.now(), null
                ))))
                .map(SessionResponse::from);
    }

    public Mono<SessionResponse> stopSession(UUID tenantId, UUID sessionId) {
        return sessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .flatMap(s -> sessionRepository.save(new Session(
                        s.id(), s.tenantId(), s.name(), s.startedAt(), LocalDateTime.now()
                )))
                .map(SessionResponse::from);
    }

    public Mono<SessionResponse> renameSession(UUID tenantId, UUID sessionId, String name) {
        return sessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .flatMap(s -> sessionRepository.save(new Session(
                        s.id(), s.tenantId(), name.trim(), s.startedAt(), s.endedAt()
                )))
                .map(SessionResponse::from);
    }

    public Flux<SessionResponse> listSessions(UUID tenantId) {
        return sessionRepository.findByTenantIdOrderByStartedAtDesc(tenantId)
                .map(SessionResponse::from);
    }

    public Mono<SessionResponse> getActiveSession(UUID tenantId) {
        return sessionRepository.findByTenantIdAndEndedAtIsNull(tenantId)
                .map(SessionResponse::from);
    }
}
