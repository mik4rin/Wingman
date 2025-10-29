package com.example.wingman;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.wingman.util.MessageConstants;
import com.example.wingman.util.ValidationUtils;
import com.example.wingman.util.ValidationUtils.ValidationResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.Executors;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPassword";
    private static final long COOLDOWN_DURATION = 60_000;
    private static final long CODE_EXPIRATION = 5 * 60 * 1000;

    private Handler resendHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    private long cooldownEndTime = 0;
    private long codeGeneratedTime = 0;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String generatedCode;
    private String userEmail;
    private String userDocId;
    private boolean isResend = false;
    private boolean isCodeVerified = false;
    private boolean isSendingCode = false;
    private boolean isVerifyingCode = false;

    EditText emailInput, verificationCodeInput;
    Button sendCodeButton, verifyCodeButton;
    LinearLayout backButton;
    Animation shake;
    Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        hideSystemUI();

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initializeViews();
        setupListeners();
        setupInitialVisibility();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        emailInput = findViewById(R.id.emailReference);
        verificationCodeInput = findViewById(R.id.verificationCodeInput);
        sendCodeButton = findViewById(R.id.btn_sendCode);
        verifyCodeButton = findViewById(R.id.btn_verifyCode);
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
                    EditText[] fields = {emailInput, verificationCodeInput};
                    for (EditText field : fields) {
                        if (field.getText() == s) {
                            field.setError(null);
                            break;
                        }
                    }
                }
            }
        };

        emailInput.addTextChangedListener(errorClearWatcher);
        verificationCodeInput.addTextChangedListener(errorClearWatcher);
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> showCancelDialog());

        sendCodeButton.setOnClickListener(v -> {
            if (!isSendingCode) {
                sendVerificationCode();
            }
        });

        verifyCodeButton.setOnClickListener(v -> {
            if (!isVerifyingCode) {
                verifyCode();
            }
        });
    }

    private void setupInitialVisibility() {
        verificationCodeInput.setVisibility(View.GONE);
        findViewById(R.id.verificationCodeLabel).setVisibility(View.GONE);
        verifyCodeButton.setVisibility(View.GONE);
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

    private void showCancelDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unsaved_changes, null);

        androidx.appcompat.app.AlertDialog cancelDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView title = dialogView.findViewById(R.id.dialogTitleText);
        TextView message = dialogView.findViewById(R.id.dialogMessageText);
        androidx.appcompat.widget.AppCompatButton noBtn = dialogView.findViewById(R.id.buttonNo);
        androidx.appcompat.widget.AppCompatButton yesBtn = dialogView.findViewById(R.id.btnYes);

        title.setText("Cancel Password Reset?");
        message.setText("Are you sure you want to cancel? Your progress will be lost.");

        noBtn.setText("No");
        yesBtn.setText("Yes, Cancel");

        noBtn.setOnClickListener(v -> cancelDialog.dismiss());

        yesBtn.setOnClickListener(v -> {
            cancelDialog.dismiss();
            finish();
        });

        cancelDialog.show();
    }

    private void sendVerificationCode() {
        long currentTime = System.currentTimeMillis();

        if (currentTime < cooldownEndTime) {
            return;
        }

        if (codeGeneratedTime != 0 && currentTime - codeGeneratedTime > CODE_EXPIRATION) {
            Toast.makeText(this, MessageConstants.ERROR_CODE_EXPIRED, Toast.LENGTH_LONG).show();
            resetCodeGeneration();
            return;
        }

        String email = ValidationUtils.sanitizeEmail(emailInput.getText().toString());
        ValidationResult emailResult = ValidationUtils.validateUmakEmail(email);

        if (!emailResult.isValid()) {
            showError(emailInput, emailResult.getErrorMessage());
            return;
        }

        isSendingCode = true;
        sendCodeButton.setEnabled(false);

        db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        showError(emailInput, MessageConstants.ERROR_EMAIL_NOT_FOUND);
                        isSendingCode = false;
                        sendCodeButton.setEnabled(true);
                        return;
                    }

                    DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    userDocId = doc.getId();
                    userEmail = email;

                    generateAndSendCode();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking email", e);
                    Toast.makeText(this,
                            String.format(MessageConstants.ERROR_CHECKING_USER, e.getMessage()),
                            Toast.LENGTH_SHORT).show();
                    isSendingCode = false;
                    sendCodeButton.setEnabled(true);
                });
    }

    private void generateAndSendCode() {
        generatedCode = String.format("%06d", (int) (Math.random() * 1_000_000));
        codeGeneratedTime = System.currentTimeMillis();

        Log.d(TAG, "Generated code: " + generatedCode + " for email: " + userEmail);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                EmailSender.sendEmail(
                        userEmail,
                        MessageConstants.EMAIL_SUBJECT_VERIFICATION,
                        MessageConstants.getVerificationEmailBody(generatedCode, isResend)
                );

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            String.format(MessageConstants.SUCCESS_CODE_SENT, userEmail),
                            Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Email sent successfully");

                    showVerificationSection();

                    emailInput.setEnabled(false);

                    startCooldown();

                    isSendingCode = false;
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to send email", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, MessageConstants.ERROR_SEND_EMAIL, Toast.LENGTH_SHORT).show();
                    isSendingCode = false;
                    sendCodeButton.setEnabled(true);
                });
            }
        });
    }

    private void showVerificationSection() {
        verificationCodeInput.setVisibility(View.VISIBLE);
        findViewById(R.id.verificationCodeLabel).setVisibility(View.VISIBLE);
        verifyCodeButton.setVisibility(View.VISIBLE);
        verificationCodeInput.requestFocus();
    }

    private void startCooldown() {
        sendCodeButton.setEnabled(false);
        sendCodeButton.setAlpha(0.5f);

        cooldownEndTime = System.currentTimeMillis() + COOLDOWN_DURATION;

        if (countdownRunnable != null) {
            resendHandler.removeCallbacks(countdownRunnable);
        }

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long timeLeft = cooldownEndTime - System.currentTimeMillis();
                if (timeLeft > 0) {
                    long secondsLeft = timeLeft / 1000;
                    sendCodeButton.setText(String.format(MessageConstants.INFO_RESEND_COUNTDOWN, secondsLeft));
                    resendHandler.postDelayed(this, 1000);
                } else {
                    sendCodeButton.setEnabled(true);
                    sendCodeButton.setAlpha(1.0f);
                    sendCodeButton.setText(MessageConstants.BUTTON_RESEND_CODE);
                    isResend = true;
                }
            }
        };
        resendHandler.post(countdownRunnable);
    }

    private void verifyCode() {
        String inputCode = verificationCodeInput.getText().toString().trim();

        ValidationResult codeResult = ValidationUtils.validateVerificationCode(inputCode);
        if (!codeResult.isValid()) {
            showError(verificationCodeInput, codeResult.getErrorMessage());
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (codeGeneratedTime != 0 && currentTime - codeGeneratedTime > CODE_EXPIRATION) {
            showError(verificationCodeInput, MessageConstants.ERROR_CODE_EXPIRED);
            return;
        }

        if (!inputCode.equals(generatedCode)) {
            showError(verificationCodeInput, MessageConstants.ERROR_CODE_INCORRECT);
            return;
        }

        isVerifyingCode = true;
        verifyCodeButton.setEnabled(false);
        verifyCodeButton.setText("Verifying...");

        // Code is correct, send Firebase password reset email
        auth.sendPasswordResetEmail(userEmail)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Verification successful! Password reset link sent to " + userEmail,
                            Toast.LENGTH_LONG).show();

                    Log.d(TAG, "Password reset email sent to: " + userEmail);

                    // Clean up and close activity
                    if (countdownRunnable != null) {
                        resendHandler.removeCallbacks(countdownRunnable);
                    }

                    new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 2500);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send password reset email", e);
                    Toast.makeText(this,
                            "Failed to send reset email: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    isVerifyingCode = false;
                    verifyCodeButton.setEnabled(true);
                    verifyCodeButton.setText("Verify Code");
                });
    }

    private void resetCodeGeneration() {
        codeGeneratedTime = 0;
        generatedCode = null;
        isResend = false;
    }

    private void showError(EditText field, String message) {
        field.setError(message);
        field.startAnimation(shake);
        field.requestFocus();
        vibrateOnError();
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
    protected void onDestroy() {
        super.onDestroy();
        if (countdownRunnable != null) {
            resendHandler.removeCallbacks(countdownRunnable);
        }
        if (vibrator != null) {
            vibrator.cancel();
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
}