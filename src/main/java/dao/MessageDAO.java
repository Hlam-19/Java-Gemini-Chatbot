package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import model.Message;

public class MessageDAO {

    /** Luu mot tin nhan vao doan chat. */
    public boolean saveMessage(int userId, int sessionId, String role, String content) {
        String sql = "INSERT INTO messages (user_id, session_id, role, content) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, sessionId);
            stmt.setString(3, role);
            stmt.setString(4, content);
            stmt.executeUpdate();
            return true;

        } catch (Exception e) {
            System.err.println("[MessageDAO] Loi luu tin nhan: " + e.getMessage());
            return false;
        }
    }

    /** Lich su cua mot doan chat, theo thu tu thoi gian. */
    public List<Message> getHistory(int sessionId) {
        List<Message> list = new ArrayList<>();
        String sql = """
                SELECT id, user_id, session_id, role, content, created_at
                FROM messages WHERE session_id = ? ORDER BY id ASC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setId(rs.getInt("id"));
                    msg.setUserId(rs.getInt("user_id"));
                    msg.setSessionId(rs.getInt("session_id"));
                    msg.setRole(rs.getString("role"));
                    msg.setContent(rs.getString("content"));
                    msg.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(msg);
                }
            }

        } catch (Exception e) {
            System.err.println("[MessageDAO] Loi lay lich su: " + e.getMessage());
        }
        return list;
    }

    /** Dem so tin nhan trong mot doan chat. */
    public int countBySession(int sessionId) {
        String sql = "SELECT COUNT(*) FROM messages WHERE session_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            System.err.println("[MessageDAO] Loi dem tin nhan: " + e.getMessage());
            return 0;
        }
    }
}
