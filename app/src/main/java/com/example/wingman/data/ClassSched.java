package com.example.wingman.data;

import java.util.HashMap;
import java.util.Map;

public class ClassSched {
    private String id;         // Firestore document ID
    private String userId;     // Firebase UID
    private String title;
    private int startHour;
    private int startMinute;
    private int endHour;
    private int endMinute;
    private int dayIndex;
    private String mainColor;
    private String accentColor;
    private boolean alarmEnabled;

    // Required empty constructor
    public ClassSched() {}

    public ClassSched(String userId, String title, int startHour, int startMinute,
                      int endHour, int endMinute, int dayIndex,
                      String mainColor, String accentColor, boolean alarmEnabled) {
        this.userId = userId;
        this.title = title;
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.endHour = endHour;
        this.endMinute = endMinute;
        this.dayIndex = dayIndex;
        this.mainColor = mainColor;
        this.accentColor = accentColor;
        this.alarmEnabled = alarmEnabled;
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getStartMinute() { return startMinute; }
    public void setStartMinute(int startMinute) { this.startMinute = startMinute; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }

    public int getEndMinute() { return endMinute; }
    public void setEndMinute(int endMinute) { this.endMinute = endMinute; }

    public int getDayIndex() { return dayIndex; }
    public void setDayIndex(int dayIndex) { this.dayIndex = dayIndex; }

    public String getMainColor() { return mainColor; }
    public void setMainColor(String mainColor) { this.mainColor = mainColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public boolean isAlarmEnabled() { return alarmEnabled; }
    public void setAlarmEnabled(boolean alarmEnabled) { this.alarmEnabled = alarmEnabled; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("title", title);
        map.put("startHour", startHour);
        map.put("startMinute", startMinute);
        map.put("endHour", endHour);
        map.put("endMinute", endMinute);
        map.put("dayIndex", dayIndex);
        map.put("mainColor", mainColor);
        map.put("accentColor", accentColor);
        map.put("alarmEnabled", alarmEnabled);
        return map;
    }
}