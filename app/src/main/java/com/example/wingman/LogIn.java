package com.example.wingman;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wingman.util.LoginAttemptManager;
import com.example.wingman.util.MessageConstants;
import com.example.wingman.util.ValidationUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LogIn extends AppCompatActivity {

    private static final String TAG = "LogIn";
    private static final String PREFS_NAME = "loginPrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_SAVED_EMAIL = "email";

    LinearLayout backButton;
    ImageButton togglePasswordVisibility;
    EditText usernameInput, passwordInput;
    Button loginButton;
    TextView forgotPasswordText, createAccountLink;
    CheckBox rememberMeCheckbox;
    SharedPreferences sharedPreferences;
    Animation shake;
    Vibrator vibrator;
    FirebaseFirestore db;
    LoginAttemptManager attemptManager;

    private boolean isLoggingIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);
        hideSystemUI();

        db = FirebaseFirestore.getInstance();
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initializeViews();
        loadSavedCredentials();
        setupListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        usernameInput = findViewById(R.id.username);
        passwordInput = findViewById(R.id.password);
        loginButton = findViewById(R.id.btn_login);
        forgotPasswordText = findViewById(R.id.forgotPassword);
        createAccountLink = findViewById(R.id.createAccountLink);
        rememberMeCheckbox = findViewById(R.id.rememberMe);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);

        shake = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.shake);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        attemptManager = new LoginAttemptManager(this);

        setupErrorClearingListeners();
    }

    private void setupErrorClearingListeners() {
        TextWatcher errorClearWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s != null && s.length() > 0) {
                    if (usernameInput.getText() == s) {
                        usernameInput.setError(null);
                    } else if (passwordInput.getText() == s) {
                        passwordInput.setError(null);
                    }
                }
            }
        };

        usernameInput.addTextChangedListener(errorClearWatcher);
        passwordInput.addTextChangedListener(errorClearWatcher);
    }

    private void loadSavedCredentials() {
        boolean rememberMe = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
        if (rememberMe) {
            String savedEmail = sharedPreferences.getString(KEY_SAVED_EMAIL, "");
            usernameInput.setText(savedEmail);
            rememberMeCheckbox.setChecked(true);
        }
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> {
            if (!isLoggingIn) {
                attemptLogin();
            }
        });

        backButton.setOnClickListener(v -> finish());

        forgotPasswordText.setOnClickListener(v -> {
            startActivity(new Intent(this, ForgotPasswordActivity.class));
        });

        createAccountLink.setOnClickListener(v -> {
            startActivity(new Intent(this, CreateAccount.class));
        });

        togglePasswordVisibility.setOnClickListener(v -> {
            if (passwordInput.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_open);
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_closed);
            }
            passwordInput.setSelection(passwordInput.getText().length());
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    private void attemptLogin() {
        usernameInput.setError(null);
        passwordInput.setError(null);

        String loginInput = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (loginInput.isEmpty()) {
            showError(usernameInput, MessageConstants.ERROR_REQUIRED_LOGIN_INPUT);
            return;
        }

        if (password.isEmpty()) {
            showError(passwordInput, MessageConstants.ERROR_REQUIRED_PASSWORD);
            return;
        }

        if (attemptManager.isLockedOut(loginInput)) {
            long remainingTime = attemptManager.getRemainingLockoutTime(loginInput);
            String formattedTime = LoginAttemptManager.formatRemainingTime(remainingTime);
            String lockoutMessage = String.format(MessageConstants.ERROR_ACCOUNT_LOCKED, formattedTime);
            showError(usernameInput, lockoutMessage);
            return;
        }

        isLoggingIn = true;
        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");

        if (loginInput.contains("@")) {
            String email = ValidationUtils.sanitizeEmail(loginInput);
            signInWithEmail(email, password);
        } else {
            String username = ValidationUtils.sanitizeUsername(loginInput);
            lookupUsernameAndSignIn(username, password);
        }
    }

    private void lookupUsernameAndSignIn(String username, String password) {
        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String email = querySnapshot.getDocuments().get(0).getString("email");
                        if (email != null && !email.isEmpty()) {
                            signInWithEmail(email, password);
                        } else {
                            attemptManager.recordFailedAttempt(username);
                            showError(usernameInput, MessageConstants.ERROR_INTERNAL);
                            resetLoginButton();
                        }
                    } else {
                        attemptManager.recordFailedAttempt(username);
                        showError(usernameInput, MessageConstants.ERROR_USERNAME_NOT_FOUND);
                        resetLoginButton();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error looking up username", e);
                    attemptManager.recordFailedAttempt(username);
                    Toast.makeText(this, "Error checking username: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resetLoginButton();
                });
    }

    private void signInWithEmail(String email, String password) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        db.collection("users")
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    if (!querySnapshot.isEmpty()) {
                                        DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                                        attemptManager.recordSuccessfulLogin(email);
                                        handleSuccessfulLogin(userDoc);
                                    } else {
                                        Toast.makeText(this, "User record missing in Firestore.", Toast.LENGTH_SHORT).show();
                                        resetLoginButton();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching Firestore user", e);
                                    Toast.makeText(this, "Error fetching user data.", Toast.LENGTH_SHORT).show();
                                    resetLoginButton();
                                });
                    } else {
                        attemptManager.recordFailedAttempt(email);
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Authentication failed.";
                        Log.e(TAG, "Auth login failed: " + errorMessage);
                        showError(passwordInput, "Invalid email or password.");
                        resetLoginButton();
                    }
                });
    }

    private void handleSuccessfulLogin(DocumentSnapshot userDoc) {
        String userId = userDoc.getId();
        String username = userDoc.getString("username");
        String email = userDoc.getString("email");

        SessionManager.setUserId(getApplicationContext(), userId);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (rememberMeCheckbox.isChecked()) {
            editor.putString(KEY_SAVED_EMAIL, email);
            editor.putBoolean(KEY_REMEMBER_ME, true);
        } else {
            editor.remove(KEY_SAVED_EMAIL);
            editor.putBoolean(KEY_REMEMBER_ME, false);
        }
        editor.apply();

        Toast.makeText(this,
                String.format(MessageConstants.SUCCESS_LOGIN, username),
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LogIn.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException ex) {
            Log.e(TAG, "Error hashing password", ex);
            return null;
        }
    }

    private void showError(EditText field, String message) {
        field.setError(message);
        field.startAnimation(shake);
        field.requestFocus();
        vibrateOnError();

        if (field == passwordInput) {
            togglePasswordVisibility.startAnimation(shake);
        }
    }

    private void resetLoginButton() {
        isLoggingIn = false;
        loginButton.setEnabled(true);
        loginButton.setText(MessageConstants.BUTTON_LOGIN);
    }

    private void vibrateOnError() {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(200);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                int[] scrcoords = new int[2];
                v.getLocationOnScreen(scrcoords);
                float x = ev.getRawX() + v.getLeft() - scrcoords[0];
                float y = ev.getRawY() + v.getTop() - scrcoords[1];

                if (x < v.getLeft() || x > v.getRight() || y < v.getTop() || y > v.getBottom()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                    v.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vibrator != null) {
            vibrator.cancel();
        }
    }
}