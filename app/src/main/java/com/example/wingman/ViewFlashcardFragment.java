package com.example.wingman;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class ViewFlashcardFragment extends Fragment {

    private FrameLayout cardContainer;
    private TextView tvCardText, tvCardLabel, tvTitle, tvDescription, tvCardCount, tvCurrentIndex;
    private ImageView btnNext, btnPrev, btnClose;

    private List<FlashcardItem> flashcards;
    private int currentIndex = 0;
    private boolean showingAnswer = false;

    private String setTitle = "Flashcard Set";
    private String setDescription = "Review your terms";

    public static ViewFlashcardFragment newInstance(List<FlashcardItem> cards, String title, String description) {
        ViewFlashcardFragment fragment = new ViewFlashcardFragment();
        Bundle args = new Bundle();
        args.putSerializable("cards", (java.io.Serializable) cards);
        args.putString("title", title);
        args.putString("description", description);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preview_flashcard, container, false);

        if (getArguments() != null) {
            flashcards = (List<FlashcardItem>) getArguments().getSerializable("cards");
            setTitle = getArguments().getString("title", "Flashcard Set");
            setDescription = getArguments().getString("description", "Review your terms");
        }

        tvTitle = view.findViewById(R.id.tvTitle);
        tvDescription = view.findViewById(R.id.tvDescription);
        tvCardText = view.findViewById(R.id.tvCardText);
        tvCardLabel = view.findViewById(R.id.tvCardLabel);
        tvCardCount = view.findViewById(R.id.tvCardCount);
        tvCurrentIndex = view.findViewById(R.id.tvCurrentIndex);
        cardContainer = view.findViewById(R.id.cardContainer);
        btnNext = view.findViewById(R.id.btnNext);
        btnPrev = view.findViewById(R.id.btnPrev);
        btnClose = view.findViewById(R.id.btnClose);

        updateCardUI();

        cardContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();
                float cardWidth = cardContainer.getWidth();
                boolean flipRight = touchX > cardWidth / 2;
                flipCard(flipRight);
                return true;
            }
            return false;
        });

        btnNext.setOnClickListener(v -> {
            if (flashcards != null && currentIndex < flashcards.size() - 1) {
                currentIndex++;
                showingAnswer = false;
                updateCardUI();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (flashcards != null && currentIndex > 0) {
                currentIndex--;
                showingAnswer = false;
                updateCardUI();
            }
        });

        btnClose.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        return view;
    }

    private void flipCard(boolean flipRight) {
        if (flashcards == null || flashcards.isEmpty()) return;

        cardContainer.setEnabled(false);

        float startRotation = 0f;
        float midRotation = flipRight ? 90f : -90f;
        float endRotation = flipRight ? -90f : 90f;
        float finalRotation = 0f;

        ObjectAnimator flipOut = ObjectAnimator.ofFloat(cardContainer, "rotationY", startRotation, midRotation);
        flipOut.setDuration(200);
        flipOut.setInterpolator(new AccelerateDecelerateInterpolator());

        flipOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                showingAnswer = !showingAnswer;
                updateCardText();
                updateCardLabel();

                ObjectAnimator flipIn = ObjectAnimator.ofFloat(cardContainer, "rotationY", endRotation, finalRotation);
                flipIn.setDuration(200);
                flipIn.setInterpolator(new AccelerateDecelerateInterpolator());

                flipIn.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        cardContainer.setEnabled(true);
                    }
                });

                flipIn.start();
            }
        });

        flipOut.start();
    }

    private void updateCardUI() {
        if (flashcards == null || flashcards.isEmpty()) {
            tvCardText.setText("No cards available");
            return;
        }

        tvTitle.setText(setTitle);
        tvDescription.setText(setDescription);
        tvCardCount.setText((currentIndex + 1) + "/" + flashcards.size());
        tvCurrentIndex.setText(String.valueOf(currentIndex + 1));

        updateCardText();
        updateCardLabel();
    }

    private void updateCardText() {
        if (flashcards == null || flashcards.isEmpty()) return;

        FlashcardItem item = flashcards.get(currentIndex);
        String textToShow = showingAnswer ? item.getAnswer() : item.getQuestion();
        if (textToShow == null || textToShow.trim().isEmpty()) {
            textToShow = showingAnswer ? "No Answer" : "No Question";
        }
        tvCardText.setText(textToShow);
    }

    private void updateCardLabel() {
        tvCardLabel.setText(showingAnswer ? "Answer" : "Question");
    }
}