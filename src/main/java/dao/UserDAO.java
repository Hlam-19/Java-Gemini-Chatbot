package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import model.User;

public class UserDAO {

    /**
     * Dang ky tai khoan moi. Mat khau duoc bam truoc khi luu.
     *
     * @return true neu thanh cong, false neu username da ton tai hoac loi DB
     */
    public boolean register(String username, String plainPassword) {

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, PasswordUtil.hashPassword(plainPassword));
            stmt.executeUpdate();
            return true;

        } catch (Exception e) {
            System.err.println("[UserDAO] Dang ky that bai: " + e.getMessage());
            return false;
        }
    }

    /** Dang nhap. Tra ve User neu dung, null neu sai thong tin. */
    public User login(String username, String plainPassword) {

        String sql = "SELECT * FROM users WHERE username = ? AND password_hash = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, PasswordUtil.hashPassword(plainPassword));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    return user;
                }
            }

        } catch (Exception e) {
            System.err.println("[UserDAO] Dang nhap loi: " + e.getMessage());
        }

        return null;
    }

    /** Tim user theo id - dung khi khoi phuc phien tu cookie. */
    public User findById(int id) {
        String sql = "SELECT id, username, password_hash FROM users WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    return user;
                }
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] Loi tim user: " + e.getMessage());
        }
        return null;
    }

    /** Kiem tra username da duoc su dung chua. */
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[UserDAO] Loi kiem tra username: " + e.getMessage());
            return false;
        }
    }
}
