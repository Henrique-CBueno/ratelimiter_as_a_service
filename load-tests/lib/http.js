import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function jsonHeaders(apiToken) {
    const headers = { 'Content-Type': 'application/json' };
    if (apiToken) {
        headers['Authorization'] = `Bearer ${apiToken}`;
    }
    return headers;
}

// Registers a fresh tenant via the real API. Returns { tenantId, apiToken }.
export function registerTenant(namePrefix) {
    const email = `${namePrefix}-${__VU}-${Date.now()}-${Math.random().toString(36).slice(2)}@loadtest.local`;
    const res = http.post(`${BASE_URL}/api/v1/tenants`, JSON.stringify({
        name: namePrefix,
        email,
        password: 'LoadTest123!',
    }), { headers: jsonHeaders() });

    if (res.status !== 201) {
        throw new Error(`Tenant registration failed: ${res.status} ${res.body}`);
    }
    return res.json();
}

// Creates a resource for the given tenant via the real API. Returns the created ResourceResponse.
export function createResource(apiToken, resourceKey, strategyType, limit, windowSeconds, burstCapacity) {
    const res = http.post(`${BASE_URL}/api/v1/resources`, JSON.stringify({
        resourceKey,
        strategyType,
        limit,
        windowSeconds,
        burstCapacity: burstCapacity || null,
        fallbackPolicy: null,
    }), { headers: jsonHeaders(apiToken) });

    if (res.status !== 201) {
        throw new Error(`Resource creation failed: ${res.status} ${res.body}`);
    }
    return res.json();
}
