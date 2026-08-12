# Rate Limiter as a Service — Plano de Arquitetura

## Contexto

O usuário quer construir, do zero, um **Rate Limiter as a Service**: uma aplicação Java reativa que
recebe chamadas de verificação (tenant/token + recurso + IP) e responde se a requisição está
autorizada, aplicando a estratégia de rate limit que cada tenant escolheu para cada recurso (Fixed
Window, Sliding Window Log, Sliding Window Counter, Token Bucket, Leaky Bucket, com limites
customizados). A aplicação precisa ser **stateless**, pensada para **escalabilidade horizontal**,
livre de **race conditions** entre instâncias concorrentes, com **circuit breaker** protegendo a
dependência de estado distribuído, e um **front Thymeleaf** para onboarding/gestão dos tenants.

O projeto será construído inteiramente através de **specs do OpenSpec**, em múltiplos `changes`
sequenciais, seguindo **arquitetura hexagonal**, **DDD** e **TDD**, com commits seguindo **git flow**.
Esta é a fase de definição arquitetural — antes de abrir a primeira spec — para alinhar todas as
decisões estruturais que vão moldar o domínio e a divisão do trabalho.

O diretório está vazio (só `.claude/`, `.idea/`, `openspec/` scaffold) e ainda não é um repositório
git.

## Decisões já validadas com o usuário

