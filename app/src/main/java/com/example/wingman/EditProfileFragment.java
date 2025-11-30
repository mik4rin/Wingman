package com.example.wingman;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileFragment extends Fragment {

    private static final String TAG = "EditProfileFragment";
    private static final long MAX_IMAGE_FILE_SIZE = 1 * 1024 * 1024;
    private static final int MAX_IMAGE_DIM = 500;

    private EditText editTextUsername, passwordInput, confirmPasswordInput;
    private Button saveButton, btnChangeProfile, btnRemoveProfile;
    private ImageView togglePassword, toggleConfirmPassword;
    private CircleImageView profileImageView;
    private TextView tvUsername, tvEmail;
    private CardView cardDeleteAccount;

    private Uri selectedImageUri;
    private String selectedImageBase64 = null;
    private String originalProfilePicBase64 = null;
    private boolean isProfilePicChanged = false;

    private Animation shake;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> imageCropLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Uri pendingCropUri = null;

    private FirebaseAuth auth;
    private FirebaseFirestore db;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_editprofile, container, false);

        Switch themeSwitch = rootView.findViewById(R.id.theme_switch);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setupThemeSwitch(themeSwitch);
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideAppBars();
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews(rootView);
        setupPermissionLauncher();
        setupImageLaunchers();
        setupListeners();
        loadUserData();

        return rootView;
    }

    private void initializeViews(View rootView) {
        ImageButton backButton = rootView.findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        editTextUsername = rootView.findViewById(R.id.editTextUsername);
        saveButton = rootView.findViewById(R.id.saveButton);
        saveButton.setVisibility(View.GONE);

        profileImageView = rootView.findViewById(R.id.profile_image);
        tvUsername = rootView.findViewById(R.id.tvUsername);
        tvEmail = rootView.findViewById(R.id.tvEmail);
        btnChangeProfile = rootView.findViewById(R.id.btnChangeProfile);
        btnRemoveProfile = rootView.findViewById(R.id.btnRemoveProfile);
        passwordInput = rootView.findViewById(R.id.editTextPassword);
        confirmPasswordInput = rootView.findViewById(R.id.editTextConfirmPassword);
        togglePassword = rootView.findViewById(R.id.togglePasswordVisibility);
        toggleConfirmPassword = rootView.findViewById(R.id.toggleConfirmPasswordVisibility);
        cardDeleteAccount = rootView.findViewById(R.id.cardDeleteAccount);

        shake = AnimationUtils.loadAnimation(getContext(), R.anim.shake);
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImageChooser();
                    } else {
                        Toast.makeText(getContext(), "Storage permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupImageLaunchers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            startCrop(uri);
                        }
                    }
                }
        );

        imageCropLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && pendingCropUri != null) {
                        profileImageView.setImageURI(pendingCropUri);
                        selectedImageUri = pendingCropUri;
                        selectedImageBase64 = convertImageToBase64(selectedImageUri);
                        isProfilePicChanged = selectedImageBase64 != null &&
                                (originalProfilePicBase64 == null || !selectedImageBase64.equals(originalProfilePicBase64));
                        checkForChanges();
                        pendingCropUri = null;
                    }
                }
        );
    }

    private void setupListeners() {
        TextWatcher changeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkForChanges();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        editTextUsername.addTextChangedListener(changeWatcher);
        passwordInput.addTextChangedListener(changeWatcher);
        confirmPasswordInput.addTextChangedListener(changeWatcher);

        togglePassword.setOnClickListener(v -> togglePasswordVisibility(passwordInput, togglePassword));
        toggleConfirmPassword.setOnClickListener(v -> togglePasswordVisibility(confirmPasswordInput, toggleConfirmPassword));

        saveButton.setOnClickListener(v -> handleSaveButtonClick());
        btnRemoveProfile.setOnClickListener(v -> showRemoveProfileDialog());
        btnChangeProfile.setOnClickListener(v -> handleChangeProfilePicture());

        cardDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void loadUserData() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && getContext() != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            String username = doc.getString("username");
                            String email = doc.getString("email");
                            String profilePicBase64 = doc.getString("profilePicture");

                            if (username != null) {
                                editTextUsername.setText(username);
                                tvUsername.setText(username);
                                if (getActivity() != null) {
                                    getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                                            .edit().putString("username", username).apply();
                                }
                            }

                            if (email != null) {
                                tvEmail.setText(email);
                            }

                            originalProfilePicBase64 = profilePicBase64;
                            loadBase64ToImageView(profilePicBase64, profileImageView);
                        } else {
                            profileImageView.setImageResource(R.drawable.default_profile_picture);
                        }
                    })
                    .addOnFailureListener(e -> {
                        profileImageView.setImageResource(R.drawable.default_profile_picture);
                        Log.e(TAG, "Error loading user data", e);
                    });
        } else {
            profileImageView.setImageResource(R.drawable.default_profile_picture);
        }
    }

    private void handleChangeProfilePicture() {
        if (getContext() == null) return;

        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_IMAGES :
                Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(getContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openImageChooser();
        } else {
            showStoragePermissionExplanationDialog();
        }
    }

    private void showRemoveProfileDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Remove Profile Picture")
                .setMessage("Are you sure you want to remove your profile picture?")
                .setPositiveButton("Remove", (dialog, which) -> removeProfilePicture())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void removeProfilePicture() {
        FirebaseUser cu = auth.getCurrentUser();
        if (cu != null) {
            db.collection("users").document(cu.getUid())
                    .update("profilePicture", "default")
                    .addOnSuccessListener(unused -> {
                        profileImageView.setImageResource(R.drawable.default_profile_picture);
                        Toast.makeText(getContext(), "Profile picture removed", Toast.LENGTH_SHORT).show();
                        selectedImageUri = null;
                        selectedImageBase64 = null;
                        originalProfilePicBase64 = "default";
                        isProfilePicChanged = false;
                        checkForChanges();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to remove profile picture", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error removing profile picture", e);
                    });
        }
    }

    private void showDeleteAccountDialog() {
        if (getContext() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_delete_account, null);

        EditText passwordField = dialogView.findViewById(R.id.dialogPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnDelete = dialogView.findViewById(R.id.btnDelete);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            String password = passwordField.getText().toString().trim();
            if (password.isEmpty()) {
                passwordField.setError("Please enter your password");
                passwordField.startAnimation(shake);
                Toast.makeText(getContext(), "Please enter your password", Toast.LENGTH_SHORT).show();
            } else {
                dialog.dismiss();
                deleteAccount(password);
            }
        });

        dialog.show();
    }

    private void deleteAccount(String password) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || currentUser.getEmail() == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "Deleting account...", Toast.LENGTH_SHORT).show();

        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> {
                    String uid = currentUser.getUid();

                    db.collection("users").document(uid)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                currentUser.delete()
                                        .addOnSuccessListener(aVoid2 -> {
                                            Toast.makeText(getContext(), "Account deleted", Toast.LENGTH_SHORT).show();
                                            clearLocalData();
                                            redirectToLogin();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(getContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                            Log.e(TAG, "Error deleting auth account", e);
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Failed to delete data: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Error deleting Firestore data", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Re-authentication failed", e);
                });
    }

    private void redirectToLogin() {
        if (getContext() == null || getActivity() == null) return;

        auth.signOut();
        clearLocalData();

        Intent intent = new Intent(getContext(), LogIn.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finish();
    }

    private void clearLocalData() {
        if (getActivity() == null || getContext() == null) return;

        SharedPreferences prefs = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private void startCrop(Uri imageUri) {
        if (getContext() == null) return;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "profile_crop_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        pendingCropUri = getContext().getContentResolver().insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
        );

        Intent cropIntent = new Intent("com.android.camera.action.CROP");
        cropIntent.setDataAndType(imageUri, "image/*");
        cropIntent.putExtra("crop", "true");
        cropIntent.putExtra("aspectX", 1);
        cropIntent.putExtra("aspectY", 1);
        cropIntent.putExtra("outputX", MAX_IMAGE_DIM);
        cropIntent.putExtra("outputY", MAX_IMAGE_DIM);
        cropIntent.putExtra("scale", true);
        cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCropUri);
        cropIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());

        imageCropLauncher.launch(cropIntent);
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showStoragePermissionExplanationDialog() {
        if (getContext() == null) return;

        new AlertDialog.Builder(getContext())
                .setTitle("Storage Permission Needed")
                .setMessage("This app needs access to your device storage to change your profile picture")
                .setPositiveButton("Allow", (dialog, which) -> {
                    String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                            Manifest.permission.READ_MEDIA_IMAGES :
                            Manifest.permission.READ_EXTERNAL_STORAGE;
                    requestPermissionLauncher.launch(permission);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    Toast.makeText(getContext(), "Storage permission denied", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    private void handleSaveButtonClick() {
        if (getActivity() == null) return;

        String newUsername = editTextUsername.getText().toString().trim();
        SharedPreferences prefs = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);
        String oldUsername = prefs.getString("username", "");
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newUsername.isEmpty()) {
            editTextUsername.setError("Please enter a username");
            editTextUsername.startAnimation(shake);
            editTextUsername.requestFocus();
            return;
        }

        if (!newUsername.matches("^[a-zA-Z0-9_]{3,15}$")) {
            editTextUsername.setError("Username must be 3–15 characters");
            editTextUsername.startAnimation(shake);
            editTextUsername.requestFocus();
            return;
        }

        if (!password.isEmpty()) {
            if (password.length() < 6) {
                passwordInput.setError("Password must be at least 6 characters");
                passwordInput.startAnimation(shake);
                togglePassword.startAnimation(shake);
                passwordInput.requestFocus();
                return;
            }

            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!]).{6,}$")) {
                passwordInput.setError("Password must include uppercase, lowercase, number, and special character");
                passwordInput.startAnimation(shake);
                togglePassword.startAnimation(shake);
                passwordInput.requestFocus();
                return;
            }

            if (confirmPassword.isEmpty()) {
                confirmPasswordInput.setError("Please confirm your password");
                confirmPasswordInput.startAnimation(shake);
                toggleConfirmPassword.startAnimation(shake);
                confirmPasswordInput.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordInput.setError("Passwords do not match");
                confirmPasswordInput.startAnimation(shake);
                toggleConfirmPassword.startAnimation(shake);
                confirmPasswordInput.requestFocus();
                return;
            }
        }

        if (!newUsername.equals(oldUsername)) {
            db.collection("users").whereEqualTo("username", newUsername).get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            editTextUsername.setError("Username already exists");
                            editTextUsername.startAnimation(shake);
                            editTextUsername.requestFocus();
                        } else {
                            saveProfileChanges(oldUsername, newUsername, password, currentUser);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(getContext(), "Failed to check username: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            saveProfileChanges(oldUsername, newUsername, password, currentUser);
        }
    }

    private void saveProfileChanges(String oldUsername, String newUsername, String password, FirebaseUser currentUser) {
        if (getActivity() == null) return;

        boolean usernameChanged = !newUsername.equals(oldUsername);
        boolean profilePicChanged = selectedImageBase64 != null &&
                (originalProfilePicBase64 == null || !selectedImageBase64.equals(originalProfilePicBase64));
        boolean passwordChanged = !password.isEmpty();

        if (!usernameChanged && !passwordChanged && !profilePicChanged) {
            Toast.makeText(getContext(), "No changes detected", Toast.LENGTH_SHORT).show();
            return;
        }

        AtomicInteger completedOperations = new AtomicInteger(0);
        int totalOperations = (usernameChanged ? 1 : 0) + (profilePicChanged ? 1 : 0) + (passwordChanged ? 1 : 0);

        Runnable checkCompletionAndNavigate = () -> {
            if (completedOperations.incrementAndGet() >= totalOperations) {
                new Handler().postDelayed(() -> {
                    loadUserData();
                    saveButton.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Changes saved successfully", Toast.LENGTH_SHORT).show();
                }, 500);
            }
        };

        String uid = currentUser.getUid();
        SharedPreferences prefs = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE);

        if (usernameChanged) {
            db.collection("users").document(uid)
                    .update("username", newUsername)
                    .addOnSuccessListener(unused -> {
                        prefs.edit().putString("username", newUsername).apply();
                        tvUsername.setText(newUsername);
                        checkCompletionAndNavigate.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to update username", Toast.LENGTH_SHORT).show();
                        checkCompletionAndNavigate.run();
                    });
        }

        if (profilePicChanged) {
            if (selectedImageBase64 == null && selectedImageUri != null) {
                selectedImageBase64 = convertImageToBase64(selectedImageUri);
            }

            if (selectedImageBase64 != null) {
                db.collection("users").document(uid)
                        .update("profilePicture", selectedImageBase64)
                        .addOnSuccessListener(unused -> {
                            originalProfilePicBase64 = selectedImageBase64;
                            isProfilePicChanged = false;
                            checkForChanges();
                            checkCompletionAndNavigate.run();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(), "Failed to update profile picture", Toast.LENGTH_SHORT).show();
                            checkCompletionAndNavigate.run();
                        });
            } else {
                Toast.makeText(getContext(), "Image too large", Toast.LENGTH_LONG).show();
                checkCompletionAndNavigate.run();
            }
        }

        if (passwordChanged) {
            currentUser.updatePassword(password)
                    .addOnSuccessListener(unused -> {
                        passwordInput.setText("");
                        confirmPasswordInput.setText("");
                        checkForChanges();
                        checkCompletionAndNavigate.run();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to update password: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        checkCompletionAndNavigate.run();
                    });
        }
    }

    private String convertImageToBase64(Uri uri) {
        if (uri == null || getContext() == null) return null;

        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), uri);

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            float scale = Math.min((float) MAX_IMAGE_DIM / width, (float) MAX_IMAGE_DIM / height);

            if (scale < 1f) {
                int newW = Math.round(width * scale);
                int newH = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
            }

            int quality = 80;
            byte[] imageBytes;

            for (int attempt = 0; attempt < 5; attempt++) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                imageBytes = baos.toByteArray();

                if (imageBytes.length <= MAX_IMAGE_FILE_SIZE) {
                    return Base64.encodeToString(imageBytes, Base64.DEFAULT);
                }

                quality -= 12;
                if (quality < 20) quality = 20;

                if (attempt == 2) {
                    int newW = Math.max(50, (int) (bitmap.getWidth() * 0.8));
                    int newH = Math.max(50, (int) (bitmap.getHeight() * 0.8));
                    bitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, baos);
            imageBytes = baos.toByteArray();
            return Base64.encodeToString(imageBytes, Base64.DEFAULT);

        } catch (IOException e) {
            Log.e(TAG, "Error converting image to Base64", e);
            return null;
        }
    }

    private void loadBase64ToImageView(String base64, CircleImageView imageView) {
        if (base64 == null || base64.equals("default")) {
            imageView.setImageResource(R.drawable.default_profile_picture);
            return;
        }

        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.default_profile_picture);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading Base64 image", e);
            imageView.setImageResource(R.drawable.default_profile_picture);
        }
    }

    private void checkForChanges() {
        if (getActivity() == null) return;

        String currentUsernameInput = editTextUsername.getText().toString().trim();
        String oldUsername = getActivity().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                .getString("username", "");
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        boolean usernameChanged = !currentUsernameInput.equals(oldUsername);
        boolean passwordChanged = !password.isEmpty() || !confirmPassword.isEmpty();
        boolean profilePicChanged = isProfilePicChanged;

        if (usernameChanged || passwordChanged || profilePicChanged) {
            saveButton.setVisibility(View.VISIBLE);
        } else {
            saveButton.setVisibility(View.GONE);
        }
    }

    private void togglePasswordVisibility(EditText passwordField, ImageView toggleIcon) {
        if (passwordField.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_eye_open);
        } else {
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_eye_closed);
        }
        passwordField.setSelection(passwordField.getText().length());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).restoreAppBarsIfNeeded();
        }
    }
}