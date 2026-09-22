package web;

import cache.RedisClient;
import config.Env;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly phien dang nhap.
 *
 * Phien duoc luu trong Redis kem han dung (TTL), nho vay:
 *   - Restart server khong lam nguoi dung bi dang xuat
 *   - Phien cu tu het han, khong dong rac trong bo nho
 *
 * Neu Redis khong dung duoc, tu dong quay ve luu trong bo nho (nhu truoc day)
 * de ung dung van chay - chi mat phien khi restart.
 */
public final class SessionManager {

    private static final String KEY_PREFIX = "session:";

    /** Han dung cua phien, tinh bang giay. Mac dinh 7 ngay. */
    private static final int TTL_SECONDS = Env.getInt("SESSION_TTL_SECONDS", 7 * 24 * 3600);

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Du phong khi Redis chet. */
    private static final Map<String, Integer> FALLBACK = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    /** Tao phien moi cho userId, tra ve token de dat vao cookie. */
    public static String create(int userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Luon ghi vao bo nho, ke ca khi co Redis: neu Redis chet ngay sau lenh
        // setex (hoac lenh do that bai am tham), phien van con dung duoc.
        FALLBACK.put(token, userId);

        RedisClient.run(jedis ->
                jedis.setex(KEY_PREFIX + token, TTL_SECONDS, String.valueOf(userId)));

        return token;
    }

    /** Tra ve userId cua phien, hoac null neu token khong hop le / da het han. */
    public static Integer getUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String value = RedisClient.execute(jedis -> {
            String v = jedis.get(KEY_PREFIX + token);
            // Con hoat dong thi gia han them - phien khong het giua chung
            if (v != null) {
                jedis.expire(KEY_PREFIX + token, TTL_SECONDS);
            }
            return v;
        }, null);

        if (value != null) {
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Redis khong tra ve gi (mat ket noi, hoac phien duoc tao luc Redis dang chet)
        // -> tra tiep trong bo nho truoc khi ket luan la khong hop le.
        return FALLBACK.get(token);
    }

    /** Xoa phien khi dang xuat. */
    public static void remove(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (RedisClient.isAvailable()) {
            RedisClient.run(jedis -> jedis.del(KEY_PREFIX + token));
        }
        FALLBACK.remove(token);
    }
}
