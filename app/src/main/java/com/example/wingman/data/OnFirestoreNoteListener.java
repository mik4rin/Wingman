package com.example.wingman.data;

public interface OnFirestoreNoteListener {
    void onSuccess(Note note);
    void onError(Exception e);
}
