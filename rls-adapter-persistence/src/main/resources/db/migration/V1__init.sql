CREATE TABLE tenants (
    id                       UUID PRIMARY KEY,
    name                     VARCHAR(255)  NOT NULL,
    email                    VARCHAR(255)  NOT NULL,
    password_hash            VARCHAR(255)  NOT NULL,
    default_fallback_policy  VARCHAR(32)   NOT NULL,
    status                   VARCHAR(32)   NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenants_email UNIQUE (email)
);

CREATE TABLE api_tokens (
    id             UUID PRIMARY KEY,
    tenant_id      UUID          NOT NULL REFERENCES tenants (id),
    token_hash     VARCHAR(255)  NOT NULL,
    token_prefix   VARCHAR(32)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ,
    last_used_at   TIMESTAMPTZ,
    CONSTRAINT uq_api_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_api_tokens_tenant_id ON api_tokens (tenant_id);

CREATE TABLE rate_limit_resources (
    id               UUID PRIMARY KEY,
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    resource_key     VARCHAR(255)  NOT NULL,
    strategy_type    VARCHAR(32)   NOT NULL,
    limit_count      INTEGER       NOT NULL,
    window_seconds   INTEGER       NOT NULL,
    burst_capacity   INTEGER,
    fallback_policy  VARCHAR(32),
    enabled          BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rate_limit_resources_tenant_key UNIQUE (tenant_id, resource_key)
);

CREATE INDEX idx_rate_limit_resources_tenant_id ON rate_limit_resources (tenant_id);
