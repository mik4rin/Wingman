package com.example.wingman;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wingman.data.User;
import com.example.wingman.util.MessageConstants;
import com.example.wingman.util.ValidationUtils;
import com.example.wingman.util.ValidationUtils.ValidationResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executors;

public class CreateAccount extends AppCompatActivity {

    private static final String TAG = "CreateAccount";

    LinearLayout backButton;
    EditText usernameInput, emailInput, passwordInput, confirmPasswordInput;
    Button createAccountButton;
    TextView loginLink;
    ImageView togglePassword, toggleConfirmPassword;
    Vibrator vibrator;
    FirebaseAuth auth;
    FirebaseFirestore db;
    Animation shake;

    private boolean isCreatingAccount = false;

    private String pendingUsername;
    private String pendingEmail;
    private String pendingPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);
        hideSystemUI();

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        usernameInput = findViewById(R.id.user_name);
        emailInput = findViewById(R.id.user_email);
        passwordInput = findViewById(R.id.password);
        confirmPasswordInput = findViewById(R.id.confirm_password);
        createAccountButton = findViewById(R.id.btn_createAccount);
        backButton = findViewById(R.id.backButton);
        loginLink = findViewById(R.id.loginLink);
        togglePassword = findViewById(R.id.togglePassword);
        toggleConfirmPassword = findViewById(R.id.toggleConfirmPassword);

        shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

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
                    EditText[] fields = {usernameInput, emailInput, passwordInput, confirmPasswordInput};
                    for (EditText field : fields) {
                        if (field.getText() == s) {
                            field.setError(null);
                            break;
                        }
                    }
                }
            }
        };

        usernameInput.addTextChangedListener(errorClearWatcher);
        emailInput.addTextChangedListener(errorClearWatcher);
        passwordInput.addTextChangedListener(errorClearWatcher);
        confirmPasswordInput.addTextChangedListener(errorClearWatcher);
    }

    private void setupListeners() {
        togglePassword.setOnClickListener(v -> togglePasswordVisibility(passwordInput, togglePassword));
        toggleConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(confirmPasswordInput, toggleConfirmPassword));

        createAccountButton.setOnClickListener(v -> {
            if (!isCreatingAccount) {
                attemptCreateAccount();
            }
        });

        backButton.setOnClickListener(v -> finish());

        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(CreateAccount.this, LogIn.class));
            finish();
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

    private void attemptCreateAccount() {
        String username = ValidationUtils.sanitizeUsername(usernameInput.getText().toString());
        String email = ValidationUtils.sanitizeEmail(emailInput.getText().toString());
        String password = passwordInput.getText().toString();
        String confirmPassword = confirmPasswordInput.getText().toString();

        if (!validateAllInputs(username, email, password, confirmPassword)) {
            return;
        }

        pendingUsername = username;
        pendingEmail = email;
        pendingPassword = password;

        showTermsAndConditionsDialog();
    }

    private void showTermsAndConditionsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.terms_and_conditions_dialog);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setCancelable(false);

        Button btnAgree = dialog.findViewById(R.id.btnAgree);
        Button btnDecline = dialog.findViewById(R.id.btnDecline);

        btnAgree.setOnClickListener(v -> {
            dialog.dismiss();
            proceedWithAccountCreation();
        });

        btnDecline.setOnClickListener(v -> {
            dialog.dismiss();
            pendingUsername = null;
            pendingEmail = null;
            pendingPassword = null;

            Toast.makeText(this, "Account creation cancelled", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(CreateAccount.this, StartActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    private void proceedWithAccountCreation() {
        isCreatingAccount = true;
        createAccountButton.setEnabled(false);
        createAccountButton.setText("Creating Account...");

        checkUsernameAvailability(pendingUsername, pendingEmail, pendingPassword);
    }

    private boolean validateAllInputs(String username, String email, String password, String confirmPassword) {
        ValidationResult usernameResult = ValidationUtils.validateUsername(username);
        if (!usernameResult.isValid()) {
            showError(usernameInput, usernameResult.getErrorMessage());
            return false;
        }

        ValidationResult emailResult = ValidationUtils.validateUmakEmail(email);
        if (!emailResult.isValid()) {
            showError(emailInput, emailResult.getErrorMessage());
            return false;
        }

        ValidationResult passwordResult = ValidationUtils.validatePassword(password);
        if (!passwordResult.isValid()) {
            showError(passwordInput, passwordResult.getErrorMessage());
            return false;
        }

        ValidationResult matchResult = ValidationUtils.validatePasswordMatch(password, confirmPassword);
        if (!matchResult.isValid()) {
            showError(confirmPasswordInput, matchResult.getErrorMessage());
            return false;
        }

        return true;
    }

    private void checkUsernameAvailability(String username, String email, String password) {
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        showError(usernameInput, MessageConstants.ERROR_USERNAME_EXISTS);
                        resetCreateButton();
                        return;
                    }
                    createFirebaseAccount(username, email, password);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking username availability", e);
                    Toast.makeText(this,
                            String.format(MessageConstants.ERROR_CHECKING_USERNAME, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    resetCreateButton();
                });
    }

    private void createFirebaseAccount(String username, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        saveUserToFirestore(uid, username, email);
                    } else {
                        String errorMsg = task.getException() != null ?
                                task.getException().getMessage() : "Unknown error";

                        if (errorMsg.contains("email address is already in use")) {
                            showError(emailInput, MessageConstants.ERROR_EMAIL_EXISTS);
                        } else {
                            Toast.makeText(this,
                                    String.format(MessageConstants.ERROR_ACCOUNT_CREATION, errorMsg),
                                    Toast.LENGTH_SHORT).show();
                        }
                        resetCreateButton();
                    }
                });
    }

    private void saveUserToFirestore(String uid, String username, String email) {
        User newUser = new User(uid, username, email, "", "default_profile_picture.jpg");

        db.collection("users")
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(aVoid -> {
                    SessionManager.setUserId(getApplicationContext(), uid);
                    Toast.makeText(this, MessageConstants.SUCCESS_ACCOUNT_CREATED, Toast.LENGTH_SHORT).show();

                    sendWelcomeEmail(username, email);

                    pendingUsername = null;
                    pendingEmail = null;
                    pendingPassword = null;

                    startActivity(new Intent(CreateAccount.this, LogIn.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user to Firestore", e);
                    Toast.makeText(this,
                            String.format(MessageConstants.ERROR_SAVE_USER, e.getMessage()),
                            Toast.LENGTH_SHORT).show();

                    if (auth.getCurrentUser() != null) {
                        auth.getCurrentUser().delete();
                    }
                    resetCreateButton();
                });
    }

    private void sendWelcomeEmail(String username, String email) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                EmailSender.sendEmail(
                        email,
                        MessageConstants.EMAIL_SUBJECT_WELCOME,
                        MessageConstants.getWelcomeEmailBody(username)
                );
                Log.d(TAG, "Welcome email sent successfully to " + email);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send welcome email", e);
            }
        });
    }

    private void togglePasswordVisibility(EditText field, ImageView toggle) {
        if (field.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setImageResource(R.drawable.ic_eye_open);
        } else {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setImageResource(R.drawable.ic_eye_closed);
        }
        field.setSelection(field.getText().length());
    }

    private void showError(EditText field, String message) {
        field.setError(message);
        field.startAnimation(shake);
        field.requestFocus();
        vibrateOnError();
    }

    private void resetCreateButton() {
        isCreatingAccount = false;
        createAccountButton.setEnabled(true);
        createAccountButton.setText(MessageConstants.BUTTON_CREATE_ACCOUNT);
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