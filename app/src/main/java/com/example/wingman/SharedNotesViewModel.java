package com.example.wingman;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SharedNotesViewModel extends ViewModel {

    private final MutableLiveData<String> noteTitle = new MutableLiveData<>();
    private final MutableLiveData<String> noteContentHtml = new MutableLiveData<>();
    private final MutableLiveData<String> currentNoteId = new MutableLiveData<>();

    public void setNoteTitle(String title) {
        noteTitle.setValue(title);
    }

    public LiveData<String> getNoteTitle() {
        return noteTitle;
    }

    public void setNoteContentHtml(String html) {
        noteContentHtml.setValue(html);
    }

    public LiveData<String> getNoteContentHtml() {
        return noteContentHtml;
    }

    public void setCurrentNoteId(String id) {
        currentNoteId.setValue(id);
    }
    public LiveData<String> getCurrentNoteId() {
        return currentNoteId;
    }
}
