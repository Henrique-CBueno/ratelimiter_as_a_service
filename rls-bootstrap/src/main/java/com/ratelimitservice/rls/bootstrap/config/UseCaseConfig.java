package com.ratelimitservice.rls.bootstrap.config;

import com.ratelimitservice.rls.application.ratelimit.CheckRateLimitUseCase;
import com.ratelimitservice.rls.application.ratelimit.port.RateLimitEvaluationPort;
import com.ratelimitservice.rls.application.resource.CreateResourceUseCase;
import com.ratelimitservice.rls.application.resource.DeleteResourceUseCase;
import com.ratelimitservice.rls.application.resource.GetResourceUseCase;
import com.ratelimitservice.rls.application.resource.ListResourcesUseCase;
import com.ratelimitservice.rls.application.resource.UpdateResourceUseCase;
import com.ratelimitservice.rls.application.resource.port.ResourceRepositoryPort;
import com.ratelimitservice.rls.application.security.port.SecretHasherPort;
import com.ratelimitservice.rls.application.tenant.AuthenticateTenantUseCase;
import com.ratelimitservice.rls.application.tenant.GetTenantUseCase;
import com.ratelimitservice.rls.application.tenant.LoginTenantUseCase;
import com.ratelimitservice.rls.application.tenant.RegisterTenantUseCase;
import com.ratelimitservice.rls.application.tenant.RotateApiTokenUseCase;
import com.ratelimitservice.rls.application.tenant.port.TenantRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly constructs every use case (design decision: {@code rls-application} stays framework
 * annotation-free, so use cases are never {@code @Component}-scanned — they're plain classes
 * wired here with their required ports).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public RegisterTenantUseCase registerTenantUseCase(TenantRepositoryPort tenantRepositoryPort,
                                                         SecretHasherPort secretHasherPort) {
        return new RegisterTenantUseCase(tenantRepositoryPort, secretHasherPort);
    }

    @Bean
    public AuthenticateTenantUseCase authenticateTenantUseCase(TenantRepositoryPort tenantRepositoryPort) {
        return new AuthenticateTenantUseCase(tenantRepositoryPort);
    }

    @Bean
    public RotateApiTokenUseCase rotateApiTokenUseCase(TenantRepositoryPort tenantRepositoryPort) {
        return new RotateApiTokenUseCase(tenantRepositoryPort);
    }

    @Bean
    public LoginTenantUseCase loginTenantUseCase(TenantRepositoryPort tenantRepositoryPort,
                                                   SecretHasherPort secretHasherPort) {
        return new LoginTenantUseCase(tenantRepositoryPort, secretHasherPort);
    }

    @Bean
    public GetTenantUseCase getTenantUseCase(TenantRepositoryPort tenantRepositoryPort) {
        return new GetTenantUseCase(tenantRepositoryPort);
    }

    @Bean
    public CreateResourceUseCase createResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        return new CreateResourceUseCase(resourceRepositoryPort);
    }

    @Bean
    public ListResourcesUseCase listResourcesUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        return new ListResourcesUseCase(resourceRepositoryPort);
    }

    @Bean
    public GetResourceUseCase getResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        return new GetResourceUseCase(resourceRepositoryPort);
    }

    @Bean
    public UpdateResourceUseCase updateResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        return new UpdateResourceUseCase(resourceRepositoryPort);
    }

    @Bean
    public DeleteResourceUseCase deleteResourceUseCase(ResourceRepositoryPort resourceRepositoryPort) {
        return new DeleteResourceUseCase(resourceRepositoryPort);
    }

    @Bean
    public CheckRateLimitUseCase checkRateLimitUseCase(ResourceRepositoryPort resourceRepositoryPort,
                                                         RateLimitEvaluationPort rateLimitEvaluationPort) {
        return new CheckRateLimitUseCase(resourceRepositoryPort, rateLimitEvaluationPort);
    }
}
