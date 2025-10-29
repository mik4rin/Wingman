package com.example.wingman.data;

import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Sched {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String date;
    private String schedType;
    private long timestamp;

    public Sched() {}

    public Sched(String id, String userId, String title, String description, String date, String schedType, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.date = date;
        this.schedType = schedType;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getSchedType() { return schedType; }
    public void setSchedType(String schedType) { this.schedType = schedType; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}