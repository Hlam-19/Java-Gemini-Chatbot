package web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

/** Cac ham tien ich dung chung cho cac handler HTTP. */
public final class Http {

    private Http() {
    }

    /** Doc toan bo body cua request duoi dang chuoi UTF-8. */
    public static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Doc body va parse thanh JSON. */
    public static JSONObject readJson(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        return body.isBlank() ? new JSONObject() : new JSONObject(body);
    }

    /** Gui ve mot doi tuong JSON. */
    public static void sendJson(HttpExchange ex, int status, JSONObject json) throws IOException {
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    /** Gui ve mot loi dang {"error": "..."}. */
    public static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, new JSONObject().put("error", message));
    }

    /** Doc gia tri mot cookie tu request. */
    public static String getCookie(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    /** Lay userId cua phien hien tai, null neu chua dang nhap. */
    public static Integer currentUserId(HttpExchange ex) {
        return SessionManager.getUserId(getCookie(ex, "SESSION"));
    }

    /** Doc mot tham so so nguyen tren URL, vd ?sessionId=5 - null neu khong co/khong hop le. */
    public static Integer queryInt(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                try {
                    return Integer.valueOf(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Kiem tra phuong thuc request, tra ve false va gui loi 405 neu sai. */
    public static boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
            sendError(ex, 405, "Phuong thuc khong duoc ho tro");
            return false;
        }
        return true;
    }
}
