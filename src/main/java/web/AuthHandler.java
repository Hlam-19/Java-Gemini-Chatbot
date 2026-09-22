package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dao.UserDAO;
import java.io.IOException;
import model.User;
import org.json.JSONObject;

/** Xu ly /api/register, /api/login, /api/logout, /api/me */
public class AuthHandler implements HttpHandler {

    private final UserDAO userDAO = new UserDAO();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            switch (path) {
                case "/api/register" -> register(ex);
                case "/api/login"    -> login(ex);
                case "/api/logout"   -> logout(ex);
                case "/api/me"       -> me(ex);
                default -> Http.sendError(ex, 404, "Khong tim thay duong dan");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Http.sendError(ex, 500, "Loi may chu: " + e.getMessage());
        }
    }

    private void register(HttpExchange ex) throws IOException {
        if (!Http.requireMethod(ex, "POST")) {
            return;
        }
        JSONObject body = Http.readJson(ex);
        String username = body.optString("username", "").trim();
        String password = body.optString("password", "");

        if (username.length() < 3) {
            Http.sendError(ex, 400, "Ten dang nhap phai co it nhat 3 ky tu");
            return;
        }
        if (password.length() < 6) {
            Http.sendError(ex, 400, "Mat khau phai co it nhat 6 ky tu");
            return;
        }
        if (userDAO.existsByUsername(username)) {
            Http.sendError(ex, 409, "Ten dang nhap da ton tai");
            return;
        }
        if (!userDAO.register(username, password)) {
            Http.sendError(ex, 500, "Dang ky that bai");
            return;
        }

        User user = userDAO.login(username, password);
        startSession(ex, user);
    }

    private void login(HttpExchange ex) throws IOException {
        if (!Http.requireMethod(ex, "POST")) {
            return;
        }
        JSONObject body = Http.readJson(ex);
        String username = body.optString("username", "").trim();
        String password = body.optString("password", "");

        User user = userDAO.login(username, password);
        if (user == null) {
            Http.sendError(ex, 401, "Sai ten dang nhap hoac mat khau");
            return;
        }
        startSession(ex, user);
    }

    private void logout(HttpExchange ex) throws IOException {
        SessionManager.remove(Http.getCookie(ex, "SESSION"));
        ex.getResponseHeaders().add("Set-Cookie",
                "SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
        Http.sendJson(ex, 200, new JSONObject().put("ok", true));
    }

    private void me(HttpExchange ex) throws IOException {
        Integer userId = Http.currentUserId(ex);
        if (userId == null) {
            Http.sendError(ex, 401, "Chua dang nhap");
            return;
        }
        User user = userDAO.findById(userId);
        if (user == null) {
            Http.sendError(ex, 401, "Phien khong hop le");
            return;
        }
        Http.sendJson(ex, 200, new JSONObject()
                .put("userId", userId)
                .put("username", user.getUsername()));
    }

    /** Tao phien moi va gui cookie ve trinh duyet. */
    private void startSession(HttpExchange ex, User user) throws IOException {
        String token = SessionManager.create(user.getId());
        ex.getResponseHeaders().add("Set-Cookie",
                "SESSION=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        Http.sendJson(ex, 200, new JSONObject()
                .put("userId", user.getId())
                .put("username", user.getUsername()));
    }
}
