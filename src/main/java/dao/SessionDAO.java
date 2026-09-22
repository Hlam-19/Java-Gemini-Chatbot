package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import model.ChatSession;

/** Truy cap bang sessions - moi doan hoi thoai cua nguoi dung. */
public class SessionDAO {

    /** Tao doan chat moi, tra ve id vua tao (-1 neu loi). */
    public int create(int userId, String title) {
        String sql = "INSERT INTO sessions (user_id, title) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, userId);
            stmt.setString(2, title);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi tao doan chat: " + e.getMessage());
        }
        return -1;
    }

    /** Danh sach doan chat cua user, moi nhat len dau. */
    public List<ChatSession> listByUser(int userId) {
        List<ChatSession> list = new ArrayList<>();
        String sql = """
                SELECT id, user_id, title, created_at, updated_at
                FROM sessions WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi lay danh sach: " + e.getMessage());
        }
        return list;
    }

    /** Kiem tra doan chat co thuoc ve user nay khong - chong truy cap trai phep. */
    public boolean belongsTo(int sessionId, int userId) {
        String sql = "SELECT 1 FROM sessions WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            stmt.setInt(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi kiem tra quyen: " + e.getMessage());
            return false;
        }
    }

    /** Doi ten doan chat. */
    public boolean rename(int sessionId, int userId, String title) {
        String sql = "UPDATE sessions SET title = ? WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, title);
            stmt.setInt(2, sessionId);
            stmt.setInt(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi doi ten: " + e.getMessage());
            return false;
        }
    }

    /** Xoa doan chat - tin nhan ben trong tu xoa theo (ON DELETE CASCADE). */
    public boolean delete(int sessionId, int userId) {
        String sql = "DELETE FROM sessions WHERE id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi xoa: " + e.getMessage());
            return false;
        }
    }

    /** Danh dau doan chat vua co hoat dong, de no nhay len dau danh sach. */
    public void touch(int sessionId) {
        String sql = "UPDATE sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, sessionId);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[SessionDAO] Loi cap nhat thoi gian: " + e.getMessage());
        }
    }

    private ChatSession map(ResultSet rs) throws Exception {
        ChatSession s = new ChatSession();
        s.setId(rs.getInt("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setTitle(rs.getString("title"));
        s.setCreatedAt(rs.getTimestamp("created_at"));
        s.setUpdatedAt(rs.getTimestamp("updated_at"));
        return s;
    }
}
