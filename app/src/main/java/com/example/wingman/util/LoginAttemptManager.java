package com.example.wingman.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class LoginAttemptManager {

    private static final String TAG = "LoginAttemptManager";
    private static final String PREFS_NAME = "login_attempts";
    private static final String KEY_ATTEMPT_COUNT = "attempt_count_";
    private static final String KEY_LOCKOUT_TIME = "lockout_time_";
    private static final String KEY_LAST_ATTEMPT = "last_attempt_";
    private static final String KEY_COUNTER_STARTED = "counter_started_";

    private static final int INITIAL_FREE_ATTEMPTS = 3;
    private static final int COUNTER_ATTEMPTS = 5;
    private static final int MAX_ATTEMPTS_BEFORE_LOCKOUT = INITIAL_FREE_ATTEMPTS + COUNTER_ATTEMPTS;

    private static final long LOCKOUT_DURATION = 15 * 60 * 1000;
    private static final long ATTEMPT_RESET_TIME = 60 * 60 * 1000;

    private final SharedPreferences prefs;

    public LoginAttemptManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void recordFailedAttempt(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        long currentTime = System.currentTimeMillis();

        int currentAttempts = getAttemptCount(normalizedId);
        int newAttempts = currentAttempts + 1;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_ATTEMPT_COUNT + normalizedId, newAttempts);
        editor.putLong(KEY_LAST_ATTEMPT + normalizedId, currentTime);

        if (newAttempts == INITIAL_FREE_ATTEMPTS + 1) {
            editor.putBoolean(KEY_COUNTER_STARTED + normalizedId, true);
            Log.w(TAG, "Brute force detected for: " + normalizedId + " - Counter phase started");
        }

        if (newAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            long lockoutUntil = currentTime + LOCKOUT_DURATION;
            editor.putLong(KEY_LOCKOUT_TIME + normalizedId, lockoutUntil);
            Log.w(TAG, "Account locked for: " + normalizedId +
                    " (After " + newAttempts + " attempts) - Locked for 15 minutes");
        } else {
            Log.w(TAG, "Failed attempt recorded for: " + normalizedId +
                    " (Attempt " + newAttempts + " of " + MAX_ATTEMPTS_BEFORE_LOCKOUT + ")");
        }

        editor.apply();
    }

    public void recordSuccessfulLogin(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        clearAttempts(normalizedId);
        Log.d(TAG, "Successful login, attempts cleared for: " + normalizedId);
    }

    public boolean isLockedOut(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        long currentTime = System.currentTimeMillis();
        long lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME + normalizedId, 0);

        if (lockoutTime > currentTime) {
            return true;
        } else if (lockoutTime > 0) {
            clearAttempts(normalizedId);
            return false;
        }

        long lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT + normalizedId, 0);
        if (lastAttempt > 0 && (currentTime - lastAttempt) > ATTEMPT_RESET_TIME) {
            clearAttempts(normalizedId);
        }

        return false;
    }

    public long getRemainingLockoutTime(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        long currentTime = System.currentTimeMillis();
        long lockoutTime = prefs.getLong(KEY_LOCKOUT_TIME + normalizedId, 0);

        if (lockoutTime > currentTime) {
            return lockoutTime - currentTime;
        }

        return 0;
    }

    public int getAttemptCount(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        return prefs.getInt(KEY_ATTEMPT_COUNT + normalizedId, 0);
    }

    public boolean isCounterPhaseActive(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        return prefs.getBoolean(KEY_COUNTER_STARTED + normalizedId, false);
    }

    public int getRemainingAttempts(String identifier) {
        int attempts = getAttemptCount(identifier);

        if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
            return 0;
        } else if (isCounterPhaseActive(identifier)) {
            return Math.max(0, MAX_ATTEMPTS_BEFORE_LOCKOUT - attempts);
        } else {
            return Math.max(0, INITIAL_FREE_ATTEMPTS - attempts);
        }
    }

    public String getCurrentPhase(String identifier) {
        if (isLockedOut(identifier)) {
            return "LOCKED";
        }

        if (isCounterPhaseActive(identifier)) {
            return "COUNTER";
        }

        return "FREE";
    }

    public void clearAttempts(String identifier) {
        String normalizedId = normalizeIdentifier(identifier);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(KEY_ATTEMPT_COUNT + normalizedId);
        editor.remove(KEY_LOCKOUT_TIME + normalizedId);
        editor.remove(KEY_LAST_ATTEMPT + normalizedId);
        editor.remove(KEY_COUNTER_STARTED + normalizedId);
        editor.apply();
    }

    public static String formatRemainingTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") +
                    (remainingSeconds > 0 ? " " + remainingSeconds + " second" + (remainingSeconds != 1 ? "s" : "") : "");
        } else {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) return "";
        return identifier.trim().toLowerCase();
    }

    public static int getInitialFreeAttempts() {
        return INITIAL_FREE_ATTEMPTS;
    }

    public static int getCounterPhaseAttempts() {
        return COUNTER_ATTEMPTS;
    }

    public static int getMaxAttemptsBeforeLockout() {
        return MAX_ATTEMPTS_BEFORE_LOCKOUT;
    }

    public static long getLockoutDuration() {
        return LOCKOUT_DURATION;
    }
}