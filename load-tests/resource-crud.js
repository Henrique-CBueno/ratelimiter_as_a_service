import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, jsonHeaders, registerTenant } from './lib/http.js';

export const options = {
    scenarios: {
        crud: {
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

export function setup() {
    const { apiToken } = registerTenant('load-crud');
    return { apiToken };
}

export default function (data) {
    const headers = jsonHeaders(data.apiToken);
    const resourceKey = `/load-crud-${__VU}-${__ITER}-${Date.now()}`;

    const createRes = http.post(`${BASE_URL}/api/v1/resources`, JSON.stringify({
        resourceKey,
        strategyType: 'TOKEN_BUCKET',
        limit: 20,
        windowSeconds: 30,
    }), { headers });

    const created = check(createRes, { 'resource created (201)': (r) => r.status === 201 });
    if (!created) {
        return;
    }
    const resourceId = createRes.json('id');

    const updateRes = http.put(`${BASE_URL}/api/v1/resources/${resourceId}`, JSON.stringify({
        strategyType: 'TOKEN_BUCKET',
        limit: 40,
        windowSeconds: 30,
    }), { headers });
    check(updateRes, { 'resource updated (200)': (r) => r.status === 200 });

    const deleteRes = http.del(`${BASE_URL}/api/v1/resources/${resourceId}`, null, { headers });
    check(deleteRes, { 'resource deleted (204)': (r) => r.status === 204 });
}
