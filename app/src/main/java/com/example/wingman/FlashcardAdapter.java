package com.example.wingman;

import android.annotation.SuppressLint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

public class FlashcardAdapter extends RecyclerView.Adapter<FlashcardAdapter.CardViewHolder> {

    private final List<FlashcardItem> cardList;
    private final OnStartDragListener dragListener;

    public interface OnStartDragListener {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    public FlashcardAdapter(List<FlashcardItem> cardList, OnStartDragListener dragListener) {
        this.cardList = cardList;
        this.dragListener = dragListener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard, parent, false);
        return new CardViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        holder.bind(cardList.get(position), position);

        holder.ivReorder.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null) {
                dragListener.onStartDrag(holder);
            }
            return false;
        });


        holder.ivDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                cardList.remove(currentPos);
                notifyItemRemoved(currentPos);
                notifyItemRangeChanged(currentPos, cardList.size());
            }
        });
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }


    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < cardList.size() && toPosition < cardList.size()) {
            Collections.swap(cardList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);

            int start = Math.min(fromPosition, toPosition);
            int end = Math.max(fromPosition, toPosition);
            notifyItemRangeChanged(start, end - start + 1);
        }
    }

    public List<FlashcardItem> getCardList() {
        return cardList;
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView tvNumber;
        EditText etQuestion, etAnswer;
        ImageView ivReorder, ivDelete;

        private TextWatcher questWatcher, ansWatcher;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            etQuestion = itemView.findViewById(R.id.etQuestion);
            etAnswer = itemView.findViewById(R.id.etAnswer);
            ivReorder = itemView.findViewById(R.id.ivReorder);
            ivDelete = itemView.findViewById(R.id.ivDelete);
        }

        void bind(FlashcardItem item, int position) {
            tvNumber.setText(String.valueOf(position + 1));

            if (questWatcher != null) etQuestion.removeTextChangedListener(questWatcher);
            if (ansWatcher != null) etAnswer.removeTextChangedListener(ansWatcher);

            etQuestion.setText(item.getQuestion());
            etAnswer.setText(item.getAnswer());

            questWatcher = new SimpleTextWatcher(text -> item.setQuestion(text));
            ansWatcher = new SimpleTextWatcher(text -> item.setAnswer(text));

            etQuestion.addTextChangedListener(questWatcher);
            etAnswer.addTextChangedListener(ansWatcher);
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final OnTextChangedListener listener;

        SimpleTextWatcher(OnTextChangedListener listener) {
            this.listener = listener;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {
            listener.onTextChanged(s.toString());
        }
    }

    private interface OnTextChangedListener {
        void onTextChanged(String text);
    }
}
