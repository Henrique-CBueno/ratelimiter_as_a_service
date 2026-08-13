import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, jsonHeaders, registerTenant, createResource } from './lib/http.js';

// 429 is an expected outcome of exceeding the limit, not an infra failure — exclude it (along
// with the normal 200) from k6's default http_req_failed classification, which otherwise treats
// any 4xx/5xx as a failure.
http.setResponseCallback(http.expectedStatuses(200, 429));

const RESOURCE_KEY = '/load-test-check';
const CLIENT_IP = '203.0.113.50';
const LIMIT = 50;
const WINDOW_SECONDS = 60;

const allowedCount = new Counter('rate_limit_allowed_total');
const limitedCount = new Counter('rate_limit_limited_total');
const checkLatency = new Trend('check_latency_ms', true);

export const options = {
    scenarios: {
        burst: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        check_latency_ms: ['p(95)<500'],
        // The window (60s) outlives the run (30s), so no rollover happens — the allowed count
        // must not exceed the configured limit, with a small margin for the strategy's documented
        // edge-of-window burst behavior.
        rate_limit_allowed_total: ['count<=55'],
    },
};

export function setup() {
    const { apiToken } = registerTenant('load-check');
    createResource(apiToken, RESOURCE_KEY, 'FIXED_WINDOW', LIMIT, WINDOW_SECONDS);
    return { apiToken };
}

export default function (data) {
    const res = http.post(`${BASE_URL}/api/v1/ratelimit/check`, JSON.stringify({
        resource: RESOURCE_KEY,
        clientIp: CLIENT_IP,
    }), { headers: jsonHeaders(data.apiToken) });

    checkLatency.add(res.timings.duration);

    check(res, {
        'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    });

    if (res.status === 200) {
        allowedCount.add(1);
    } else if (res.status === 429) {
        limitedCount.add(1);
    }
}
