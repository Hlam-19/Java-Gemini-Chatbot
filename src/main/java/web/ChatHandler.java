package web;

import cache.ChatCache;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dao.MessageDAO;
import dao.SessionDAO;
import java.io.IOException;
import java.util.List;
import model.Message;
import org.json.JSONArray;
import org.json.JSONObject;
import service.GeminiService;

/**
 * Xu ly hoi thoai:
 *   POST /api/chat                 - gui tin nhan trong mot doan chat
 *   GET  /api/history?sessionId=N  - lay lich su cua doan chat
 */
public class ChatHandler implements HttpHandler {

    /** So tin nhan gan nhat gui kem lam ngu canh cho Gemini. */
    private static final int CONTEXT_SIZE = 20;

    /** Do dai toi da cua tieu de tu sinh tu cau hoi dau tien. */
    private static final int AUTO_TITLE_LENGTH = 60;

    private final MessageDAO messageDAO = new MessageDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final GeminiService gemini = new GeminiService();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Integer userId = Http.currentUserId(ex);
        if (userId == null) {
            Http.sendError(ex, 401, "Vui long dang nhap truoc");
            return;
        }

        try {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/api/history")) {
                history(ex, userId);
            } else if (path.equals("/api/chat")) {
                chat(ex, userId);
            } else {
                Http.sendError(ex, 404, "Khong tim thay duong dan");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Http.sendError(ex, 500, "Loi may chu: " + e.getMessage());
        }
    }

    private void history(HttpExchange ex, int userId) throws IOException {
        if (!Http.requireMethod(ex, "GET")) {
            return;
        }
        Integer sessionId = Http.queryInt(ex, "sessionId");
        if (sessionId == null) {
            Http.sendError(ex, 400, "Thieu tham so sessionId");
            return;
        }
        if (!sessionDAO.belongsTo(sessionId, userId)) {
            Http.sendError(ex, 404, "Khong tim thay doan chat");
            return;
        }

        JSONArray arr = new JSONArray();
        for (Message m : messageDAO.getHistory(sessionId)) {
            arr.put(new JSONObject()
                    .put("role", m.getRole())
                    .put("content", m.getContent())
                    .put("createdAt", String.valueOf(m.getCreatedAt())));
        }
        Http.sendJson(ex, 200, new JSONObject().put("messages", arr));
    }

    private void chat(HttpExchange ex, int userId) throws Exception {
        if (!Http.requireMethod(ex, "POST")) {
            return;
        }
        JSONObject body = Http.readJson(ex);
        String prompt = body.optString("message", "").trim();
        int sessionId = body.optInt("sessionId", -1);

        if (prompt.isEmpty()) {
            Http.sendError(ex, 400, "Tin nhan khong duoc de trong");
            return;
        }

        // Chan spam truoc khi lam bat cu viec gi ton kem
        if (!ChatCache.allowRequest(userId)) {
            Http.sendError(ex, 429, "Ban gui qua nhanh. Vui long cho mot phut roi thu lai "
                    + "(gioi han " + ChatCache.rateLimit() + " tin nhan moi phut).");
            return;
        }

        // Chua co doan chat nao -> tu tao moi, lay cau hoi dau lam tieu de
        boolean isNewSession = false;
        if (sessionId <= 0) {
            sessionId = sessionDAO.create(userId, autoTitle(prompt));
            if (sessionId < 0) {
                Http.sendError(ex, 500, "Khong tao duoc doan chat");
                return;
            }
            isNewSession = true;
        } else if (!sessionDAO.belongsTo(sessionId, userId)) {
            Http.sendError(ex, 404, "Khong tim thay doan chat");
            return;
        }

        // Lay ngu canh truoc khi luu cau hoi moi, de khong lap lai chinh no
        List<Message> history = messageDAO.getHistory(sessionId);
        if (history.size() > CONTEXT_SIZE) {
            history = history.subList(history.size() - CONTEXT_SIZE, history.size());
        }

        messageDAO.saveMessage(userId, sessionId, "user", prompt);

        // Cau hoi dau tien cua doan chat thi co the dung lai cau tra loi cu,
        // vi luc nay chua co ngu canh rieng nao anh huong den ket qua.
        String reply = null;
        boolean cacheable = history.isEmpty();
        if (cacheable) {
            reply = ChatCache.getReply(prompt, gemini.getModel());
        }

        boolean fromCache = reply != null;
        if (!fromCache) {
            reply = gemini.askGemini(prompt, history);
            if (cacheable) {
                ChatCache.putReply(prompt, gemini.getModel(), reply);
            }
        }

        messageDAO.saveMessage(userId, sessionId, "model", reply);
        sessionDAO.touch(sessionId);

        JSONObject result = new JSONObject()
                .put("reply", reply)
                .put("sessionId", sessionId)
                .put("cached", fromCache)
                .put("remaining", ChatCache.remainingQuota(userId));
        if (isNewSession) {
            result.put("newSession", true).put("title", autoTitle(prompt));
        }
        Http.sendJson(ex, 200, result);
    }

    /** Lay cau hoi dau tien lam ten doan chat, cat ngan neu qua dai. */
    private String autoTitle(String prompt) {
        String title = prompt.replaceAll("\s+", " ").trim();
        if (title.length() > AUTO_TITLE_LENGTH) {
            title = title.substring(0, AUTO_TITLE_LENGTH).trim() + "...";
        }
        return title;
    }
}
