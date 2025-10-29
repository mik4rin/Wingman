package com.example.wingman.data;

import android.os.Parcel;
import android.os.Parcelable;

public class Task implements Parcelable {
    private String id;
    private String title;
    private String description;
    private String course;
    private long deadline;
    private boolean completed;
    private String userId;

    public Task() {}

    public Task(String id, String title, String description, String course, long deadline, boolean completed, String userId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.course = course;
        this.deadline = deadline;
        this.completed = completed;
        this.userId = userId;
    }

    // Parcelable constructor
    protected Task(Parcel in) {
        id = in.readString();
        title = in.readString();
        description = in.readString();
        course = in.readString();
        deadline = in.readLong();
        completed = in.readByte() != 0;
        userId = in.readString();
    }

    public static final Creator<Task> CREATOR = new Creator<Task>() {
        @Override
        public Task createFromParcel(Parcel in) {
            return new Task(in);
        }

        @Override
        public Task[] newArray(int size) {
            return new Task[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(course);
        dest.writeLong(deadline);
        dest.writeByte((byte) (completed ? 1 : 0));
        dest.writeString(userId);
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }

    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }

    public long getDeadlineTimestamp() { return deadline; }
    public void setDeadlineTimestamp(long deadlineTimestamp) { this.deadline = deadlineTimestamp; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}