package dao;

import model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;

public class UserDAO {
public static String hashPassword(String password) {

    try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        byte[] hash = md.digest(password.getBytes());

        StringBuilder hexString = new StringBuilder();

        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);

            if (hex.length() == 1) {
                hexString.append('0');
            }

            hexString.append(hex);
        }

        return hexString.toString();

    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
public boolean register(String username, String passwordHash) {

    String sql = """
        INSERT INTO USERS (username, password_hash)
        VALUES (?, ?)
    """;

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, username);
        stmt.setString(2, passwordHash);

        stmt.executeUpdate();

        return true;

    } catch (Exception e) {
        e.printStackTrace();
        return false;
    }
}
public User login(String username, String passwordHash) {

    String sql = """
        SELECT * FROM USERS
        WHERE username = ? AND password_hash = ?
    """;

    try (Connection conn = DBConnection.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, username);
        stmt.setString(2, passwordHash);

        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {

            User user = new User();

            user.setId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));

            return user;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return null;
}
}