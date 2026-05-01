package com.llmgateway.prompt;

import com.llmgateway.repository.PromptRepository;
import com.llmgateway.repository.PromptVersionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Service
public class PromptService {

    private final PromptRepository promptRepository;
    private final PromptVersionRepository promptVersionRepository;
    private final PromptInterpolator interpolator;

    public PromptService(PromptRepository promptRepository,
                         PromptVersionRepository promptVersionRepository,
                         PromptInterpolator interpolator) {
        this.promptRepository = promptRepository;
        this.promptVersionRepository = promptVersionRepository;
        this.interpolator = interpolator;
    }

    public record ResolvedPrompt(UUID promptId, String version, String content) {}

    public Mono<ResolvedPrompt> resolve(UUID tenantId, String promptName, String version, Map<String, String> vars) {
        return promptRepository.findByTenantIdAndName(tenantId, promptName)
                .switchIfEmpty(Mono.error(new PromptNotFoundException(promptName, version)))
                .flatMap(prompt -> {
                    Mono<com.llmgateway.domain.PromptVersion> versionMono =
                            (version != null && !version.isBlank())
                                    ? promptVersionRepository.findByPromptIdAndVersion(prompt.id(), version)
                                    : promptVersionRepository.findByPromptIdAndIsActiveTrue(prompt.id()).next();
                    return versionMono
                            .switchIfEmpty(Mono.error(new PromptNotFoundException(promptName, version)))
                            .map(pv -> new ResolvedPrompt(
                                    prompt.id(),
                                    pv.version(),
                                    interpolator.interpolate(pv.template(), vars)));
                });
    }
}
