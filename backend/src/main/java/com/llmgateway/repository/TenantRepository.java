package com.llmgateway.repository;

import com.llmgateway.domain.Tenant;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface TenantRepository extends ReactiveCrudRepository<Tenant, UUID> {}
