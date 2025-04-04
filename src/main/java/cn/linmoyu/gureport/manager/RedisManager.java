package cn.linmoyu.gureport.manager;

import cn.linmoyu.gureport.Report;
import lombok.Getter;
import net.md_5.bungee.config.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Getter
public class RedisManager {
    private static volatile RedisManager instance;
    private final JedisPool jedisPool;

    private RedisManager(Configuration config) throws RedisInitException {
        String host = config.getString("host", "localhost");
        int port = config.getInt("port", 6379);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getInt("max-connections", 8));
        poolConfig.setMaxIdle(config.getInt("pool-config.max-idle", 5));
        poolConfig.setMinIdle(config.getInt("pool-config.min-idle", 1));

        // 创建连接池
        jedisPool = new JedisPool(
                poolConfig,
                host,
                port,
                5000,
                config.getString("username", null),
                config.getString("password", null),
                config.getBoolean("ssl", false)
        );
//        this.jedisPool = new JedisPool(
//                poolConfig,
//                host,
//                port,
//                config.getInt("timeout", 5000),
//                config.getString("password", ""),
//                config.getInt("database", 0),
//                null, // clientName
//                config.getBoolean("ssl", false)
//        );

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            Report.getInstance().getLogger().info("§aRedis连接成功! §7[" + host + ":" + port + "]");
        } catch (Exception e) {
            closePool();
            throw new RedisInitException("§cRedis初始化失败: " + e.getMessage(), e);
        }
    }

    public static synchronized void initialize(Configuration config) throws RedisInitException {
        if (instance != null) {
            throw new IllegalStateException("§cRedisManager已被初始化过, 请不要重复加载!");
        }
        instance = new RedisManager(config);
    }

    public static RedisManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RedisManager未初始化.");
        }
        return instance;
    }

    public void closePool() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.clear();
            jedisPool.close();
            Report.getInstance().getLogger().info("§cRedis连接池已关闭.");
        }
    }

    public static class RedisInitException extends Exception {
        public RedisInitException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}