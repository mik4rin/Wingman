package com.example.wingman.data;

import java.util.List;

public interface OnFirestoreTasksListener {
    void onSuccess(List<Task> tasks);
    void onError(Exception e);
}