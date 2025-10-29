package com.example.wingman.data;

import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationRepository {
    private static final String TAG = "NotificationRepo";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CollectionReference notifRef;

    public NotificationRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        notifRef = db.collection("notifications");
    }

    private String currentUidOrNull() {
        if (auth.getCurrentUser() == null) return null;
        return auth.getCurrentUser().getUid();
    }

    public void insert(Notification notif, OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) {
                listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (notif.getTimestamp() <= 0L) {
            notif.setTimestamp(now);
        }
        if (notif.getTime() == null || notif.getTime().trim().isEmpty()) {
            String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(notif.getTimestamp()));
            notif.setTime(formatted);
        }

        final String userIdFinal = uid;
        final Notification notifFinal = notif;

        if (notifFinal.getTaskId() == null || notifFinal.getTaskId().trim().isEmpty()) {
            String id = notifRef.document().getId();
            notifFinal.setId(id);
            notifFinal.setUserId(userIdFinal);
            notifRef.document(id)
                    .set(notifFinal)
                    .addOnCompleteListener(listener)
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Insert failed", e);
                        if (listener != null)
                            listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                    });
            return;
        }

        Query q = notifRef
                .whereEqualTo("userId", userIdFinal)
                .whereEqualTo("taskId", notifFinal.getTaskId())
                .whereEqualTo("type", notifFinal.getType())
                .limit(1);

        q.get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        DocumentSnapshot doc = snapshot.getDocuments().get(0);
                        String existingId = doc.getId();
                        notifFinal.setId(existingId);
                        notifFinal.setUserId(userIdFinal);
                        notifRef.document(existingId)
                                .set(notifFinal)
                                .addOnCompleteListener(listener)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed updating existing notification, falling back to add", e);
                                    notifFinal.setUserId(userIdFinal);
                                    String id = notifRef.document().getId();
                                    notifFinal.setId(id);
                                    notifRef.document(id)
                                            .set(notifFinal)
                                            .addOnCompleteListener(listener)
                                            .addOnFailureListener(e2 -> {
                                                Log.e(TAG, "Fallback add failed", e2);
                                                if (listener != null)
                                                    listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e2));
                                            });
                                });
                    } else {
                        String id = notifRef.document().getId();
                        notifFinal.setId(id);
                        notifFinal.setUserId(userIdFinal);
                        notifRef.document(id)
                                .set(notifFinal)
                                .addOnCompleteListener(listener)
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Insert failed", e);
                                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Query failed when attempting upsert; falling back to insert", e);
                    notifFinal.setUserId(userIdFinal);
                    String id = notifRef.document().getId();
                    notifFinal.setId(id);
                    notifRef.document(id)
                            .set(notifFinal)
                            .addOnCompleteListener(listener)
                            .addOnFailureListener(e2 -> {
                                Log.e(TAG, "Fallback insert failed", e2);
                                if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e2));
                            });
                });
    }

    public void delete(String notifId, OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.document(notifId).delete().addOnCompleteListener(listener);
    }

    public void update(Notification notif, OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.document(notif.getId()).set(notif).addOnCompleteListener(listener);
    }

    public void updatePartial(String notifId, java.util.Map<String, Object> updates, OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.document(notifId).update(updates).addOnCompleteListener(listener);
    }

    public void clearForUser(OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        notifRef.document(doc.getId()).delete();
                    }
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(null));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error clearing notifications", e);
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                });
    }

    public void getNotifications(OnCompleteListener<List<Notification>> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }

        notifRef.whereEqualTo("userId", uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Notification notif = doc.toObject(Notification.class);
                        if (notif != null) {
                            notif.setId(doc.getId());
                        }
                        list.add(notif);
                    }
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(list));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching notifications", e);
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                });
    }

    public void markAsRead(String notifId, OnCompleteListener<Void> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.document(notifId).update("isRead", true).addOnCompleteListener(listener);
    }

    public void hasNotificationForTask(String taskId, String type, OnCompleteListener<Boolean> listener) {
        String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User not signed in")));
            return;
        }
        notifRef.whereEqualTo("userId", uid)
                .whereEqualTo("taskId", taskId)
                .whereEqualTo("type", type)
                .get()
                .addOnSuccessListener(snapshot -> {
                    boolean exists = !snapshot.isEmpty();
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(exists));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking notifications", e);
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                });
    }

    public com.google.firebase.firestore.ListenerRegistration listenToNotificationsRealtime(final OnNotificationsChanged listener) {
        final String uid = currentUidOrNull();
        if (uid == null) {
            if (listener != null) listener.onError(new Exception("User not signed in"));
            return null;
        }

        Query q = notifRef.whereEqualTo("userId", uid)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING);

        return q.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "Notifications realtime listener error", e);
                if (listener != null) listener.onError(e);
                return;
            }
            if (snapshot == null) {
                if (listener != null) listener.onChanged(new ArrayList<>());
                return;
            }

            List<Notification> list = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                try {
                    Notification notif = doc.toObject(Notification.class);
                    if (notif != null) {
                        notif.setId(doc.getId());
                    } else {
                        notif = new Notification();
                        notif.setId(doc.getId());
                    }
                    list.add(notif);
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to parse notification doc " + doc.getId(), ex);
                }
            }
            if (listener != null) listener.onChanged(list);
        });
    }

    public void markNotificationsReadForUserTaskType(String userId, String taskId, String type, OnCompleteListener<Void> listener) {
        if (userId == null || userId.trim().isEmpty()) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("User id missing")));
            return;
        }
        if (taskId == null || taskId.trim().isEmpty()) {
            if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(new Exception("Task id missing")));
            return;
        }
        if (type == null) type = "";

        notifRef
                .whereEqualTo("userId", userId)
                .whereEqualTo("taskId", taskId)
                .whereEqualTo("type", type)
                .whereEqualTo("isRead", false)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(null));
                        return;
                    }

                    WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        DocumentReference ref = notifRef.document(doc.getId());
                        batch.update(ref, "isRead", true);
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forResult(null));
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed marking notifications read in batch", e);
                                if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed querying notifications for mark-read", e);
                    if (listener != null) listener.onComplete(com.google.android.gms.tasks.Tasks.forException(e));
                });
    }

    public void markNotificationDocAsRead(String notifId, OnCompleteListener<Void> listener) {
        markAsRead(notifId, listener);
    }
    public interface OnNotificationsChanged {
        void onChanged(List<Notification> notifications);
        void onError(Exception e);
    }
}