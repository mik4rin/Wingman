package com.example.wingman;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewScheduleListAdapter extends BaseAdapter {

    private final Context context;
    private final ArrayList<NewSchedule> scheduleList;

    public NewScheduleListAdapter(Context context, List<NewSchedule> scheduleList) {
        this.context = context;
        this.scheduleList = new ArrayList<>();
        if (scheduleList != null) this.scheduleList.addAll(scheduleList);
    }

    public void updateList(List<NewSchedule> newList) {
        scheduleList.clear();
        if (newList != null) scheduleList.addAll(newList);
        notifyDataSetChanged();
    }

    public boolean addIfNotExists(NewSchedule s) {
        if (s == null || s.getId() == null) return false;
        for (NewSchedule n : scheduleList) {
            if (s.getId().equals(n.getId())) return false;
        }
        scheduleList.add(0, s);
        notifyDataSetChanged();
        return true;
    }

    public void replaceOrAdd(NewSchedule s) {
        if (s == null || s.getId() == null) return;
        int idx = -1;
        for (int i = 0; i < scheduleList.size(); i++) {
            if (s.getId().equals(scheduleList.get(i).getId())) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            scheduleList.set(idx, s);
            notifyDataSetChanged();
        } else {
            scheduleList.add(0, s);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return scheduleList.size();
    }

    @Override
    public Object getItem(int position) {
        return (position >= 0 && position < scheduleList.size()) ? scheduleList.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    static class ViewHolder {
        TextView titleView, typeView, descView, dateView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_new_sched, parent, false);
            holder = new ViewHolder();
            holder.titleView = convertView.findViewById(R.id.item_sched_title);
            holder.typeView = convertView.findViewById(R.id.item_sched_type);
            holder.descView = convertView.findViewById(R.id.item_sched_desc);
            holder.dateView = convertView.findViewById(R.id.item_sched_date);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        NewSchedule schedule = scheduleList.get(position);
        holder.titleView.setText(schedule.getTitle() != null ? schedule.getTitle() : "");
        holder.typeView.setText(schedule.getSchedType() != null ? schedule.getSchedType() : "");
        holder.descView.setText(schedule.getDescription() != null ? schedule.getDescription() : "");
        holder.dateView.setText(formatDate(schedule.getDate()));

        return convertView;
    }

    private String formatDate(String rawDate) {
        try {
            SimpleDateFormat src = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date d = src.parse(rawDate);
            SimpleDateFormat dest = new SimpleDateFormat("'On:' MMM dd, yyyy", Locale.getDefault());
            return dest.format(d);
        } catch (Exception e) {
            return "On: Unknown Date";
        }
    }
}