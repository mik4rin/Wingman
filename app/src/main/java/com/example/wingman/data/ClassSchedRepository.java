package com.example.wingman.data;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassSchedRepository {
    private static final String TAG = "ClassSchedRepo";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CollectionReference schedRef;

    public ClassSchedRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        schedRef = db.collection("class_schedules");
    }

    public void insert(ClassSched sched, OnCompleteListener<Void> listener) {
        String id = schedRef.document().getId();
        sched.setId(id);
        sched.setUserId(auth.getCurrentUser().getUid());
        schedRef.document(id).set(sched).addOnCompleteListener(listener);
    }

    public void update(ClassSched sched, OnCompleteListener<Void> listener) {
        if (sched.getId() == null) {
            Log.e(TAG, "Cannot update ClassSched: ID is null");
            return;
        }

        schedRef.document(sched.getId()).update(sched.toMap())
                .addOnCompleteListener(listener);
    }

    public void delete(String schedId, OnCompleteListener<Void> listener) {
        schedRef.document(schedId).delete().addOnCompleteListener(listener);
    }

    public void getAllForUser(OnCompleteListener<List<ClassSched>> listener) {
        schedRef.whereEqualTo("userId", auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ClassSched> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        list.add(doc.toObject(ClassSched.class));
                    }
                    listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(list));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching schedules", e));
    }
}