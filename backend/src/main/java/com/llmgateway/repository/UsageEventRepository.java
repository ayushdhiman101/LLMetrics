package com.llmgateway.repository;

import com.llmgateway.domain.UsageEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface UsageEventRepository extends ReactiveCrudRepository<UsageEvent, UUID> {}
