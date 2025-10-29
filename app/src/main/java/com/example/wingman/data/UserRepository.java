package com.example.wingman.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CollectionReference usersRef;

    public UserRepository() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        usersRef = db.collection("users");
    }

    public void registerUser(User user, OnCompleteListener<AuthResult> listener) {
        auth.createUserWithEmailAndPassword(user.getEmail(), user.getPassword())
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser fUser = auth.getCurrentUser();
                        if (fUser != null) {
                            String uid = fUser.getUid();
                            user.setId(uid);
                            usersRef.document(uid).set(user)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "User saved in Firestore"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Error saving user", e));
                        }
                    }
                    listener.onComplete(task);
                });
    }

    public void loginUserWithEmail(String email, String password, OnCompleteListener<AuthResult> listener) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(listener);
    }

    public void loginUserWithUsername(String username, String password, OnCompleteListener<AuthResult> listener) {
        usersRef.whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        User u = doc.toObject(User.class);
                        if (u != null && u.getEmail() != null) {
                            loginUserWithEmail(u.getEmail(), password, listener);
                        } else {
                            Log.e(TAG, "User document missing email");
                        }
                    } else {
                        Log.e(TAG, "Username not found: " + username);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error finding username", e));
    }

    public void resetPassword(String email, OnCompleteListener<Void> listener) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener(listener);
    }

    public void updatePasswordByUsername(String username, String newPassword, OnFirestoreResultListener listener) {
        usersRef.whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (!qs.isEmpty()) {
                        String docId = qs.getDocuments().get(0).getId();

                        FirebaseUser current = auth.getCurrentUser();
                        if (current != null) {
                            usersRef.document(docId).get().addOnSuccessListener(doc -> {
                                User u = doc.toObject(User.class);
                                if (u != null && u.getEmail() != null && u.getEmail().equals(current.getEmail())) {
                                    current.updatePassword(newPassword)
                                            .addOnSuccessListener(unused ->
                                                    Log.d(TAG, "FirebaseAuth password updated"))
                                            .addOnFailureListener(e ->
                                                    Log.e(TAG, "Error updating Auth password", e));
                                }
                            });
                        }

                        usersRef.document(docId).update("password", newPassword)
                                .addOnSuccessListener(unused -> listener.onSuccess(docId))
                                .addOnFailureListener(listener::onError);
                    } else {
                        listener.onError(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    public void deleteUserById(String uid, OnFirestoreResultListener listener) {
        usersRef.document(uid).delete()
                .addOnSuccessListener(unused -> {
                    FirebaseUser current = auth.getCurrentUser();
                    if (current != null && current.getUid().equals(uid)) {
                        current.delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "FirebaseAuth user deleted"))
                                .addOnFailureListener(e -> Log.e(TAG, "Error deleting FirebaseAuth user", e));
                    }
                    listener.onSuccess(uid);
                })
                .addOnFailureListener(listener::onError);
    }

    public ListenerRegistration listenUserById(String uid, EventListener<DocumentSnapshot> listener) {
        return usersRef.document(uid).addSnapshotListener(listener);
    }

    public ListenerRegistration listenUserByUsername(String username, EventListener<com.google.firebase.firestore.QuerySnapshot> listener) {
        return usersRef.whereEqualTo("username", username).addSnapshotListener(listener);
    }

    public interface OnFirestoreResultListener {
        void onSuccess(String id);
        void onError(Exception e);
    }

    public interface OnFirestoreUserListener {
        void onSuccess(User user);
        void onError(Exception e);
    }

    public interface OnFirestoreCountListener {
        void onResult(int count);
        void onError(Exception e);
    }

    public interface OnFirestoreBooleanListener {
        void onResult(boolean value);
        void onError(Exception e);
    }

    public interface OnFirestoreStringListener {
        void onResult(String value);
        void onError(Exception e);
    }

    public interface OnFirestoreUserWithNotesListener {
        void onSuccess(UserWithNotes result);
        void onError(Exception e);
    }
}