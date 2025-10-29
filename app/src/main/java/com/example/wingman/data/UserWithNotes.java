package com.example.wingman.data;

import java.util.List;

public class UserWithNotes {

    private User user;
    private List<Note> notes;

    public UserWithNotes() {
        // Default constructor
    }

    public UserWithNotes(User user, List<Note> notes) {
        this.user = user;
        this.notes = notes;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }
}