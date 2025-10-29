package com.example.wingman.data;

import java.util.Locale;

public class Notification {
    private String id;
    private String userId;
    private String taskId;
    private String type;
    private String title;
    private String message;
    private String time;
    private boolean isRead;
    private long timestamp;

    public Notification() {}

    public Notification(String userId, String taskId, String type, String title,
                        String message, String time, boolean isRead) {
        this.userId = userId;
        this.taskId = taskId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.time = time;
        this.isRead = isRead;
        this.timestamp = System.currentTimeMillis();
    }

    public Notification(String userId, String taskId, String type, String title,
                        String message, String time, long timestamp, boolean isRead) {
        this.userId = userId;
        this.taskId = taskId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.time = time;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", taskId='" + taskId + '\'' +
                ", type='" + type + '\'' +
                ", title='" + title + '\'' +
                ", message='" + message + '\'' +
                ", time='" + time + '\'' +
                ", isRead=" + isRead +
                ", timestamp=" + timestamp +
                '}';
    }
}