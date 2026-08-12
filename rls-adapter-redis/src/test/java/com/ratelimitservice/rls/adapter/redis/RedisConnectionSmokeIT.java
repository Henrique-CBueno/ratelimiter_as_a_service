package com.ratelimitservice.rls.adapter.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.test.StepVerifier;

import java.util.List;

class RedisConnectionSmokeIT extends AbstractRedisIT {

    @Test
    void canPingTheContainer() {
        StepVerifier.create(REDIS_TEMPLATE.getConnectionFactory().getReactiveConnection().ping())
                .expectNext("PONG")
                .verifyComplete();
    }

    @Test
    void canExecuteATrivialEvalScript() {
        RedisScript<Long> script = RedisScript.of("return 1", Long.class);

        StepVerifier.create(REDIS_TEMPLATE.execute(script, List.of()))
                .expectNext(1L)
                .verifyComplete();
    }
}
