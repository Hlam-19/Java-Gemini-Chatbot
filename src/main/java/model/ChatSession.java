package model;

import java.sql.Timestamp;

/** Mot doan hoi thoai rieng biet cua nguoi dung. */
public class ChatSession {

    private int id;
    private int userId;
    private String title;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ChatSession() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
