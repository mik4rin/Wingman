package com.example.wingman;

import android.os.Parcel;
import android.os.Parcelable;

public class FlashcardItem implements Parcelable {
    private String id;
    private String userId;
    private String setId;
    private String question;
    private String answer;
    private int position;
    private long createdAt;
    private long updatedAt;

    public FlashcardItem() {

    }

    public FlashcardItem(String question, String answer) {
        this.question = question;
        this.answer = answer;
        this.position = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    protected FlashcardItem(Parcel in) {
        id = in.readString();
        userId = in.readString();
        setId = in.readString();
        question = in.readString();
        answer = in.readString();
        position = in.readInt();
        createdAt = in.readLong();
        updatedAt = in.readLong();
    }

    public static final Creator<FlashcardItem> CREATOR = new Creator<FlashcardItem>() {
        @Override
        public FlashcardItem createFromParcel(Parcel in) {
            return new FlashcardItem(in);
        }

        @Override
        public FlashcardItem[] newArray(int size) {
            return new FlashcardItem[size];
        }
    };

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(userId);
        dest.writeString(setId);
        dest.writeString(question);
        dest.writeString(answer);
        dest.writeInt(position);
        dest.writeLong(createdAt);
        dest.writeLong(updatedAt);
    }
}