package com.example.wingman.data;

import java.util.List;

public interface OnFirestoreListListener<T> {
    void onSuccess(List<T> items);
    void onError(Exception e);
}
