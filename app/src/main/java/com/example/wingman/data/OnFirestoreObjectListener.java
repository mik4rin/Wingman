package com.example.wingman.data;

public interface OnFirestoreObjectListener<T> {
    void onSuccess(T item);
    void onError(Exception e);
}