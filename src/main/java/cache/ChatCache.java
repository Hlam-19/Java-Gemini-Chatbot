package cache;

import config.Env;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import model.Message;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cache cho hoi thoai, gom ba viec:
 *
 *   1. Cache lich su tin nhan cua tung doan chat (do MySQL)
 *   2. Cache cau tra loi cua Gemini theo cau hoi (do quota API)
 *   3. Gioi han so lan goi Gemini moi phut (chong spam)
 *
 * Tat ca deu tu dong bo qua khi Redis khong san sang.
 */
public final class ChatCache {

    private static final String KEY_HISTORY = "history:";   // history:{sessionId}
    private static final String KEY_REPLY   = "reply:";     // reply:{hash cua cau hoi}
    private static final String KEY_RATE    = "rate:";      // rate:{userId}:{phut hien tai}

    /** Lich su song 1 gio - du lau de co ich, du ngan de khong lech qua xa. */
    private static final int HISTORY_TTL = Env.getInt("CACHE_HISTORY_TTL", 3600);

    /** Cau tra loi cua Gemini song 24 gio. */
    private static final int REPLY_TTL = Env.getInt("CACHE_REPLY_TTL", 86400);

    /** So lan duoc goi Gemini trong moi phut. */
    private static final int RATE_LIMIT = Env.getInt("RATE_LIMIT_PER_MINUTE", 20);

    private ChatCache() {
    }

    // =====================================================================
    // 1. CACHE LICH SU TIN NHAN
    // =====================================================================

    /** Lay lich su tu cache, tra ve null neu chua co (goi ben goi tu doc MySQL). */
    public static List<Message> getHistory(int sessionId) {
        String json = RedisClient.execute(
                jedis -> jedis.get(KEY_HISTORY + sessionId), null);

        if (json == null) {
            return null;
        }

        try {
            JSONArray arr = new JSONArray(json);
            List<Message> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Message m = new Message();
                m.setSessionId(sessionId);
                m.setRole(o.getString("role"));
                m.setContent(o.getString("content"));
                m.setAttachmentName(o.optString("aName", null));
                m.setAttachmentPath(o.optString("aPath", null));
                m.setAttachmentType(o.optString("aType", null));
                list.add(m);
            }
            return list;
        } catch (Exception e) {
            System.err.println("[ChatCache] Cache lich su hong, se doc lai tu DB: "
                    + e.getMessage());
            return null;
        }
    }

    /** Luu lich su vao cache sau khi doc tu MySQL. */
    public static void putHistory(int sessionId, List<Message> messages) {
        if (!RedisClient.isAvailable()) {
            return;
        }
        JSONArray arr = new JSONArray();
        for (Message m : messages) {
            JSONObject o = new JSONObject()
                    .put("role", m.getRole())
                    .put("content", m.getContent() == null ? "" : m.getContent());
            if (m.hasAttachment()) {
                o.put("aName", m.getAttachmentName())
                 .put("aPath", m.getAttachmentPath())
                 .put("aType", m.getAttachmentType());
            }
            arr.put(o);
        }
        RedisClient.run(jedis ->
                jedis.setex(KEY_HISTORY + sessionId, HISTORY_TTL, arr.toString()));
    }

    /** Xoa cache khi doan chat co thay doi (them tin nhan, xoa doan chat). */
    public static void invalidateHistory(int sessionId) {
        RedisClient.run(jedis -> jedis.del(KEY_HISTORY + sessionId));
    }

    // =====================================================================
    // 2. CACHE CAU TRA LOI CUA GEMINI
    // =====================================================================

    /**
     * Lay cau tra loi da luu cho mot cau hoi.
     *
     * Chi dung cho cau hoi DAU TIEN cua doan chat (khong co ngu canh truoc do),
     * vi cung mot cau hoi nhung ngu canh khac nhau thi cau tra loi phai khac.
     */
    public static String getReply(String prompt, String model) {
        String key = KEY_REPLY + hash(model + "|" + prompt);
        return RedisClient.execute(jedis -> jedis.get(key), null);
    }

    public static void putReply(String prompt, String model, String reply) {
        if (!RedisClient.isAvailable() || reply == null || reply.isBlank()) {
            return;
        }
        // Khong cache thong bao loi - lan sau co the goi lai thanh cong
        if (reply.startsWith("Loi") || reply.startsWith("Gemini dang qua tai")) {
            return;
        }
        String key = KEY_REPLY + hash(model + "|" + prompt);
        RedisClient.run(jedis -> jedis.setex(key, REPLY_TTL, reply));
    }

    // =====================================================================
    // 3. GIOI HAN SO LAN GOI (RATE LIMIT)
    // =====================================================================

    /**
     * Dem them mot lan goi va kiem tra con trong han muc khong.
     *
     * Dung cua so theo phut: moi phut la mot khoa rieng, het phut thi khoa
     * tu het han - khong can don dep.
     *
     * @return true neu duoc phep goi, false neu da vuot han muc
     */
    public static boolean allowRequest(int userId) {
        if (!RedisClient.isAvailable()) {
            return true;    // Khong co Redis thi khong gioi han
        }

        long minute = System.currentTimeMillis() / 60000;
        String key = KEY_RATE + userId + ":" + minute;

        Long count = RedisClient.execute(jedis -> {
            long c = jedis.incr(key);
            if (c == 1) {
                jedis.expire(key, 120);   // giu 2 phut cho chac
            }
            return c;
        }, 0L);

        return count == null || count <= RATE_LIMIT;
    }

    /** So lan con lai trong phut hien tai - dung de bao cho nguoi dung. */
    public static int remainingQuota(int userId) {
        if (!RedisClient.isAvailable()) {
            return RATE_LIMIT;
        }
        long minute = System.currentTimeMillis() / 60000;
        String key = KEY_RATE + userId + ":" + minute;

        String value = RedisClient.execute(jedis -> jedis.get(key), null);
        int used = 0;
        if (value != null) {
            try {
                used = Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // gia tri hong thi coi nhu chua dung lan nao
            }
        }
        return Math.max(0, RATE_LIMIT - used);
    }

    public static int rateLimit() {
        return RATE_LIMIT;
    }

    // =====================================================================

    /** Bam cau hoi thanh khoa ngan, tranh khoa qua dai trong Redis. */
    private static String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {          // 16 byte dau la du
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
