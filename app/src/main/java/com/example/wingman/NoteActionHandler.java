package com.example.wingman;

public interface NoteActionHandler {
    void onNoteClick(String noteId);
    void onNoteLongClick(int position);
}