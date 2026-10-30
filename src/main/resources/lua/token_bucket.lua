-- KEYS[1]: 桶 key，如 rl:user:write:1
-- ARGV[1]: capacity 容量  ARGV[2]: rate 每秒补几个
-- ARGV[3]: now 毫秒时间戳  ARGV[4]: ttl 秒
-- 返回: {是否放行, 剩余令牌, 建议重试秒数}
local cap  = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now  = tonumber(ARGV[3])

local data   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts     = tonumber(data[2])

if tokens == nil then
  tokens = cap
  ts = now
end

local elapsed = math.max(0, now - ts) / 1000.0
tokens = math.min(cap, tokens + elapsed * rate)

local allowed = 0
local retry = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retry = math.ceil((1 - tokens) / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], ARGV[4])

return { allowed, math.floor(tokens), retry }