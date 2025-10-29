package com.example.wingman.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskRepository {
    private static final String TAG = "TaskRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CollectionReference tasksRef;

    public TaskRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        tasksRef = db.collection("tasks");
    }

    private String currentUidOrNull() {
        if (auth.getCurrentUser() == null) return null;
        return auth.getCurrentUser().getUid();
    }

    public void addTask(Task task, OnFirestoreResultListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return;
        }
        String taskId = tasksRef.document().getId();
        task.setId(taskId);
        task.setUserId(uid);
        tasksRef.document(taskId).set(task)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onSuccess(taskId);
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e);
                });
    }

    public void updateTask(Task task, OnFirestoreResultListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return;
        }
        task.setUserId(uid);
        tasksRef.document(task.getId()).set(task)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onSuccess(task.getId());
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e);
                });
    }

    public void updateTaskPartial(String taskId, Map<String, Object> updates, OnFirestoreResultListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return;
        }
        tasksRef.document(taskId).update(updates)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onSuccess(taskId);
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e);
                });
    }

    public void deleteTask(String taskId, OnFirestoreResultListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return;
        }
        tasksRef.document(taskId).delete()
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onSuccess(taskId);
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e);
                });
    }

    public void getTasks(OnFirestoreTasksListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            listener.onError(new Exception("User not signed in"));
            return;
        }
        tasksRef.whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Task> tasks = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Task task = doc.toObject(Task.class);
                        task.setId(doc.getId()); // ensure ID retained
                        tasks.add(task);
                    }
                    listener.onSuccess(tasks);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching tasks", e);
                    listener.onError(e);
                });
    }

    public void getAllTasks(OnFirestoreTasksListener listener) {
        tasksRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Task> tasks = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Task task = doc.toObject(Task.class);
                        task.setId(doc.getId());
                        tasks.add(task);
                    }
                    listener.onSuccess(tasks);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching all tasks", e);
                    listener.onError(e);
                });
    }

    public void getTaskById(String taskId, OnFirestoreObjectListener<Task> listener) {
        tasksRef.document(taskId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Task task = doc.toObject(Task.class);
                        if (task != null) {
                            task.setId(doc.getId());
                        }
                        listener.onSuccess(task);
                    } else {
                        listener.onError(new Exception("Task not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    listener.onError(e);
                });
    }

    public void markTaskCompleted(String taskId, boolean completed, OnFirestoreResultListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return;
        }
        tasksRef.document(taskId).update("completed", completed)
                .addOnSuccessListener(unused -> {
                    if (listener != null) listener.onSuccess(taskId);
                })
                .addOnFailureListener(e -> {
                    if (listener != null) listener.onError(e);
                });
    }

    public void searchTasks(String query, OnFirestoreTasksListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            listener.onError(new Exception("User not signed in"));
            return;
        }
        tasksRef.whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Task> tasks = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Task task = doc.toObject(Task.class);
                        task.setId(doc.getId());
                        if ((task.getTitle() != null && task.getTitle().toLowerCase().contains(query.toLowerCase())) ||
                                (task.getDescription() != null && task.getDescription().toLowerCase().contains(query.toLowerCase()))) {
                            tasks.add(task);
                        }
                    }
                    listener.onSuccess(tasks);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error searching tasks", e);
                    listener.onError(e);
                });
    }

    public ListenerRegistration listenToTasks(OnFirestoreTasksListener listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            listener.onError(new Exception("User not signed in"));
            return null;
        }
        return tasksRef.whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        listener.onError(e);
                        return;
                    }

                    if (snapshots != null) {
                        List<Task> taskList = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Task task = doc.toObject(Task.class);
                            task.setId(doc.getId());
                            taskList.add(task);
                        }
                        listener.onSuccess(taskList);
                    }
                });
    }
}