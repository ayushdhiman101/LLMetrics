package com.llmgateway.prompt;

import com.llmgateway.domain.Prompt;
import com.llmgateway.domain.PromptVersion;
import com.llmgateway.dto.prompt.CreateVersionRequest;
import com.llmgateway.repository.PromptRepository;
import com.llmgateway.repository.PromptVersionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class PromptManagementService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;

    public PromptManagementService(PromptRepository promptRepository,
                                   PromptVersionRepository promptVersionRepository) {
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
    }

    public Flux<Prompt> listPrompts(UUID tenantId) {
        return promptRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public Mono<Prompt> createPrompt(UUID tenantId, String name) {
        return promptRepository.save(new Prompt(null, tenantId, name, null));
    }

    public Mono<PromptVersion> createVersion(UUID tenantId, String promptName, CreateVersionRequest req) {
        boolean shouldActivate = Boolean.TRUE.equals(req.isActive());
        return findPrompt(tenantId, promptName)
                .flatMap(prompt -> {
                    Mono<Void> deactivate = shouldActivate
                            ? deactivateActiveVersions(prompt.id())
                            : Mono.empty();
                    PromptVersion fresh = new PromptVersion(
                            null,
                            prompt.id(),
                            req.version(),
                            req.template(),
                            req.description(),
                            req.changelog(),
                            shouldActivate,
                            null
                    );
                    return deactivate.then(promptVersionRepository.save(fresh));
                });
    }

    public Flux<PromptVersion> listVersions(UUID tenantId, String promptName) {
        return findPrompt(tenantId, promptName)
                .flatMapMany(prompt -> promptVersionRepository.findByPromptIdOrderByCreatedAtDesc(prompt.id()));
    }

    public Mono<PromptVersion> getVersion(UUID tenantId, String promptName, String version) {
        return findPrompt(tenantId, promptName)
                .flatMap(prompt -> promptVersionRepository.findByPromptIdAndVersion(prompt.id(), version))
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptName, version)));
    }

    public Mono<PromptVersion> setActiveVersion(UUID tenantId, String promptName, String targetVersion) {
        return findPrompt(tenantId, promptName)
                .flatMap(prompt -> promptVersionRepository.findByPromptIdAndVersion(prompt.id(), targetVersion)
                        .switchIfEmpty(Mono.error(new PromptNotFoundException(promptName, targetVersion)))
                        .flatMap(target -> deactivateActiveVersions(prompt.id())
                                .then(promptVersionRepository.save(withActive(target, true)))));
    }

    private Mono<Prompt> findPrompt(UUID tenantId, String promptName) {
        return promptRepository.findByTenantIdAndName(tenantId, promptName)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptName, null)));
    }

    private Mono<Void> deactivateActiveVersions(UUID promptId) {
        return promptVersionRepository.findByPromptIdAndIsActiveTrue(promptId)
                .flatMap(v -> promptVersionRepository.save(withActive(v, false)))
                .then();
    }

    private static PromptVersion withActive(PromptVersion v, boolean active) {
        return new PromptVersion(
                v.id(), v.promptId(), v.version(), v.template(),
                v.description(), v.changelog(), active, v.createdAt());
    }
}
