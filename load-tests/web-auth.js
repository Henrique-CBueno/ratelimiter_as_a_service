import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/http.js';

export const options = {
    scenarios: {
        login: {
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

const PASSWORD = 'LoadTest123!';

export function setup() {
    const email = `web-load-${Date.now()}-${Math.random().toString(36).slice(2)}@loadtest.local`;

    const registerForm = http.get(`${BASE_URL}/app/register`);
    const registerRes = http.post(`${BASE_URL}/app/register`, {
        _csrf: csrfToken(registerForm),
        name: 'Web Load Test',
        email,
        password: PASSWORD,
    });

    if (registerRes.status !== 200) {
        throw new Error(`Web registration failed: ${registerRes.status}`);
    }

    return { email };
}

export default function (data) {
    // Each VU reuses the same cookie jar across iterations; clear it so every iteration starts
    // from an unauthenticated GET /app/login, matching a real concurrent-logins scenario instead
    // of reusing a session from a previous iteration.
    http.cookieJar().clear(BASE_URL);

    const loginForm = http.get(`${BASE_URL}/app/login`);
    const loginRes = http.post(`${BASE_URL}/app/login`, {
        _csrf: csrfToken(loginForm),
        username: data.email,
        password: PASSWORD,
    }, { redirects: 0 });

    check(loginRes, {
        'login redirects to resources': (r) => r.status === 302 && (r.headers['Location'] || '').includes('/app/resources'),
    });
}

function csrfToken(res) {
    return res.html().find('input[name="_csrf"]').first().attr('value');
}
