package com.example.wingman.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Note implements Serializable {

    private String id;
    private String title;
    private String contents;
    private long timestamp;
    private String userId;

    private String mainColor;
    private String accentColor;
    private boolean pinned;
    private List<String> sharedWith = new ArrayList<>();

    // Default constructor (needed by Firestore)
    public Note() {}

    // Full constructor
    public Note(String id, String title, String contents,
                long timestamp, String userId,
                String mainColor, String accentColor, boolean pinned,
                List<String> sharedWith) {
        this.id = id;
        this.title = title;
        this.contents = contents;
        this.timestamp = timestamp;
        this.userId = userId;
        this.mainColor = mainColor;
        this.accentColor = accentColor;
        this.pinned = pinned;
        this.sharedWith = sharedWith != null ? sharedWith : new ArrayList<>();
    }

    // for sharing list
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();

        map.put("title", title);
        map.put("contents", contents);
        map.put("timestamp", timestamp);
        map.put("mainColor", mainColor);
        map.put("accentColor", accentColor);
        map.put("pinned", pinned);
        map.put("sharedWith", sharedWith);

        if (userId != null) {
            map.put("userId", userId);
        }

        return map;
    }

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContents() { return contents; }
    public void setContents(String contents) { this.contents = contents; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMainColor() { return mainColor; }
    public void setMainColor(String mainColor) { this.mainColor = mainColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public List<String> getSharedWith() { return sharedWith; }
    public void setSharedWith(List<String> sharedWith) { this.sharedWith = sharedWith; }
}