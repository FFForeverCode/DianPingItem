-- lua脚本实现 校验分布式锁+释放分布式锁 操作的原子性

--KEYS:传入的key   ARGV：传入的参数，本线程id
--注意：lua中数组下标从 1 开始
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    --校验成功，释放锁
    return redis.call('del',KEYS[1])
end
--校验失败
return 0
