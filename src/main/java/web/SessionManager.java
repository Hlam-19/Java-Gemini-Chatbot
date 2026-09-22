package web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly phien dang nhap trong bo nho.
 * Moi session la mot chuoi ngau nhien gui ve trinh duyet qua cookie.
 * Luu y: restart server thi tat ca phien se mat (chap nhan duoc voi du an hoc tap).
 */
public final class SessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Integer> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    /** Tao phien moi cho userId, tra ve token de set vao cookie. */
    public static String create(int userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SESSIONS.put(token, userId);
        return token;
    }

    /** Tra ve userId cua phien, hoac null neu token khong hop le. */
    public static Integer getUserId(String token) {
        return token == null ? null : SESSIONS.get(token);
    }

    public static void remove(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }
}
