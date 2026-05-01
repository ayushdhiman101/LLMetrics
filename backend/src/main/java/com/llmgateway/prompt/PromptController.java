package com.llmgateway.prompt;

import com.llmgateway.domain.Tenant;
import com.llmgateway.dto.prompt.CreatePromptRequest;
import com.llmgateway.dto.prompt.CreateVersionRequest;
import com.llmgateway.dto.prompt.PromptResponse;
import com.llmgateway.dto.prompt.PromptVersionResponse;
import com.llmgateway.dto.prompt.SetActiveVersionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/v1/prompts")
public class PromptController {

    private final PromptManagementService management;

    public PromptController(PromptManagementService management) {
        this.management = management;
    }

    @GetMapping
    public Flux<PromptResponse> listPrompts() {
        return tenant().flatMapMany(t -> management.listPrompts(t.id()))
                .map(PromptResponse::from);
    }

    @PostMapping
    public Mono<PromptResponse> createPrompt(@RequestBody CreatePromptRequest body) {
        return tenant().flatMap(t -> management.createPrompt(t.id(), body.name()))
                .map(PromptResponse::from);
    }

    @PostMapping("/{name}/versions")
    public Mono<PromptVersionResponse> createVersion(@PathVariable String name,
                                                     @RequestBody CreateVersionRequest body) {
        return tenant().flatMap(t -> management.createVersion(t.id(), name, body))
                .map(PromptVersionResponse::from);
    }

    @GetMapping("/{name}/versions")
    public Flux<PromptVersionResponse> listVersions(@PathVariable String name) {
        return tenant().flatMapMany(t -> management.listVersions(t.id(), name))
                .map(PromptVersionResponse::from);
    }

    @GetMapping("/{name}/versions/{version}")
    public Mono<PromptVersionResponse> getVersion(@PathVariable String name,
                                                  @PathVariable String version) {
        return tenant().flatMap(t -> management.getVersion(t.id(), name, version))
                .map(PromptVersionResponse::from);
    }

    @PutMapping("/{name}/active-version")
    public Mono<PromptVersionResponse> setActiveVersion(@PathVariable String name,
                                                        @RequestBody SetActiveVersionRequest body) {
        return tenant().flatMap(t -> management.setActiveVersion(t.id(), name, body.version()))
                .map(PromptVersionResponse::from);
    }

    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<String> handleNotFound(PromptNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    private static Mono<Tenant> tenant() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.get("tenant")));
    }
}
