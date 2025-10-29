package com.example.wingman.util;

public class MessageConstants {

    // Success Messages
    public static final String SUCCESS_ACCOUNT_CREATED = "Account created successfully! Welcome to Wingman.";
    public static final String SUCCESS_LOGIN = "Welcome back, %s!"; // Requires username parameter
    public static final String SUCCESS_PASSWORD_RESET = "Password reset successful! You can now log in with your new password.";
    public static final String SUCCESS_CODE_SENT = "Verification code sent to %s"; // Requires email parameter
    public static final String SUCCESS_CODE_VERIFIED = "Code verified successfully! You can now reset your password.";

    // Error Messages - Authentication
    public static final String ERROR_EMAIL_NOT_FOUND = "No account found with this email address";
    public static final String ERROR_USERNAME_NOT_FOUND = "Username not found";
    public static final String ERROR_INVALID_CREDENTIALS = "Invalid email or password";
    public static final String ERROR_USERNAME_EXISTS = "This username is already taken";
    public static final String ERROR_EMAIL_EXISTS = "An account with this email already exists";

    // Error Messages - Validation
    public static final String ERROR_REQUIRED_USERNAME = "Username is required";
    public static final String ERROR_REQUIRED_EMAIL = "UMAK email is required";
    public static final String ERROR_REQUIRED_PASSWORD = "Password is required";
    public static final String ERROR_REQUIRED_LOGIN_INPUT = "Email or username is required";

    // Error Messages - Code Verification
    public static final String ERROR_CODE_EXPIRED = "Verification code has expired. Please request a new one.";
    public static final String ERROR_CODE_INCORRECT = "Incorrect verification code. Please try again.";
    public static final String ERROR_CODE_NOT_VERIFIED = "Please verify your code first";

    // Error Messages - System
    public static final String ERROR_NETWORK = "Network error. Please check your connection and try again.";
    public static final String ERROR_SEND_EMAIL = "Failed to send verification email. Please try again.";
    public static final String ERROR_PROCESSING_PASSWORD = "Error processing password. Please try again.";
    public static final String ERROR_UPDATE_PASSWORD = "Failed to update password: %s"; // Requires error detail
    public static final String ERROR_CHECKING_USER = "Error checking user information: %s"; // Requires error detail
    public static final String ERROR_CHECKING_USERNAME = "Error checking username: %s"; // Requires error detail
    public static final String ERROR_ACCOUNT_CREATION = "Failed to create account: %s"; // Requires error detail
    public static final String ERROR_SAVE_USER = "Error saving user data: %s"; // Requires error detail
    public static final String ERROR_INTERNAL = "An internal error occurred. Please try again.";

    // Error Messages - Cycle-Based Login Lockout
    // Phase 1: Free attempts (1-3)
    public static final String WARNING_BRUTE_FORCE_DETECTED = "Too many failed attempts. Monitoring is now active.";
    public static final String WARNING_FREE_ATTEMPTS_REMAINING = "Invalid credentials. %d attempt(s) remaining before monitoring begins.";

    // Phase 2: Counter phase (4-8)
    public static final String WARNING_COUNTER_ATTEMPTS_REMAINING = "Invalid credentials. %d attempt(s) remaining before account lockout.";
    public static final String WARNING_COUNTER_ATTEMPTS_CRITICAL = "Invalid credentials. Account will be locked after next failure!";

    // Phase 3: Locked out
    public static final String ERROR_ACCOUNT_LOCKED = "Account locked due to too many failed attempts. Locked for %s. Please try again later.";
    public static final String ERROR_ACCOUNT_LOCKED_AFTER_ATTEMPTS = "Too many failed login attempts. Your account is now locked for security.";

    // Info Messages
    public static final String INFO_RESEND_COUNTDOWN = "Resend in %ds"; // Requires seconds parameter
    public static final String INFO_RESETTING_PASSWORD = "Resetting password...";

    // Dialog Messages
    public static final String DIALOG_CANCEL_RESET_TITLE = "Cancel Password Reset?";
    public static final String DIALOG_CANCEL_RESET_MESSAGE = "Are you sure you want to cancel? Your progress will be lost.";
    public static final String DIALOG_BUTTON_YES = "Yes";
    public static final String DIALOG_BUTTON_NO = "No";

    // Button Text
    public static final String BUTTON_SEND_CODE = "Send Verification Code";
    public static final String BUTTON_RESEND_CODE = "Resend Code";
    public static final String BUTTON_VERIFY_CODE = "Verify Code";
    public static final String BUTTON_RESET_PASSWORD = "Reset Password";
    public static final String BUTTON_LOGIN = "Log In";
    public static final String BUTTON_CREATE_ACCOUNT = "Create Account";

    // Email Templates
    public static final String EMAIL_SUBJECT_VERIFICATION = "Wingman - Password Reset Verification Code";
    public static final String EMAIL_SUBJECT_WELCOME = "Welcome to Wingman!";

    public static String getVerificationEmailBody(String code, boolean isResend) {
        return "Dear UMAK Student,\n\n" +
                (isResend ? "As requested, here is your new password reset verification code:\n\n"
                        : "You have requested to reset your Wingman account password. " +
                        "Please use the verification code below:\n\n") +
                "Verification Code: " + code + "\n\n" +
                "This code will expire in 5 minutes for security purposes.\n\n" +
                "If you did not request this password reset, please ignore this email. " +
                "Your account remains secure.\n\n" +
                "Best regards,\n" +
                "The Wingman Team";
    }

    public static String getWelcomeEmailBody(String username) {
        return "Dear " + username + ",\n\n" +
                "Welcome to Wingman! Thank you for joining our community of UMAK students.\n\n" +
                "Wingman is designed to help you stay organized and productive throughout your academic journey. " +
                "From managing notes, schedules, and tasks to using tools like flashcards and a Pomodoro timer, " +
                "our app provides everything you need to make studying more efficient and rewarding.\n\n" +
                "Start exploring the app and discover how Wingman can make your academic life simpler and more manageable.\n\n" +
                "Warm regards,\n" +
                "The Wingman Team";
    }

    private MessageConstants() {
        // Private constructor to prevent instantiation
    }
}