| Tema | Decisão |
|---|---|
| Store distribuído (contadores) | **Redis com Lua scripts (EVAL)** — atomicidade nativa |
| Persistência de cadastro | **PostgreSQL via R2DBC** (reativo) |
| Granularidade da config | **Por recurso, dentro do tenant** — cada recurso tem sua própria estratégia + limite |
| Build/módulos | **Maven multi-módulo**, refletindo fisicamente a arquitetura hexagonal |
| Fallback do circuit breaker | **Configurável por tenant/recurso** (FAIL_OPEN ou FAIL_CLOSED) |
| Contrato da API de check | **Booleano + metadados** (limit, remaining, resetAt, retryAfter) — estilo `RateLimit-*` (IETF draft) |
| Escopo de infra nesta fase | **Docker/Docker Compose** + **Observabilidade** (Actuator/Micrometer/health). Kubernetes fica de fora por ora |
| Auth no endpoint de check | **Token/API Key no header** (ex.: `Authorization: Bearer`), identifica o tenant |
| Escopo do front Thymeleaf | **Onboarding + login + dashboard de gestão** (CRUD de recursos, não só cadastro inicial) |
| Setup do repositório | Claude inicializa **git com modelo git-flow** (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`) como parte da spec 1 |
| Ordem das specs | Por camada hexagonal (ver seção "Quebra em specs" abaixo) |
| Commits | Claude pode rodar `git commit`, mas **sem** a linha `Co-Authored-By: Claude` |

## Arquitetura de módulos (Maven multi-módulo)

Reactor multi-módulo com parent `pom.xml` (`packaging=pom`, `dependencyManagement` central: Spring
Boot BOM, Java 21, Reactor BOM, JUnit5, AssertJ, Testcontainers BOM).

```
ratelimit-as-service/
├── pom.xml                     (parent)
├── rls-domain/                 (puro Java, zero framework — entidades, VOs, as 5 estratégias)
├── rls-application/            (use cases + ports; única exceção: depende de reactor-core)
├── rls-adapter-redis/          (outbound: Lua scripts, contadores distribuídos)
├── rls-adapter-persistence/    (outbound: R2DBC/Postgres, Flyway)
├── rls-adapter-resilience/     (outbound decorator: Resilience4j circuit breaker)
├── rls-adapter-rest/           (inbound: WebFlux REST controllers)
├── rls-adapter-web/            (inbound: Thymeleaf SSR — onboarding/login/dashboard)
└── rls-bootstrap/              (Spring Boot app: main class, wiring, application.yml, Docker)
```

Grafo de dependências: `rls-domain` ← `rls-application` ← {todos os adapters} ← `rls-bootstrap`.
`rls-domain` não conhece Reactor, Spring, nem Redis/Postgres — as 5 estratégias são funções puras
síncronas. Adapters nunca se conhecem entre si; comunicação só via portas definidas em
`rls-application`.

## Modelo de domínio (DDD)

**Bounded contexts** (modular monolith, mesmo deployável): **Tenant Management**
(`domain.tenant`, `domain.resource`) e **Rate Limiting Enforcement** (`domain.ratelimit`).
Comunicam-se só por IDs (`TenantId`, `ResourceId`), nunca por referência de objeto.

**Aggregates:**
- `Tenant` (root): `TenantId`, `name`, `email`, `passwordHash`, `status`, `defaultFallbackPolicy`.
  Tokens de API em entidade filha `ApiToken` (`tokenHash`, `prefix`, `revokedAt`) para suportar
  rotação/histórico.
- `RateLimitResource` (root independente, não filho de `Tenant` — evita lock no agregado inteiro ao
  editar um recurso): `ResourceId`, `tenantId`, `resourceKey`, `strategyType`, `Quota` (VO),
  `fallbackPolicy` (nullable = herda do tenant), `enabled`.

**Value Objects principais:** `RateLimitKey` (tenantId+resourceId+clientIp+strategyCode),
`Quota` (limit, window, burstCapacity), `RateLimitDecision` (allowed, limit, remaining, resetAt,
retryAfter, degraded), `RateLimitState` (uma variante por estratégia), `ClientIp`, `FallbackPolicy`.

**As 5 estratégias como Strategy Pattern testável:** `RateLimitStrategy` é uma interface **pura,
síncrona, sem I/O** (`evaluate(state, quota, now) -> decision+novoEstado`), com 5 implementações
(`FixedWindowStrategy`, `SlidingWindowLogStrategy`, `SlidingWindowCounterStrategy`,
`TokenBucketStrategy`, `LeakyBucketStrategy`), testadas em TDD puro com JUnit5, sem
Spring/Mockito. Essas implementações são a **especificação executável** do algoritmo — usadas em
testes de unidade e em **testes de paridade** (spec 2) que comparam a saída dos scripts Lua reais
contra a versão pura de domínio para a mesma sequência determinística de chamadas. A versão Lua é a
autoridade em produção (é a única capaz de garantir atomicidade sob concorrência real); a versão
Java pura é a fonte da verdade comportamental e o veículo do TDD pedido pelo usuário.

Domain Events (`TenantRegistered`, `ResourceConfigChanged`) ficam como hook arquitetural mencionado,
não implementados no MVP (evitar scope creep de mensageria/outbox).

## As 5 estratégias no Redis (Lua/EVAL)

Convenção comum: `KEYS[1]` = `rl:{tenantId}:{resourceId}:{ip}:{strategyCode}` (strategyCode no nome
evita `WRONGTYPE` se o tenant trocar de estratégia — a chave antiga só expira via TTL). Todos os
scripts usam `redis.call('TIME')` **dentro do Lua** como fonte de tempo (não recebem `now` da app) —
crítico numa aplicação stateless multi-instância, evita clock skew entre instâncias. Retorno
padronizado: `{allowed, limit, remaining, reset_at_ms, retry_after_ms}`.

1. **Fixed Window** — `STRING` + `INCR`/`PEXPIRE`. O(1). Aceita o burst 2x na borda (limitação
   inerente ao algoritmo).
2. **Sliding Window Log** — `ZSET` (score=timestamp, member=`timestamp:uuid`).
   `ZREMRANGEBYSCORE` + `ZCARD` + `ZADD` condicional. Preciso, O(limit) em memória.
3. **Sliding Window Counter** — duas `STRING` (bucket atual/anterior),
   `estimated = curr + prev*(1-fração_decorrida)`. O(1), suaviza a borda do fixed window.
4. **Token Bucket** — `HASH{tokens, last_refill}`, refill proporcional ao tempo decorrido.
5. **Leaky Bucket** — via **GCRA** (Generic Cell Rate Algorithm, padrão de mercado — Kong,
   Cloudflare — para leaky bucket sem processo de "drip" em background, incompatível com
   stateless/múltiplas instâncias). `STRING` guarda só o `TAT` (theoretical arrival time).

## Fluxo ponta a ponta do `check`

1. `POST /api/v1/ratelimit/check` (`rls-adapter-rest`), `Authorization: Bearer <token>`,
   body `{resource, clientIp}`.
2. `WebFilter` de auth extrai token → `AuthenticateTenantUseCase` →
   `TenantRepositoryPort.findByTokenHash` (Postgres/R2DBC) → 401 se inválido.
3. `CheckRateLimitUseCase` resolve `RateLimitResource` via `ResourceRepositoryPort`
   (strategy, quota, fallbackPolicy).
4. Monta `RateLimitKey` usando `resourceId` (UUID, não o path em texto — renomear recurso não deve
   resetar contadores).
5. `RateLimitEvaluationPort.evaluate(...)`, decorado por `rls-adapter-resilience`
   (circuit breaker reativo, Resilience4j `CircuitBreakerOperator`).
6. Circuito aberto → `onErrorResume` consulta `resource.fallbackPolicy` (ou
   `tenant.defaultFallbackPolicy` se nulo) → decisão sintética `degraded=true`, sem propagar erro.
7. Controller mapeia para HTTP: `200`/`429`, headers `RateLimit-Limit/Remaining/Reset` +
   `Retry-After`.

**Nota registrada para o futuro (não é requisito agora):** o hot path toca Postgres a cada chamada
para resolver a config do recurso — candidato a otimização futura via cache write-through em Redis,
não obrigatório no MVP.

## Circuit Breaker (Resilience4j reativo)

O circuito é uma **instância única/global por dependência física** (ex.: `"redis-ratelimit"`),
porque seu papel é detectar saúde da infraestrutura compartilhada — um circuito por tenant diluiria
a detecção estatística de falha. **O que é configurável por tenant/recurso é só a política de
fallback** (`FAIL_OPEN` vs `FAIL_CLOSED`) aplicada quando o circuito global está aberto.
`rls-adapter-resilience` decora o adapter Redis real; estado exposto via Actuator health indicator +
métricas Micrometer nativas do Resilience4j (spec 6).

## Modelo de dados Postgres

```
tenants(id, name, email UNIQUE, password_hash, default_fallback_policy, status, created_at, updated_at)
api_tokens(id, tenant_id FK, token_hash UNIQUE, token_prefix, created_at, revoked_at, last_used_at)
rate_limit_resources(id, tenant_id FK, resource_key, strategy_type, limit_count, window_seconds,
                      burst_capacity, fallback_policy NULL, enabled, created_at, updated_at,
                      UNIQUE(tenant_id, resource_key))
```

Tokens em tabela própria (não coluna) para suportar rotação/histórico. Migrations via Flyway.

## Contrato REST (essencial)

Auth: `Authorization: Bearer <token>` (API/CRUD); sessão via cookie/Spring Security form-login para
o dashboard Thymeleaf (login separado do token de API).

- `POST /api/v1/tenants` (público) → `201 {tenantId, apiToken}` (token mostrado uma única vez)
- `POST /api/v1/tokens/rotate` (autenticado)
- `GET/POST /api/v1/resources`, `GET/PUT /api/v1/resources/{id}`, `DELETE` (soft-delete via `enabled=false`)
- `POST /api/v1/ratelimit/check` → `{allowed, limit, remaining, resetAt, retryAfterSeconds, strategy}`
- Erros via `ProblemDetail` (RFC 7807)
- `GET /actuator/health`, `/actuator/prometheus` (spec 6)

## Estratégia de testes por camada

- **`rls-domain`**: JUnit5 puro, sem Spring/Mockito, testes table-driven por estratégia, `Instant`
  controlado, cobrindo bordas. Considerar property-based (jqwik) para invariantes
  (`remaining` nunca negativo, `allowed` nunca excede `limit`). Este módulo carrega o TDD pedido —
  cobertura de branch completa nas 5 estratégias.
- **`rls-application`**: unit tests dos use cases com **fakes em memória** das portas.
- **`rls-adapter-redis`**: Testcontainers (Redis real), `EVAL` de fato; teste de concorrência (N
  chamadas paralelas na mesma chave, assert de exatamente `limit` permitidas — prova a atomicidade);
  testes de paridade Lua vs. domínio puro.
- **`rls-adapter-persistence`**: Testcontainers (Postgres real) + R2DBC, migrations Flyway.
- **`rls-adapter-rest`**: slice tests com `WebTestClient`; alguns `@SpringBootTest` +
  Testcontainers ponta-a-ponta (permitido/excedido/circuito aberto).
- **`rls-adapter-web`**: testes de renderização Thymeleaf via `WebTestClient`.
- **Contract tests**: suites abstratas por porta, satisfeitas por qualquer adapter.
- Surefire (unit, rápido) vs Failsafe (integração/Testcontainers) separados no Maven.

## Quebra em specs OpenSpec (ordem de execução)

1. **Setup do projeto + domínio core** — `git init` modelo git-flow (`main`/`develop`); parent POM +
   `rls-domain` + `rls-application` (esqueleto de portas); aggregates `Tenant`/`RateLimitResource` +
   VOs; `RateLimitStrategy` + 5 implementações puras com suíte TDD completa; `.gitignore`, README.
   → **status: proposta criada em `openspec/changes/setup-domain-core/`**
2. **Portas + adapter Redis** — `RateLimitEvaluationPort`; módulo `rls-adapter-redis` (Lettuce/Spring
   Data Redis Reactive), 5 scripts Lua, registry strategy→script; testes de concorrência e paridade.
3. **Adapter Postgres/R2DBC** — módulo `rls-adapter-persistence`; migrations Flyway; implementação
   de `TenantRepositoryPort`/`ResourceRepositoryPort`; hashing de senha/token.
4. **API REST reativa + Circuit Breaker** — módulo `rls-adapter-rest` (controllers, auth por API
   key, DTOs, ProblemDetail, OpenAPI); módulo `rls-adapter-resilience`; primeira materialização real
   de `rls-bootstrap`; testes e2e com Testcontainers.
5. **Front Thymeleaf** — módulo `rls-adapter-web`; onboarding, login (Spring Security
   form-login), dashboard CRUD reaproveitando os use cases da API REST; CSRF.
6. **Observabilidade + Docker** — Actuator/Micrometer, health indicators customizados
   (Redis/R2DBC/circuit breaker), `Dockerfile` multi-stage, `docker-compose.yml`
   (app+Redis+Postgres), documentação de execução local.

Cada spec vira uma `feature/*` branch a partir de `develop`, seguindo git flow.

## Arquivos críticos que ancoram a implementação (spec 1 em diante)

- `pom.xml` (parent) — reactor multi-módulo + dependencyManagement
- `rls-domain/.../ratelimit/RateLimitStrategy.java` — contrato pure-domain das 5 estratégias
- `rls-application/.../ratelimit/port/RateLimitEvaluationPort.java` — porta domínio/Redis
- `rls-adapter-redis/src/main/resources/scripts/*.lua` — os 5 scripts, autoridade real de produção
- `rls-adapter-persistence/.../db/migration/V1__init.sql` — schema Postgres

## Verificação (ao longo das specs)

- Cada spec só é considerada concluída com `mvn verify` passando (unit + integração via
  Testcontainers) no(s) módulo(s) que ela adiciona/modifica.
- Spec 2: teste de concorrência real contra Redis (N requisições paralelas) comprovando que não há
  race condition — critério de aceite explícito, não só "testes passam".
- Spec 4 em diante: subir a aplicação (`rls-bootstrap`) localmente e validar manualmente ao menos um
  ciclo completo — cadastrar tenant, cadastrar recurso, chamar `check` várias vezes até estourar o
  limite, confirmar `429` e headers corretos.
- Spec 6: `docker-compose up` de ponta a ponta (app+Redis+Postgres) como critério final de que a
  escalabilidade horizontal/stateless é real — subir 2 instâncias da app atrás do compose e repetir
  o teste de concorrência entre elas.

## Specs OpenSpec relacionadas

- `openspec/changes/setup-domain-core/` — spec 1 (proposal, design, specs e tasks já gerados e
  validados; pronta para `/opsx:apply`).
- Specs 2–6 serão criadas em `openspec/changes/` conforme forem propostas, seguindo esta mesma ordem.
