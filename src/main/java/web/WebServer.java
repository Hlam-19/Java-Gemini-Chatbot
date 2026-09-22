package web;

import com.sun.net.httpserver.HttpServer;
import config.Env;
import dao.DBConnection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Diem khoi dong ung dung web chatbot. */
public class WebServer {

    public static void main(String[] args) throws IOException {

        // Tao bang neu chua co
        DBConnection.initTables();

        int port = Env.getInt("SERVER_PORT", 8080);
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        AuthHandler auth = new AuthHandler();
        ChatHandler chat = new ChatHandler();
        SessionHandler sessions = new SessionHandler();

        server.createContext("/api/register", auth);
        server.createContext("/api/login", auth);
        server.createContext("/api/logout", auth);
        server.createContext("/api/me", auth);
        server.createContext("/api/chat", chat);
        server.createContext("/api/history", chat);
        // Bat ca /api/sessions va /api/sessions/{id}
        server.createContext("/api/sessions", sessions);
        server.createContext("/", new StaticHandler());

        // Dung thread pool vi goi Gemini mat vai giay, khong the chan server
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("=================================================");
        System.out.println("  Chatbot dang chay tai: http://localhost:" + port);
        System.out.println("  Model: " + Env.get("GEMINI_MODEL", "gemini-3.6-flash"));
        System.out.println("  Nhan Ctrl+C de dung.");
        System.out.println("=================================================");
    }
}
