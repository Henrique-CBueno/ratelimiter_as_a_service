-- KEYS[1] = redis key (STRING: theoretical arrival time, in ms)
-- ARGV[1] = limit
-- ARGV[2] = window_ms
-- ARGV[3] = burst capacity
-- Returns "allowed,limit,remaining,reset_at_ms,retry_after_ms" (retry_after_ms=-1 when allowed=1)
-- GCRA. tau (burst tolerance) = (burstCapacity - 1) * emissionInterval, matching
-- LeakyBucketStrategy.java exactly (an off-by-one here was caught by that class's own tests).
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])

local time = redis.call('TIME')
local now_ms = math.floor(tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000)

local stored_tat = tonumber(redis.call('GET', key))
if stored_tat == nil then
    stored_tat = now_ms
end

local emission_interval_ms = window_ms / limit
local tau_ms = emission_interval_ms * (capacity - 1)

local reference_tat = stored_tat
if now_ms > stored_tat then
    reference_tat = now_ms
end
local allow_at_ms = reference_tat - tau_ms

if now_ms >= allow_at_ms then
    local new_tat = reference_tat + emission_interval_ms
    redis.call('SET', key, new_tat, 'PX', math.floor(tau_ms + emission_interval_ms + window_ms))

    local headroom_ms = tau_ms - (new_tat - now_ms)
    local remaining = 0
    if headroom_ms > 0 then
        remaining = math.floor(headroom_ms / emission_interval_ms)
    end
    if remaining > limit then remaining = limit end

    return table.concat({1, limit, remaining, math.floor(new_tat), -1}, ',')
end

local retry_after_ms = allow_at_ms - now_ms
return table.concat({0, limit, 0, math.floor(stored_tat), math.ceil(retry_after_ms)}, ',')
