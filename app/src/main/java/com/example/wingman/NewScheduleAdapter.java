package com.example.wingman;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NewScheduleAdapter extends RecyclerView.Adapter<NewScheduleAdapter.ScheduleViewHolder> {

    private final List<NewSchedule> items;
    private final List<NewSchedule> fullList;
    private final LayoutInflater inflater;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(NewSchedule item, int position);
    }

    public NewScheduleAdapter(Context ctx, List<NewSchedule> items, OnItemClickListener listener) {
        this.items = new ArrayList<>(items);
        this.fullList = new ArrayList<>(items);
        this.inflater = LayoutInflater.from(ctx);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_new_sched, parent, false);
        return new ScheduleViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        NewSchedule s = items.get(position);
        holder.titleText.setText(s.getTitle());
        holder.dateText.setText(s.getDate());
        holder.descText.setText(s.getDescription());
        holder.typeText.setText(s.getSchedType());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(s, position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, dateText, descText, typeText;
        ScheduleViewHolder(View iv) {
            super(iv);
            titleText = iv.findViewById(R.id.item_sched_title);
            dateText = iv.findViewById(R.id.item_sched_date);
            descText = iv.findViewById(R.id.item_sched_desc);
            typeText = iv.findViewById(R.id.item_sched_type);
        }
    }

    // Sorting
    public void sortByTitleAsc() { Collections.sort(items, Comparator.comparing(NewSchedule::getTitle)); notifyDataSetChanged(); }
    public void sortByTitleDesc() { Collections.sort(items, Comparator.comparing(NewSchedule::getTitle).reversed()); notifyDataSetChanged(); }
    public void sortByDateAsc() { Collections.sort(items, Comparator.comparing(NewSchedule::getParsedDate)); notifyDataSetChanged(); }
    public void sortByDateDesc() { Collections.sort(items, Comparator.comparing(NewSchedule::getParsedDate).reversed()); notifyDataSetChanged(); }

    // Filtering
    public void filter(String query) {
        items.clear();
        if (query == null || query.trim().isEmpty()) {
            items.addAll(fullList);
        } else {
            String lower = query.toLowerCase().trim();

            for (NewSchedule s : fullList) {
                // Check if the query starts with '@' to search schedType specifically
                if (lower.startsWith("@")) {
                    String keyword = lower.substring(1);
                    if (s.getSchedType().toLowerCase().contains(keyword)) {
                        items.add(s);
                    }
                } else {
                    // General search
                    if (s.getTitle().toLowerCase().contains(lower)
                            || s.getDescription().toLowerCase().contains(lower)
                            || s.getDate().toLowerCase().contains(lower)) {
                        items.add(s);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    public void updateData(List<NewSchedule> newSchedules) {
        fullList.clear(); fullList.addAll(newSchedules);
        items.clear(); items.addAll(newSchedules);
        notifyDataSetChanged();
    }

    public void addSchedule(NewSchedule s) {
        if (s == null || s.getId() == null) return;
        for (NewSchedule item : fullList) {
            if (s.getId().equals(item.getId())) return;
        }
        fullList.add(0, s);
        items.add(0, s);
        notifyItemInserted(0);
    }


    public void removeScheduleById(String id) {
        int indexInItems = -1;
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).getId())) {
                indexInItems = i;
                break;
            }
        }
        if (indexInItems != -1) {
            NewSchedule removed = items.remove(indexInItems);
            int indexInFullList = -1;
            for (int i = 0; i < fullList.size(); i++) {
                if (id.equals(fullList.get(i).getId())) {
                    indexInFullList = i;
                    break;
                }
            }
            if (indexInFullList != -1) {
                fullList.remove(indexInFullList);
            }
            notifyItemRemoved(indexInItems);
        }
    }
}