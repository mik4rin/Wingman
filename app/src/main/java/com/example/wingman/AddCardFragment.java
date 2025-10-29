package com.example.wingman;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.FlashcardRepository;
import com.example.wingman.data.OnFirestoreResultListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class AddCardFragment extends Fragment implements FlashcardAdapter.OnStartDragListener {

    private FlashcardAdapter adapter;
    private List<FlashcardItem> cardList;
    private ItemTouchHelper itemTouchHelper;
    private RecyclerView recyclerView;
    private Button btnAddCard, btnCreate;
    private TextInputEditText etTitle, etDescription;
    private FlashcardRepository repository;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_addcard, container, false);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideAppBars();
        }

        // Initialize repository
        repository = new FlashcardRepository();

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        recyclerView = view.findViewById(R.id.recyclerCards);
        btnAddCard = view.findViewById(R.id.btnAddCard);
        btnCreate = view.findViewById(R.id.btnCreate);
        etTitle = view.findViewById(R.id.etTitle);
        etDescription = view.findViewById(R.id.etDescription);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        cardList = new ArrayList<>();
        cardList.add(new FlashcardItem("", ""));
        cardList.add(new FlashcardItem("", ""));

        adapter = new FlashcardAdapter(cardList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new FlashcardItemTouchHelper(adapter);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        btnAddCard.setOnClickListener(v -> {
            cardList.add(new FlashcardItem("", ""));
            adapter.notifyItemInserted(cardList.size() - 1);
            recyclerView.post(() -> recyclerView.smoothScrollToPosition(cardList.size() - 1));
        });

        btnCreate.setOnClickListener(v -> {
            createFlashcardSet();
        });

        return view;
    }

    private void createFlashcardSet() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String description = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";

        // Validation
        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        // Filter out empty cards
        List<FlashcardItem> validCards = new ArrayList<>();
        for (FlashcardItem item : cardList) {
            String question = item.getQuestion() != null ? item.getQuestion().trim() : "";
            String answer = item.getAnswer() != null ? item.getAnswer().trim() : "";

            if (!question.isEmpty() && !answer.isEmpty()) {
                validCards.add(item);
            }
        }

        if (validCards.isEmpty()) {
            Toast.makeText(getContext(), "Please add at least one card with question and answer", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId == null) {
            Toast.makeText(getContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double-click
        btnCreate.setEnabled(false);

        // Save to Firestore
        repository.createFlashcardSet(currentUserId, title, description, validCards, new OnFirestoreResultListener() {
            @Override
            public void onSuccess(String setId) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Flashcard set created!", Toast.LENGTH_SHORT).show();

                        // Navigate back to FlashcardsFragment
                        requireActivity().getSupportFragmentManager().popBackStack();
                        btnCreate.setEnabled(true);
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Error creating flashcard set: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e("AddCardFragment", "Error creating flashcard set", e);
                        btnCreate.setEnabled(true);
                    });
                }
            }
        });
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }
}