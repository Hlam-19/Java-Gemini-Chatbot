package dao;

import model.Message;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    public boolean saveMessage(int userId, String role, String content) {
        String sql = "INSERT INTO MESSAGES (user_id, role, content) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setString(2, role);
            stmt.setString(3, content);
            stmt.executeUpdate();
            return true;

        } catch (SQLException e) {
            System.out.println("Lỗi lưu tin nhắn: " + e.getMessage());
            return false;
        }
    }

    public List<Message> getHistory(int userId) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT * FROM MESSAGES WHERE user_id = ? ORDER BY created_at ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Message msg = new Message();
                    msg.setId(rs.getInt("id"));
                    msg.setUserId(rs.getInt("user_id"));
                    msg.setRole(rs.getString("role"));
                    msg.setContent(rs.getString("content"));
                    msg.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(msg);
                }
            }

        } catch (SQLException e) {
            System.out.println("Lỗi lấy lịch sử: " + e.getMessage());
        }
        return list;
    }
}