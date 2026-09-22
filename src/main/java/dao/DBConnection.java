package dao;

import config.Env;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {

    private static final String URL = Env.get("DB_URL",
            "jdbc:mysql://localhost:3306/chatbot_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
    private static final String USER = Env.get("DB_USER", "root");
    private static final String PASSWORD = Env.get("DB_PASSWORD", "");

    // Ket noi database
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Tao bang USERS
            String sqlUsers = """
                CREATE TABLE IF NOT EXISTS users (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(50) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            stmt.executeUpdate(sqlUsers);

            // Tao bang SESSIONS (moi session la mot doan hoi thoai rieng)
            String sqlSessions = """
                CREATE TABLE IF NOT EXISTS sessions (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    title VARCHAR(255) NOT NULL DEFAULT 'Doan chat moi',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id)
                        REFERENCES users(id) ON DELETE CASCADE,
                    INDEX idx_sessions_user (user_id, updated_at DESC)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            stmt.executeUpdate(sqlSessions);

            // Tao bang MESSAGES
            String sqlMessages = """
                CREATE TABLE IF NOT EXISTS messages (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id INT NOT NULL,
                    session_id INT NOT NULL,
                    role VARCHAR(10) NOT NULL,
                    content TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_messages_user FOREIGN KEY (user_id)
                        REFERENCES users(id) ON DELETE CASCADE,
                    CONSTRAINT fk_messages_session FOREIGN KEY (session_id)
                        REFERENCES sessions(id) ON DELETE CASCADE,
                    INDEX idx_messages_session (session_id, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """;
            stmt.executeUpdate(sqlMessages);

            System.out.println("[DB] Da ket noi MySQL va khoi tao bang users, sessions, messages.");

        } catch (SQLException e) {
            System.err.println("[DB] Loi khoi tao bang: " + e.getMessage());
            System.err.println("[DB] Kiem tra MySQL da chay chua va DB_URL trong .env co dung khong.");
            e.printStackTrace();
        }
    }
}
