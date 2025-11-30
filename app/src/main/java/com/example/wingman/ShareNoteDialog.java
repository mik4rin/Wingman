package com.example.wingman;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.Note;
import com.example.wingman.data.User;
import com.example.wingman.data.UserRepository;
import androidx.appcompat.widget.AppCompatButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShareNoteDialog extends DialogFragment {

    private static final String TAG = "ShareNoteDialog";
    private static final String ARG_NOTE = "note";

    public interface ShareNoteListener {
        void onSharedUsersUpdated(String noteId, List<String> newSharedUserIds);
    }

    private ShareNoteListener listener;
    private Note noteToShare;
    private final UserRepository userRepository = new UserRepository();

    private TextInputEditText searchUserEditText;
    private RecyclerView usersRecyclerView;
    private ProgressBar searchProgressBar;
    private androidx.appcompat.widget.AppCompatButton btnConfirm;
    private androidx.appcompat.widget.AppCompatButton btnCancel;
    private ImageButton btnClose;

    private UserAdapter userAdapter;
    private final List<User> allVisibleUsers = new ArrayList<>();
    private List<String> initialSharedUids;
    private boolean initialUsersLoaded = false;

    public static ShareNoteDialog newInstance(Note note) {
        ShareNoteDialog dialog = new ShareNoteDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_NOTE, note);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            noteToShare = (Note) getArguments().getSerializable(ARG_NOTE);
        }

        initialSharedUids = noteToShare != null && noteToShare.getSharedWith() != null
                ? noteToShare.getSharedWith() : new ArrayList<>();

        if (getTargetFragment() != null) {
            try {
                listener = (ShareNoteListener) getTargetFragment();
            } catch (ClassCastException e) {
                Log.e(TAG, "Target Fragment does not implement ShareNoteListener", e);
            }
        }

        if (listener == null && getParentFragment() != null) {
            try {
                listener = (ShareNoteListener) getParentFragment();
            } catch (ClassCastException e) {
                Log.e(TAG, "Parent Fragment does not implement ShareNoteListener", e);
            }
        }

        if (listener == null && getActivity() instanceof ShareNoteListener) {
            listener = (ShareNoteListener) getActivity();
        }

        if (listener == null) {
            throw new ClassCastException("Calling Fragment/Activity (or its parent/target) must implement ShareNoteListener.");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialog);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_share_note, null);
        builder.setView(view);

        findViews(view);
        setupRecyclerView();
        setupListeners();

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    private void findViews(View view) {
        searchUserEditText = view.findViewById(R.id.searchUserEditText);
        usersRecyclerView = view.findViewById(R.id.usersRecyclerView);
        searchProgressBar = view.findViewById(R.id.searchProgressBar);

        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnClose = view.findViewById(R.id.btnClose);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnConfirm.setEnabled(false);
    }

    private void setupRecyclerView() {
        userAdapter = new UserAdapter((user, isSelected) -> {
            btnConfirm.setEnabled(true);
        }, initialSharedUids);

        usersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        usersRecyclerView.setAdapter(userAdapter);
        loadAndDisplayInitialSharedUsers();
    }

    private void loadAndDisplayInitialSharedUsers() {
        if (initialSharedUids.isEmpty()) {
            initialUsersLoaded = true;
            usersRecyclerView.setVisibility(View.GONE);
            return;
        }

        searchProgressBar.setVisibility(View.VISIBLE);

        userRepository.getUsersByIds(initialSharedUids, new UserRepository.OnFirestoreUsersListener() {
            @Override
            public void onSuccess(List<User> users) {
                if (isAdded()) {
                    searchProgressBar.setVisibility(View.GONE);
                    initialUsersLoaded = true;

                    allVisibleUsers.clear();
                    allVisibleUsers.addAll(users);

                    userAdapter.submitList(new ArrayList<>(allVisibleUsers));
                    usersRecyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded()) {
                    Log.e(TAG, "Error loading initial shared users", e);
                    searchProgressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error loading shared users.", Toast.LENGTH_SHORT).show();
                    initialUsersLoaded = true;
                }
            }
        });
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> dismiss());
        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> saveSharedUsers());

        searchUserEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (initialUsersLoaded) {
                    performUserSearch(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnConfirm.setEnabled(true);
    }

    private void performUserSearch(String query) {
        String trimmedQuery = query.trim();

        if (trimmedQuery.length() < 3) {
            allVisibleUsers.clear();
            loadAndDisplayInitialSharedUsers();
            return;
        }

        searchProgressBar.setVisibility(View.VISIBLE);
        usersRecyclerView.setVisibility(View.GONE);

        userRepository.searchUsersByEmail(trimmedQuery, new UserRepository.OnFirestoreUsersListener() {
            @Override
            public void onSuccess(List<User> searchResults) {
                if (isAdded()) {
                    searchProgressBar.setVisibility(View.GONE);

                    Set<String> visibleUids = new HashSet<>();

                    List<User> mergedList = new ArrayList<>();
                    for (User u : allVisibleUsers) {
                        if (initialSharedUids.contains(u.getId())) {
                            mergedList.add(u);
                            visibleUids.add(u.getId());
                        }
                    }

                    for (User searchUser : searchResults) {
                        if (!visibleUids.contains(searchUser.getId())) {
                            mergedList.add(searchUser);
                        }
                    }

                    allVisibleUsers.clear();
                    allVisibleUsers.addAll(mergedList);
                    userAdapter.submitList(mergedList);

                    usersRecyclerView.setVisibility(mergedList.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                if (isAdded()) {
                    Log.e(TAG, "User search failed", e);
                    searchProgressBar.setVisibility(View.GONE);
                    usersRecyclerView.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error searching: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void saveSharedUsers() {
        if (noteToShare == null) return;

        List<String> finalSharedIds = userAdapter.getSelectedUserIds();

        String currentUid = userRepository.getCurrentUserUid();
        finalSharedIds.remove(currentUid);

        listener.onSharedUsersUpdated(noteToShare.getId(), finalSharedIds);

        dismiss();
    }
}