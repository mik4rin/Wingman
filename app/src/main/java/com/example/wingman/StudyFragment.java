package com.example.wingman;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.cardview.widget.CardView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Random;

public class StudyFragment extends Fragment {

    private CardView cardFlashcards, cardPomodoro;
    private TextView tvStudyTip;

    private final String[] studyTips = {
            "Take 5-minute breaks every 25 minutes",
            "Study in a quiet, well-lit environment",
            "Use active recall instead of passive reading",
            "Practice spaced repetition for better retention",
            "Create mind maps to connect concepts",
            "Teach what you learn to solidify understanding",
            "Set specific, achievable study goals daily",
            "Use the Feynman technique to simplify complex topics",
            "Stay hydrated to maintain focus and concentration",
            "Get 7-8 hours of sleep for optimal memory consolidation",
            "Exercise regularly to boost brain function",
            "Remove distractions from your study space",
            "Use flashcards for quick review sessions",
            "Study difficult subjects when you're most alert",
            "Take handwritten notes for better retention",
            "Use mnemonic devices for memorizing lists",
            "Review material within 24 hours of learning",
            "Break large tasks into smaller, manageable chunks",
            "Practice retrieval by testing yourself frequently",
            "Create a consistent study schedule and stick to it"
    };

    public StudyFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_study_, container, false);

        cardFlashcards = view.findViewById(R.id.cardFlashcards);
        cardPomodoro = view.findViewById(R.id.cardPomodoro);
        tvStudyTip = view.findViewById(R.id.tvStudyTip);

        setRandomStudyTip();

        cardFlashcards.setOnClickListener(v -> openFragment(new FlashcardsFragment()));
        cardPomodoro.setOnClickListener(v -> openFragment(new TimerFragment()));

        return view;
    }

    private void setRandomStudyTip() {
        Random random = new Random();
        int randomIndex = random.nextInt(studyTips.length);
        tvStudyTip.setText(studyTips[randomIndex]);
    }

    private void openFragment(Fragment fragment) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}