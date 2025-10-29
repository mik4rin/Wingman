package com.example.wingman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.FlashcardRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FlashcardSetAdapter extends RecyclerView.Adapter<FlashcardSetAdapter.SetViewHolder> {

    private final List<FlashcardRepository.FlashcardSetData> setList;
    private final FlashcardSetListener listener;

    public interface FlashcardSetListener {
        void onSetClicked(FlashcardRepository.FlashcardSetData setData);
        void onSetDeleted(FlashcardRepository.FlashcardSetData setData);
    }

    public FlashcardSetAdapter(List<FlashcardRepository.FlashcardSetData> setList, FlashcardSetListener listener) {
        this.setList = setList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_flashcard_set, parent, false);
        return new SetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
        FlashcardRepository.FlashcardSetData setData = setList.get(position);
        holder.bind(setData);
    }

    @Override
    public int getItemCount() {
        return setList.size();
    }

    class SetViewHolder extends RecyclerView.ViewHolder {
        TextView tvSetName, tvSetDescription, tvCardCount, tvCreatedDate;
        ImageButton btnDelete;

        SetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSetName = itemView.findViewById(R.id.tvSetName);
            tvSetDescription = itemView.findViewById(R.id.tvSetDescription);
            tvCardCount = itemView.findViewById(R.id.tvCardCount);
            tvCreatedDate = itemView.findViewById(R.id.tvCreatedDate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(FlashcardRepository.FlashcardSetData setData) {
            tvSetName.setText(setData.getName());

            if (setData.getDescription() != null && !setData.getDescription().isEmpty()) {
                tvSetDescription.setText(setData.getDescription());
                tvSetDescription.setVisibility(View.VISIBLE);
            } else {
                tvSetDescription.setVisibility(View.GONE);
            }

            tvCardCount.setText(setData.getCardCount() + " cards");

            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            tvCreatedDate.setText("Created: " + dateFormat.format(new Date(setData.getCreatedAt())));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSetClicked(setData);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSetDeleted(setData);
                }
            });
        }
    }
}