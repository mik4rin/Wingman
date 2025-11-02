package com.example.wingman;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DailyBreakdownAdapter extends RecyclerView.Adapter<DailyBreakdownAdapter.ViewHolder> {

    private List<DailyBreakdownItem> items = new ArrayList<>();

    public void setData(List<DailyBreakdownItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_breakdown, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyBreakdownItem item = items.get(position);
        holder.tvDate.setText(item.date);

        String sessionsText = item.sessions == 1 ? "1 session" : item.sessions + " sessions";
        holder.tvSessions.setText(sessionsText);

        int maxSessions = 1;
        for (DailyBreakdownItem i : items) {
            maxSessions = Math.max(maxSessions, i.sessions);
        }

        float widthPercent = (item.sessions / (float) maxSessions);

        int screenWidth = holder.progressBar.getContext().getResources().getDisplayMetrics().widthPixels;
        int maxBarWidth = (int) (screenWidth * 0.5);

        ViewGroup.LayoutParams params = holder.progressBar.getLayoutParams();
        params.width = Math.max((int) (maxBarWidth * widthPercent), 30);
        holder.progressBar.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvSessions;
        View progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSessions = itemView.findViewById(R.id.tvSessions);
            progressBar = itemView.findViewById(R.id.progressBar);
        }
    }
}