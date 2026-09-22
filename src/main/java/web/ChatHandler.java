package web;

import cache.ChatCache;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dao.MessageDAO;
import dao.SessionDAO;
import java.io.IOException;
import java.util.List;
import service.FileStorage;
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

    /** So ky tu toi da doc tu file van ban de gui kem cau hoi. */
    private static final int MAX_TEXT_FILE_CHARS = 30000;

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
            JSONObject o = new JSONObject()
                    .put("role", m.getRole())
                    .put("content", m.getContent())
                    .put("createdAt", String.valueOf(m.getCreatedAt()));
            if (m.hasAttachment()) {
                o.put("attachment", new JSONObject()
                        .put("name", m.getAttachmentName())
                        .put("path", "/" + m.getAttachmentPath())
                        .put("type", m.getAttachmentType()));
            }
            arr.put(o);
        }
        Http.sendJson(ex, 200, new JSONObject().put("messages", arr));
    }

    private void chat(HttpExchange ex, int userId) throws Exception {
        if (!Http.requireMethod(ex, "POST")) {
            return;
        }

        String prompt;
        int sessionId;
        Multipart.Part uploaded = null;

        // Form co kem file gui duoi dang multipart, tin nhan thuong gui JSON
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (Multipart.isMultipart(contentType)) {
            Multipart.Form form = Multipart.parse(ex.getRequestBody(), contentType);
            prompt = form.field("message", "").trim();
            try {
                sessionId = Integer.parseInt(form.field("sessionId", "-1"));
            } catch (NumberFormatException e) {
                sessionId = -1;
            }
            uploaded = form.firstFile();
        } else {
            JSONObject body = Http.readJson(ex);
            prompt = body.optString("message", "").trim();
            sessionId = body.optInt("sessionId", -1);
        }

        if (prompt.isEmpty() && uploaded == null) {
            Http.sendError(ex, 400, "Tin nhan khong duoc de trong");
            return;
        }

        // Gui moi file khong kem chu -> dat cau hoi mac dinh
        if (prompt.isEmpty()) {
            prompt = "Hay mo ta va phan tich noi dung file nay.";
        }

        // Chan spam truoc khi lam bat cu viec gi ton kem
        if (!ChatCache.allowRequest(userId)) {
            Http.sendError(ex, 429, "Ban gui qua nhanh. Vui long cho mot phut roi thu lai "
                    + "(gioi han " + ChatCache.rateLimit() + " tin nhan moi phut).");
            return;
        }

        // Luu file truoc, de neu file hong thi khong tao doan chat thua
        FileStorage.Stored stored = null;
        String fileAsText = null;
        if (uploaded != null) {
            try {
                stored = FileStorage.save(uploaded.fileName(), uploaded.data());
                // File van ban thi doc noi dung gui kem cau hoi luon
                if (!stored.isBinaryForGemini()) {
                    fileAsText = new String(uploaded.data(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (fileAsText.length() > MAX_TEXT_FILE_CHARS) {
                        fileAsText = fileAsText.substring(0, MAX_TEXT_FILE_CHARS)
                                + "\n... (da cat bot vi file qua dai)";
                    }
                }
            } catch (Exception e) {
                Http.sendError(ex, 400, e.getMessage());
                return;
            }
        }

        // Chua co doan chat nao -> tu tao moi, lay cau hoi dau lam tieu de
        boolean isNewSession = false;
        if (sessionId <= 0) {
            sessionId = sessionDAO.create(userId, autoTitle(prompt));
            if (sessionId < 0) {
                if (stored != null) {
                    FileStorage.delete(stored.storedName());
                }
                Http.sendError(ex, 500, "Khong tao duoc doan chat");
                return;
            }
            isNewSession = true;
        } else if (!sessionDAO.belongsTo(sessionId, userId)) {
            if (stored != null) {
                FileStorage.delete(stored.storedName());
            }
            Http.sendError(ex, 404, "Khong tim thay doan chat");
            return;
        }

        // Lay ngu canh truoc khi luu cau hoi moi, de khong lap lai chinh no
        List<Message> history = messageDAO.getHistory(sessionId);
        if (history.size() > CONTEXT_SIZE) {
            history = history.subList(history.size() - CONTEXT_SIZE, history.size());
        }

        if (stored != null) {
            messageDAO.saveMessage(userId, sessionId, "user", prompt,
                    stored.originalName(), stored.path(), stored.mimeType());
        } else {
            messageDAO.saveMessage(userId, sessionId, "user", prompt);
        }

        // Cau hoi gui cho Gemini: neu la file van ban thi ghep noi dung vao
        String promptForGemini = prompt;
        if (fileAsText != null) {
            promptForGemini = prompt + "\n\n--- Noi dung file "
                    + stored.originalName() + " ---\n" + fileAsText;
        }

        // Chi dung lai cau tra loi cu khi la cau hoi dau va khong kem file
        String reply = null;
        boolean cacheable = history.isEmpty() && stored == null;
        if (cacheable) {
            reply = ChatCache.getReply(prompt, gemini.getModel());
        }

        boolean fromCache = reply != null;
        if (!fromCache) {
            GeminiService.Attachment attachment = null;
            if (stored != null && stored.isBinaryForGemini()) {
                attachment = new GeminiService.Attachment(
                        stored.mimeType(), uploaded.data());
            }
            reply = gemini.askGemini(promptForGemini, history, attachment);
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

        if (stored != null) {
            result.put("attachment", new JSONObject()
                    .put("name", stored.originalName())
                    .put("path", "/" + stored.path())
                    .put("type", stored.mimeType()));
        }
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
