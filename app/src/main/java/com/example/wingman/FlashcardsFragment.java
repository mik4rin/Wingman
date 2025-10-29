package com.example.wingman;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.FlashcardRepository;
import com.example.wingman.data.OnFirestoreFlashcardsListener;
import com.example.wingman.data.OnFirestoreResultListener;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class FlashcardsFragment extends Fragment {

    private RecyclerView recyclerViewSets;
    private FlashcardSetAdapter setAdapter;
    private List<FlashcardRepository.FlashcardSetData> setList;
    private FlashcardRepository repository;
    private String currentUserId;
    private ListenerRegistration setsListenerRegistration;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_flashcards, container, false);

        MaterialCardView addCard = view.findViewById(R.id.flashcardsCard);
        ImageButton btnBack = view.findViewById(R.id.btnBack);
        recyclerViewSets = view.findViewById(R.id.recyclerViewSets);
        emptyView = view.findViewById(R.id.emptyView);

        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        addCard.setOnClickListener(v -> {
            FragmentTransaction transaction = requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction();
            transaction.replace(R.id.fragment_container, new AddCardFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });

        repository = new FlashcardRepository();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        setList = new ArrayList<>();
        setAdapter = new FlashcardSetAdapter(setList, new FlashcardSetAdapter.FlashcardSetListener() {
            @Override
            public void onSetClicked(FlashcardRepository.FlashcardSetData setData) {
                openViewFlashcardFragment(setData);
            }

            @Override
            public void onSetDeleted(FlashcardRepository.FlashcardSetData setData) {
                deleteFlashcardSet(setData);
            }
        });

        recyclerViewSets.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewSets.setAdapter(setAdapter);

        loadFlashcardSets();

        return view;
    }

    private void loadFlashcardSets() {
        if (currentUserId == null) {
            Log.e("FlashcardsFragment", "currentUserId is null");
            updateEmptyView();
            return;
        }

        Log.d("FlashcardsFragment", "Loading flashcard sets for user: " + currentUserId);

        setsListenerRegistration = repository.listenToFlashcardSets(currentUserId, new OnFirestoreFlashcardsListener<FlashcardRepository.FlashcardSetData>() {
            @Override
            public void onSuccess(List<FlashcardRepository.FlashcardSetData> data) {
                Log.d("FlashcardsFragment", "Successfully loaded " + data.size() + " flashcard sets");
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        setList.clear();
                        setList.addAll(data);
                        setAdapter.notifyDataSetChanged();
                        updateEmptyView();
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e("FlashcardsFragment", "Error loading flashcard sets", e);
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error loading flashcard sets: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        updateEmptyView();
                    });
                }
            }
        });
    }

    private void openViewFlashcardFragment(FlashcardRepository.FlashcardSetData setData) {
        repository.getFlashcardsInSet(setData.getId(), new OnFirestoreFlashcardsListener<FlashcardItem>() {
            @Override
            public void onSuccess(List<FlashcardItem> data) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        ViewFlashcardFragment fragment = ViewFlashcardFragment.newInstance(
                                data,
                                setData.getName(),
                                setData.getDescription()
                        );

                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, fragment)
                                .addToBackStack(null)
                                .commit();
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e("FlashcardsFragment", "Error loading flashcards", e);
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error loading flashcards", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void deleteFlashcardSet(FlashcardRepository.FlashcardSetData setData) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_confirm_delete_flashcard, null);

        View btnNo = dialogView.findViewById(R.id.btnNo);
        View btnYes = dialogView.findViewById(R.id.btnYes);

        android.app.AlertDialog dialog = builder
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();

            repository.deleteFlashcardSet(setData.getId(), new OnFirestoreResultListener() {
                @Override
                public void onSuccess(String id) {
                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Flashcard set deleted", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e("FlashcardsFragment", "Error deleting flashcard set", e);
                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Error deleting flashcard set", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });

        dialog.show();
    }

    private void updateEmptyView() {
        if (setList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerViewSets.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerViewSets.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (setsListenerRegistration != null) {
            setsListenerRegistration.remove();
            setsListenerRegistration = null;
        }
    }
}