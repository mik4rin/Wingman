package com.example.wingman.util;

import android.util.Patterns;
import java.util.regex.Pattern;

public class ValidationUtils {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!*()]).{8,}$"
    );

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 30;
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9][a-zA-Z0-9 ._-]*[a-zA-Z0-9]$"
    );

    private static final String UMAK_EMAIL_DOMAIN = "@umak.edu.ph";

    public static ValidationResult validatePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return new ValidationResult(false, "Password is required");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            return new ValidationResult(false, "Password must be at least 8 characters");
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return new ValidationResult(false,
                    "Password must contain uppercase, lowercase, number, and special character (@#$%^&+=!*())");
        }

        return new ValidationResult(true, null);
    }

    public static ValidationResult validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, "Username is required");
        }

        String trimmedUsername = username.trim();

        if (trimmedUsername.length() < MIN_USERNAME_LENGTH) {
            return new ValidationResult(false,
                    "Username must be at least " + MIN_USERNAME_LENGTH + " characters");
        }

        if (trimmedUsername.length() > MAX_USERNAME_LENGTH) {
            return new ValidationResult(false,
                    "Username cannot exceed " + MAX_USERNAME_LENGTH + " characters");
        }

        if (!USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            return new ValidationResult(false,
                    "Username can only contain letters, numbers, spaces, dots, underscores, and hyphens");
        }

        if (trimmedUsername.contains("  ")) {
            return new ValidationResult(false, "Username cannot contain consecutive spaces");
        }

        return new ValidationResult(true, null);
    }

    public static ValidationResult validateUmakEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return new ValidationResult(false, "UMAK email is required");
        }

        String trimmedEmail = email.trim().toLowerCase();

        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return new ValidationResult(false, "Invalid email format");
        }

        if (!trimmedEmail.endsWith(UMAK_EMAIL_DOMAIN)) {
            return new ValidationResult(false,
                    "Only UMAK email addresses (@umak.edu.ph) are allowed");
        }

        String localPart = trimmedEmail.substring(0, trimmedEmail.indexOf("@"));
        if (localPart.length() < 1) {
            return new ValidationResult(false, "Invalid email address");
        }

        return new ValidationResult(true, null);
    }

    public static ValidationResult validateVerificationCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return new ValidationResult(false, "Verification code is required");
        }

        String trimmedCode = code.trim();
        if (!trimmedCode.matches("^\\d{6}$")) {
            return new ValidationResult(false, "Verification code must be 6 digits");
        }

        return new ValidationResult(true, null);
    }

    public static ValidationResult validatePasswordMatch(String password, String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            return new ValidationResult(false, "Please confirm your password");
        }

        if (!password.equals(confirmPassword)) {
            return new ValidationResult(false, "Passwords do not match");
        }

        return new ValidationResult(true, null);
    }

    public static String sanitizeUsername(String username) {
        if (username == null) return "";
        // Trim outer spaces and collapse multiple consecutive spaces to single space
        return username.trim().replaceAll("\\s+", " ");
    }

    public static String sanitizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }

    public static class ValidationResult {
        private final boolean isValid;
        private final String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}