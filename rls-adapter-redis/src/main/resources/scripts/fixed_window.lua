-- KEYS[1] = redis key
-- ARGV[1] = limit
-- ARGV[2] = window_ms
-- ARGV[3] = burst capacity (unused by this strategy)
-- Returns "allowed,limit,remaining,reset_at_ms,retry_after_ms" (retry_after_ms=-1 when allowed=1)
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])

local time = redis.call('TIME')
local now_ms = math.floor(tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000)

local count = redis.call('INCR', key)
if count == 1 then
    redis.call('PEXPIRE', key, window_ms)
end

local ttl = redis.call('PTTL', key)
if ttl < 0 then
    ttl = window_ms
end
local reset_at_ms = now_ms + ttl

if count > limit then
    redis.call('DECR', key)
    return table.concat({0, limit, 0, reset_at_ms, ttl}, ',')
end

local remaining = limit - count
return table.concat({1, limit, remaining, reset_at_ms, -1}, ',')
