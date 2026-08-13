import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, jsonHeaders } from './lib/http.js';

export const options = {
    scenarios: {
        onboarding: {
            executor: 'constant-vus',
            vus: 10,
            duration: '20s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<800'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const email = `onboard-${__VU}-${__ITER}-${Date.now()}@loadtest.local`;

    const res = http.post(`${BASE_URL}/api/v1/tenants`, JSON.stringify({
        name: `Load Test Tenant ${__VU}-${__ITER}`,
        email,
        password: 'LoadTest123!',
    }), { headers: jsonHeaders() });

    check(res, {
        'tenant created (201)': (r) => r.status === 201,
        'response has apiToken': (r) => {
            try {
                return !!r.json('apiToken');
            } catch (e) {
                return false;
            }
        },
    });
}
