package com.example.wingman.data;

import java.util.List;

public interface OnFirestoreFlashcardsListener<T> {
    void onSuccess(List<T> data);
    void onError(Exception e);
}