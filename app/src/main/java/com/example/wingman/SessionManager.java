package com.example.wingman;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.example.wingman.data.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SessionManager {
    private static final String PREF_NAME = "WingmanPrefs";
    private static final String KEY_USER_ID = "user_id";

    public static void saveUserId(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_USER_ID, null);
        if (saved != null) return saved;
        com.google.firebase.auth.FirebaseUser f = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        return (f != null) ? f.getUid() : null;
    }

    public static void setUserId(Context context, String userId) {
        saveUserId(context, userId);
    }

    public static FirebaseUser getCurrentFirebaseUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public static void getUsername(Context context, UsernameCallback callback) {
        String userId = getUserId(context);

        if (userId == null) {
            callback.onUsernameLoaded(null);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    String username = (user != null) ? user.getUsername() : null;
                    callback.onUsernameLoaded(username);
                })
                .addOnFailureListener(e -> {
                    callback.onUsernameLoaded(null);
                });
    }

    public static void getUserProfile(Context context, UserProfileCallback callback) {
        String userId = getUserId(context);

        if (userId == null) {
            callback.onUserLoaded(null);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    callback.onUserLoaded(user);
                })
                .addOnFailureListener(e -> {
                    callback.onUserLoaded(null);
                });
    }

    public static void getProfilePicture(Context context, ProfilePictureCallback callback) {
        String userId = getUserId(context);

        if (userId == null) {
            callback.onProfilePictureLoaded(null);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String base64 = documentSnapshot.getString("profilePicture");

                        if (base64 == null || base64.equals("default")) {
                            callback.onProfilePictureLoaded(null);
                            return;
                        }

                        try {
                            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            callback.onProfilePictureLoaded(bitmap);
                        } catch (Exception e) {
                            callback.onProfilePictureLoaded(null);
                        }
                    } else {
                        callback.onProfilePictureLoaded(null);
                    }
                })
                .addOnFailureListener(e -> {
                    callback.onProfilePictureLoaded(null);
                });
    }

    public static void logout(Context context) {
        FirebaseAuth.getInstance().signOut();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public interface UsernameCallback {
        void onUsernameLoaded(String username);
    }

    public interface UserProfileCallback {
        void onUserLoaded(User user);
    }

    public interface ProfilePictureCallback {
        void onProfilePictureLoaded(Bitmap profilePicture);
    }
}