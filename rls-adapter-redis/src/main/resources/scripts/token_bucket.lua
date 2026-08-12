-- KEYS[1] = redis key (HASH: tokens, last_refill_ms)
-- ARGV[1] = limit
-- ARGV[2] = window_ms
-- ARGV[3] = burst capacity
-- Returns "allowed,limit,remaining,reset_at_ms,retry_after_ms" (retry_after_ms=-1 when allowed=1)
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])

local time = redis.call('TIME')
local now_ms = math.floor(tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000)

local existing = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens = tonumber(existing[1])
local last_refill_ms = tonumber(existing[2])
if tokens == nil or last_refill_ms == nil then
    tokens = capacity
    last_refill_ms = now_ms
end

local refill_rate_per_ms = limit / window_ms
local elapsed_ms = now_ms - last_refill_ms
if elapsed_ms < 0 then elapsed_ms = 0 end

local refilled = tokens + (elapsed_ms * refill_rate_per_ms)
if refilled > capacity then refilled = capacity end

if refilled >= 1.0 then
    local remaining_tokens = refilled - 1.0
    redis.call('HSET', key, 'tokens', remaining_tokens, 'last_refill_ms', now_ms)
    redis.call('PEXPIRE', key, window_ms * 2)

    local ms_to_full = (capacity - remaining_tokens) / refill_rate_per_ms
    local reset_at_ms = now_ms + math.floor(ms_to_full)
    return table.concat({1, limit, math.floor(remaining_tokens), reset_at_ms, -1}, ',')
end

redis.call('HSET', key, 'tokens', refilled, 'last_refill_ms', now_ms)
redis.call('PEXPIRE', key, window_ms * 2)

local ms_to_next_token = (1.0 - refilled) / refill_rate_per_ms
local reset_at_ms = now_ms + math.ceil(ms_to_next_token)
local retry_after_ms = reset_at_ms - now_ms
return table.concat({0, limit, 0, reset_at_ms, retry_after_ms}, ',')
