package com.example.wingman.data;

import java.util.List;

public interface OnFirestoreNotesListener {
    void onSuccess(List<Note> notes);
    void onError(Exception e);
}
