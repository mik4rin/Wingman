package com.example.wingman.data;

public class Note {
    private String id;         // Firestore document ID
    private String title;
    private String contents;   // keep same name as old Room for compatibility
    private long timestamp;
    private String userId;     // Firebase UID

    private String mainColor;   // hex string like "#FF0000"
    private String accentColor; // hex string like "#00FF00"
    private boolean pinned;

    // Default constructor (needed by Firestore)
    public Note() {}

    // Full constructor
    public Note(String id, String title, String contents,
                long timestamp, String userId,
                String mainColor, String accentColor, boolean pinned) {
        this.id = id;
        this.title = title;
        this.contents = contents;
        this.timestamp = timestamp;
        this.userId = userId;
        this.mainColor = mainColor;
        this.accentColor = accentColor;
        this.pinned = pinned;
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
}