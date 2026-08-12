## Why

O projeto Rate Limiter as a Service ainda não existe: não há repositório git, nem esqueleto de
build, nem modelo de domínio. Antes de qualquer adapter (Redis, Postgres, REST, Thymeleaf) poder
ser construído, precisamos estabelecer a fundação sobre a qual tudo depende — o esqueleto de
módulos Maven que materializa fisicamente a arquitetura hexagonal, e o núcleo de domínio (DDD) que
é a fonte da verdade comportamental do sistema: os agregados `Tenant` e `RateLimitResource`, e as
cinco estratégias de rate limit (Fixed Window, Sliding Window Log, Sliding Window Counter, Token
Bucket, Leaky Bucket) como lógica pura, 100% testável via TDD, sem depender de infraestrutura real.
Sem essa base, nenhuma spec seguinte (adapter Redis, Postgres, REST, front) tem onde se apoiar.

## What Changes

- Inicializar o repositório git seguindo o modelo git-flow: branch `main`, branch `develop`,
  convenção de branches `feature/*`, `release/*`, `hotfix/*`; `.gitignore` para Java/Maven/IDE;
  README inicial descrevendo o projeto.
- Criar o parent `pom.xml` (reactor multi-módulo, `packaging=pom`), com `dependencyManagement`
  centralizando Spring Boot BOM, Reactor BOM, JUnit5, AssertJ e Testcontainers BOM, fixando Java 21.
- Criar o esqueleto físico dos módulos Maven que refletem a arquitetura hexagonal:
  `rls-domain`, `rls-application` (com os módulos de adapter/bootstrap ainda vazios/adiados para
  specs seguintes, apenas os dois primeiros ganham conteúdo real nesta spec).
- Modelar o domínio de **Tenant Management**: agregado `Tenant` (identidade, credenciais,
  `defaultFallbackPolicy`, status) e entidade filha `ApiToken` (suporte a rotação/histórico).
- Modelar o domínio de **Rate Limiting Enforcement**: agregado `RateLimitResource` (recurso
  configurado por um tenant, com sua própria estratégia, quota e política de fallback), e os Value
  Objects centrais (`RateLimitKey`, `Quota`, `RateLimitDecision`, `RateLimitState`, `ClientIp`,
  `FallbackPolicy`, `TenantId`, `ResourceId`).
- Implementar a interface `RateLimitStrategy` (pura, síncrona, sem I/O) e as cinco implementações
  concretas (Fixed Window, Sliding Window Log, Sliding Window Counter, Token Bucket via refill
  proporcional, Leaky Bucket via GCRA), cada uma com suíte de testes TDD cobrindo casos de borda
  (janela exata, burst, timing de refill/drain).
- Criar `StrategyRegistry` (factory de domínio) que mapeia `StrategyType` → implementação, para uso
  futuro pelos testes de paridade (spec 2) e pelo adapter Redis.
- Não há endpoints, persistência real ou infraestrutura externa nesta spec — é fundação de domínio
  e build, propositalmente sem I/O.

## Capabilities

### New Capabilities
- `rate-limit-strategy-evaluation`: avaliação pura das cinco estratégias de rate limit (decisão de
  permitir/negar + novo estado, dado estado atual, quota e instante), sem tocar infraestrutura.
- `tenant-management`: regras de domínio do agregado `Tenant` — identidade, credenciais, status,
  política de fallback padrão, e gestão de `ApiToken` (emissão, rotação, revogação).
- `rate-limit-resource-configuration`: regras de domínio do agregado `RateLimitResource` — um
  tenant configura recursos, cada um com sua própria estratégia, quota e política de fallback
  (herdada do tenant quando não definida), com unicidade de `resourceKey` por tenant.

### Modified Capabilities
(nenhuma — projeto greenfield, não há specs existentes)

## Impact

- **Novo repositório git** com histórico iniciando nesta spec (modelo git-flow).
- **Novo build Maven multi-módulo** (`rls-domain`, `rls-application` populados; demais módulos de
  adapter/bootstrap serão criados em specs futuras conforme `openspec/changes/setup-domain-core`
  não os inclui).
- **Nenhuma dependência externa em runtime** (sem Redis, sem Postgres, sem Spring Boot rodando)
  nesta spec — `rls-domain` é Java puro; `rls-application` introduz apenas `reactor-core` como
  contrato reativo para as portas que serão implementadas a partir da spec 2.
- Estabelece o vocabulário e os contratos (`RateLimitStrategy`, agregados, VOs) que as specs 2–6
  (adapter Redis, adapter Postgres, API REST + circuit breaker, front Thymeleaf, observabilidade)
  vão consumir.
