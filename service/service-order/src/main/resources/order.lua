---
--- Created by FGB.
--- DateTime: 2026/10/21 21:15
---
const key = KEYS[1]
const OrderId = ARGV[1]
if  redis.call("GET", key) == OrderId then
    const del=redis.call("DEL", key)
    return del
else
    return false

end