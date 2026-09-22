package web;

import cache.ChatCache;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dao.MessageDAO;
import dao.SessionDAO;
import java.io.IOException;
import model.ChatSession;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Quan ly cac doan chat (session):
 *   GET    /api/sessions          - danh sach
 *   POST   /api/sessions          - tao moi
 *   PATCH  /api/sessions/{id}     - doi ten
 *   DELETE /api/sessions/{id}     - xoa
 */
public class SessionHandler implements HttpHandler {

    private static final int MAX_TITLE_LENGTH = 255;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Integer userId = Http.currentUserId(ex);
        if (userId == null) {
            Http.sendError(ex, 401, "Vui long dang nhap truoc");
            return;
        }

        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            // /api/sessions  hoac  /api/sessions/{id}
            String rest = path.substring("/api/sessions".length());

            if (rest.isEmpty() || rest.equals("/")) {
                switch (method.toUpperCase()) {
                    case "GET"  -> list(ex, userId);
                    case "POST" -> create(ex, userId);
                    default     -> Http.sendError(ex, 405, "Phuong thuc khong duoc ho tro");
                }
                return;
            }

            int sessionId;
            try {
                sessionId = Integer.parseInt(rest.substring(1));
            } catch (NumberFormatException e) {
                Http.sendError(ex, 400, "Ma doan chat khong hop le");
                return;
            }

            // Chan truy cap doan chat cua nguoi khac
            if (!sessionDAO.belongsTo(sessionId, userId)) {
                Http.sendError(ex, 404, "Khong tim thay doan chat");
                return;
            }

            switch (method.toUpperCase()) {
                case "PATCH"  -> rename(ex, userId, sessionId);
                case "DELETE" -> delete(ex, userId, sessionId);
                default       -> Http.sendError(ex, 405, "Phuong thuc khong duoc ho tro");
            }

        } catch (Exception e) {
            e.printStackTrace();
            Http.sendError(ex, 500, "Loi may chu: " + e.getMessage());
        }
    }

    private void list(HttpExchange ex, int userId) throws IOException {
        JSONArray arr = new JSONArray();
        for (ChatSession s : sessionDAO.listByUser(userId)) {
            arr.put(new JSONObject()
                    .put("id", s.getId())
                    .put("title", s.getTitle())
                    .put("messageCount", messageDAO.countBySession(s.getId()))
                    .put("updatedAt", String.valueOf(s.getUpdatedAt())));
        }
        Http.sendJson(ex, 200, new JSONObject().put("sessions", arr));
    }

    private void create(HttpExchange ex, int userId) throws IOException {
        String title = Http.readJson(ex).optString("title", "").trim();
        if (title.isEmpty()) {
            title = "Doan chat moi";
        }
        title = trim(title);

        int id = sessionDAO.create(userId, title);
        if (id < 0) {
            Http.sendError(ex, 500, "Khong tao duoc doan chat");
            return;
        }
        Http.sendJson(ex, 201, new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("messageCount", 0));
    }

    private void rename(HttpExchange ex, int userId, int sessionId) throws IOException {
        String title = Http.readJson(ex).optString("title", "").trim();
        if (title.isEmpty()) {
            Http.sendError(ex, 400, "Ten doan chat khong duoc de trong");
            return;
        }
        title = trim(title);

        if (!sessionDAO.rename(sessionId, userId, title)) {
            Http.sendError(ex, 500, "Doi ten that bai");
            return;
        }
        Http.sendJson(ex, 200, new JSONObject().put("id", sessionId).put("title", title));
    }

    private void delete(HttpExchange ex, int userId, int sessionId) throws IOException {
        if (!sessionDAO.delete(sessionId, userId)) {
            Http.sendError(ex, 500, "Xoa that bai");
            return;
        }
        ChatCache.invalidateHistory(sessionId);
        Http.sendJson(ex, 200, new JSONObject().put("ok", true));
    }

    private String trim(String title) {
        return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
    }
}
