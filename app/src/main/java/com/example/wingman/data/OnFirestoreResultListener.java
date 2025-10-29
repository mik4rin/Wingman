package com.example.wingman.data;

public interface OnFirestoreResultListener {
    void onSuccess(String id);
    void onError(Exception e);
}
