-- KEYS[1] = redis key (HASH: prev, curr, window_start_ms)
-- ARGV[1] = limit
-- ARGV[2] = window_ms
-- ARGV[3] = burst capacity (unused by this strategy)
-- Returns "allowed,limit,remaining,reset_at_ms,retry_after_ms" (retry_after_ms=-1 when allowed=1)
-- Mirrors SlidingWindowCounterStrategy.java field-for-field so the two stay in parity.
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])

local time = redis.call('TIME')
local now_ms = math.floor(tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000)

local existing = redis.call('HMGET', key, 'prev', 'curr', 'window_start_ms')
local prev = tonumber(existing[1]) or 0
local curr = tonumber(existing[2]) or 0
local window_start_ms = tonumber(existing[3])
if window_start_ms == nil then
    window_start_ms = now_ms
end

local window_end_ms = window_start_ms + window_ms
if now_ms >= window_end_ms then
    local elapsed_windows = math.floor((now_ms - window_start_ms) / window_ms)
    if elapsed_windows == 1 then
        prev = curr
        curr = 0
        window_start_ms = window_start_ms + window_ms
    else
        prev = 0
        curr = 0
        window_start_ms = window_start_ms + (elapsed_windows * window_ms)
    end
end

local elapsed_fraction = (now_ms - window_start_ms) / window_ms
if elapsed_fraction < 0 then elapsed_fraction = 0 end
if elapsed_fraction > 1 then elapsed_fraction = 1 end

local reset_at_ms = window_start_ms + window_ms
local estimated_before = prev * (1 - elapsed_fraction) + curr

if estimated_before < limit then
    curr = curr + 1
    redis.call('HSET', key, 'prev', prev, 'curr', curr, 'window_start_ms', window_start_ms)
    redis.call('PEXPIRE', key, window_ms * 2)

    local estimated_after = prev * (1 - elapsed_fraction) + curr
    local remaining = math.floor(limit - estimated_after)
    if remaining < 0 then remaining = 0 end
    return table.concat({1, limit, remaining, reset_at_ms, -1}, ',')
end

redis.call('HSET', key, 'prev', prev, 'curr', curr, 'window_start_ms', window_start_ms)
redis.call('PEXPIRE', key, window_ms * 2)
local retry_after_ms = reset_at_ms - now_ms
return table.concat({0, limit, 0, reset_at_ms, retry_after_ms}, ',')
