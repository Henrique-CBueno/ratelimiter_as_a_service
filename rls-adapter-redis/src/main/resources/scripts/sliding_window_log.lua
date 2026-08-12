-- KEYS[1] = redis key (a ZSET of request timestamps)
-- ARGV[1] = limit
-- ARGV[2] = window_ms
-- ARGV[3] = burst capacity (unused by this strategy)
-- Returns "allowed,limit,remaining,reset_at_ms,retry_after_ms" (retry_after_ms=-1 when allowed=1)
-- A companion "<key>:seq" counter guarantees unique ZSET members even when many requests land in
-- the same millisecond, since ZADD would otherwise silently collapse identical members.
local key = KEYS[1]
local seq_key = key .. ':seq'
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])

local time = redis.call('TIME')
local now_ms = math.floor(tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000)
local cutoff = now_ms - window_ms

redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. cutoff)
local count = redis.call('ZCARD', key)

local function reset_at_ms_from_oldest()
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if oldest[2] ~= nil then
        return tonumber(oldest[2]) + window_ms
    end
    return now_ms + window_ms
end

if count < limit then
    local seq = redis.call('INCR', seq_key)
    redis.call('ZADD', key, now_ms, now_ms .. '-' .. seq)
    redis.call('PEXPIRE', key, window_ms * 2)
    redis.call('PEXPIRE', seq_key, window_ms * 2)

    local remaining = limit - (count + 1)
    return table.concat({1, limit, remaining, reset_at_ms_from_oldest(), -1}, ',')
end

local reset_at_ms = reset_at_ms_from_oldest()
local retry_after_ms = reset_at_ms - now_ms
return table.concat({0, limit, 0, reset_at_ms, retry_after_ms}, ',')
