package com.llmgateway.dto.session;

import com.llmgateway.domain.Session;

import java.util.UUID;

public record SessionResponse(
        UUID id,
        String name,
        String startedAt,
        String endedAt,
        boolean active
) {
    public static SessionResponse from(Session s) {
        return new SessionResponse(
                s.id(),
                s.name(),
                s.startedAt().toString(),
                s.endedAt() != null ? s.endedAt().toString() : null,
                s.endedAt() == null
        );
    }
}
