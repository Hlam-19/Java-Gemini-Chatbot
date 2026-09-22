package cache;

import config.Env;
import java.time.Duration;
import java.util.function.Function;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;

/**
 * Ket noi Redis dung chung cho toan ung dung.
 *
 * Nguyen tac quan trong: Redis chi la lop tang toc, KHONG phai nguon du lieu
 * chinh. Neu Redis chet, ung dung van phai chay binh thuong bang MySQL -
 * moi thao tac deu bat loi va tra ve gia tri mac dinh thay vi nem ra ngoai.
 */
public final class RedisClient {

    private static JedisPool pool;
    private static boolean available = false;

    static {
        init();
    }

    private RedisClient() {
    }

    private static void init() {
        boolean enabled = Boolean.parseBoolean(Env.get("REDIS_ENABLED", "true"));
        if (!enabled) {
            System.out.println("[Redis] Da tat qua bien REDIS_ENABLED=false.");
            return;
        }

        String host = Env.get("REDIS_HOST", "localhost");
        int port = Env.getInt("REDIS_PORT", 6379);
        String password = Env.get("REDIS_PASSWORD", "");

        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(Env.getInt("REDIS_MAX_CONNECTIONS", 32));
            config.setMaxIdle(8);
            config.setMinIdle(2);
            config.setTestOnBorrow(true);

            pool = password.isBlank()
                    ? new JedisPool(config, host, port, 2000)
                    : new JedisPool(config, host, port, 2000, password);

            // Kiem tra that su ket noi duoc, khong chi tao pool
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            available = true;
            System.out.println("[Redis] Da ket noi " + host + ":" + port);

        } catch (Exception e) {
            available = false;
            System.err.println("[Redis] Khong ket noi duoc (" + host + ":" + port + "): "
                    + e.getMessage());
            System.err.println("[Redis] Ung dung van chay binh thuong, chi la khong co cache.");
        }
    }

    /** Redis co dung duoc khong - dung de bo qua cache khi mat ket noi. */
    public static boolean isAvailable() {
        return available && pool != null && !pool.isClosed();
    }

    /**
     * Chay mot lenh Redis, tu nuot loi va tra ve fallback neu that bai.
     * Nho vay noi goi khong can bat try/catch o moi cho.
     */
    public static <T> T execute(Function<Jedis, T> action, T fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        try (Jedis jedis = pool.getResource()) {
            return action.apply(jedis);
        } catch (Exception e) {
            System.err.println("[Redis] Loi khi thao tac: " + e.getMessage());
            return fallback;
        }
    }

    /** Chay mot lenh khong can ket qua tra ve. */
    public static void run(java.util.function.Consumer<Jedis> action) {
        execute(jedis -> { action.accept(jedis); return null; }, null);
    }

    /** Dong pool khi tat ung dung. */
    public static void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
            System.out.println("[Redis] Da dong ket noi.");
        }
    }
}
