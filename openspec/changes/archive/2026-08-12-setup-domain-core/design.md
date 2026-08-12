## Context

Este é o primeiro change do projeto Rate Limiter as a Service, um sistema Java reativo que oferece
rate limiting como serviço para múltiplos tenants, cada um escolhendo por recurso uma entre cinco
estratégias (Fixed Window, Sliding Window Log, Sliding Window Counter, Token Bucket, Leaky Bucket).
O projeto inteiro será entregue por specs sucessivas seguindo arquitetura hexagonal, DDD e TDD, com
requisito arquitetural de stateless/escalabilidade horizontal e ausência de race conditions sob
concorrência entre instâncias.

Esta spec estabelece a fundação: repositório git (modelo git-flow), esqueleto Maven multi-módulo e o
núcleo de domínio (agregados `Tenant`/`RateLimitResource` e as cinco estratégias como lógica pura).
Não há infraestrutura real envolvida ainda — isso é proposital, para que o domínio seja 100%
testável via TDD sem depender de Redis/Postgres, que só entram nas specs 2 e 3.

## Goals / Non-Goals

**Goals:**
- Estabelecer o esqueleto Maven multi-módulo que reflete fisicamente a arquitetura hexagonal
  (`rls-domain`, `rls-application` com conteúdo real; demais módulos referenciados no `pom.xml`
  parent conforme forem criados nas próximas specs, para não gerar módulos vazios sem propósito
  nesta spec).
- Modelar os agregados `Tenant` e `RateLimitResource` e os Value Objects associados, com regras de
  domínio (invariantes) aplicadas nos próprios construtores/métodos de fábrica dos agregados.
- Implementar as cinco estratégias de rate limit como funções puras, testáveis por TDD, servindo
  como especificação executável do comportamento que os scripts Lua (spec 2) deverão replicar.
- Inicializar o repositório com o modelo git-flow.

**Non-Goals:**
- Nenhuma persistência real (Postgres/R2DBC) — fica para a spec 3.
- Nenhuma chamada a Redis / script Lua — a versão pura de domínio não é a que roda em produção sob
  concorrência real; isso é responsabilidade da spec 2.
- Nenhum endpoint HTTP, controller ou aplicação Spring Boot executável — fica para a spec 4.
- Sem circuit breaker, sem autenticação — fora do escopo desta spec.

## Decisions

**1. `rls-domain` não depende de nenhum framework (nem Reactor).**
As cinco estratégias são funções síncronas puras (`RateLimitStrategy.evaluate(state, quota, now) ->
decision + newState`). Alternativa considerada: já modelar como `Mono<RateLimitDecision>` desde o
domínio, para "encaixar" direto na aplicação reativa. Rejeitada porque acopla o domínio a um
framework reativo sem necessidade — a decisão de rate limit é, em si, um cálculo síncrono; a
reatividade pertence à infraestrutura (I/O de rede com Redis), não ao cálculo. Isso também torna os
testes de domínio triviais (sem `StepVerifier`, sem scheduler).

**2. A versão Java pura das estratégias não é a autoridade de produção — é a especificação
executável.**
Sob concorrência real com múltiplas instâncias da aplicação, apenas um script Lua atômico executado
dentro do Redis pode garantir ausência de race condition (será implementado na spec 2). A versão de
domínio criada nesta spec serve para: (a) TDD do comportamento de cada algoritmo isoladamente; (b)
"golden spec" contra a qual os testes de paridade da spec 2 vão validar os scripts Lua, chamando
ambas as versões com a mesma sequência determinística de eventos e comparando decisões. Alternativa
considerada: pular a versão Java e escrever só os scripts Lua direto. Rejeitada porque violaria o
requisito explícito de TDD do usuário (Lua é difícil de testar unitariamente de forma rápida e
isolada) e removeria a rede de segurança de paridade.

**3. `RateLimitResource` é um Aggregate Root independente, não uma entidade filha de `Tenant`.**
Cada recurso é criado/editado/consultado individualmente (CRUD granular via API e dashboard).
Modelá-lo como filho de `Tenant` forçaria carregar/travar o agregado inteiro do tenant (incluindo
todos os outros recursos) a cada edição. Trade-off aceito: consistência entre `Tenant` e seus
`RateLimitResource`s é eventual/por convenção (referenciados por `TenantId`), não transacional
única — aceitável porque não há invariante que exija atomicidade entre os dois além da existência do
tenant, validada na camada de aplicação antes da escrita.

**4. `ApiToken` é entidade filha de `Tenant`, não Aggregate Root próprio.**
Ao contrário de `RateLimitResource`, o ciclo de vida de tokens (emissão, rotação, revogação) é
sempre disparado a partir de operações do tenant e precisa manter invariantes simples (ex.: não
permitir dois tokens ativos "principais" simultaneamente, se essa regra vier a existir) dentro da
mesma transação/consistência do agregado `Tenant`.

**5. `FallbackPolicy` em `RateLimitResource` é nullable e herda de `Tenant.defaultFallbackPolicy`
quando ausente.**
Reflete a decisão já validada com o usuário de que o fallback do circuit breaker é configurável por
tenant/recurso. Modelar como nullable no VO/entidade evita duplicar a política em todo recurso
cadastrado quando o tenant só quer um padrão global.

**6. Estrutura Maven: apenas `rls-domain` e `rls-application` ganham conteúdo nesta spec.**
Os demais módulos (`rls-adapter-redis`, `rls-adapter-persistence`, `rls-adapter-resilience`,
`rls-adapter-rest`, `rls-adapter-web`, `rls-bootstrap`) serão adicionados ao `pom.xml` parent e
populados nas specs 2–6, na ordem definida. Criar módulos vazios agora foi considerado e rejeitado —
não há valor em placeholders sem conteúdo; cada spec futura já inclui a criação do seu próprio
módulo como parte do escopo.

## Risks / Trade-offs

- [Risco] A versão Java pura e os futuros scripts Lua divergirem sutilmente (ex.: arredondamento de
  ponto flutuante no Token Bucket) → Mitigação: testes de paridade obrigatórios na spec 2, comparando
  ambas as implementações com a mesma sequência de eventos determinística antes de considerar a spec
  2 concluída.
- [Risco] Modelar `RateLimitResource` como agregado independente de `Tenant` permite, em teoria,
  criar um recurso apontando para um `TenantId` inexistente se a validação de aplicação falhar →
  Mitigação: a validação de existência do tenant é responsabilidade explícita do use case
  (`CreateResourceUseCase`, spec 4), documentada aqui para não ser esquecida; o agregado em si não
  pode impor essa checagem (não tem acesso a repositório).
- [Trade-off] Sem Reactor no domínio, a assinatura de `RateLimitStrategy.evaluate` é síncrona — isso
  significa que a camada de aplicação (spec 2+) precisa envolver a chamada em `Mono.fromCallable`
  ou equivalente quando compuser com portas reativas. Aceito conscientemente pela clareza de testes.

## Migration Plan

Não aplicável — projeto greenfield, primeiro change. Não há dados ou sistema em produção a migrar.

## Open Questions

- Nenhuma pendente para esta spec. Questões sobre paridade Lua/domínio, cache write-through e
  invariantes de token "principal" ficam registradas nas specs 2 e 3, quando o contexto de
  infraestrutura real existir para decidi-las com mais informação.
