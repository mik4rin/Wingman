package com.example.wingman;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.wingman.data.Task;

import java.util.List;

public class TaskAdapter extends ArrayAdapter<Task> {

    public interface TaskListener {
        void onTaskClicked(Task task);
        void onTaskLongPressed(Task task);
        void onTaskCheckedChanged(Task task, boolean checked);
    }

    private static final String TAG = "TaskAdapter";
    private final TaskListener listener;
    private final LayoutInflater inflater;

    public TaskAdapter(@NonNull Context context, @NonNull List<Task> tasks, TaskListener listener) {
        super(context, 0, tasks);
        this.listener = listener;
        this.inflater = LayoutInflater.from(context);
    }

    private static class VH {
        TextView title;
        TextView deadline;
        CheckBox checkbox;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final Task task = getItem(position);
        final VH vh;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_task, parent, false);
            vh = new VH();
            vh.title = convertView.findViewById(R.id.textViewTitle);
            vh.deadline = convertView.findViewById(R.id.textViewDeadline);
            vh.checkbox = convertView.findViewById(R.id.checkBoxComplete);
            convertView.setTag(vh);
        } else {
            vh = (VH) convertView.getTag();
        }

        if (task == null) return convertView;

        if (vh.title != null) {
            vh.title.setText(task.getTitle() != null ? task.getTitle() : "Untitled");
        } else {
            Log.w(TAG, "title TextView is null in item layout");
        }

        boolean isOverdue = false;
        long dl = task.getDeadline();
        if (vh.deadline != null) {
            if (dl > 0) {
                String date = android.text.format.DateFormat.getDateFormat(getContext()).format(dl);
                String time = android.text.format.DateFormat.getTimeFormat(getContext()).format(dl);
                isOverdue = !task.isCompleted() && System.currentTimeMillis() > dl;
                if (isOverdue) {
                    vh.deadline.setText("Overdue - " + date + " " + time);
                    vh.deadline.setVisibility(View.VISIBLE);
                } else {
                    vh.deadline.setText(date + " " + time);
                    vh.deadline.setVisibility(View.VISIBLE);
                }
            } else {
                vh.deadline.setText("No deadline");
                vh.deadline.setVisibility(View.GONE);
            }
        }

        if (vh.checkbox != null) {
            vh.checkbox.setOnCheckedChangeListener(null);
            vh.checkbox.setChecked(task.isCompleted());
            try {
                vh.checkbox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                        androidx.core.content.ContextCompat.getColor(getContext(), R.color.gold)
                ));
            } catch (Exception e) {
                Log.e(TAG, "Failed to set checkbox tint", e);
            }
            vh.checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) listener.onTaskCheckedChanged(task, isChecked);
            });
        } else {
            Log.w(TAG, "checkbox not found in item layout");
        }

        int colorBlue;
        try {
            colorBlue = ContextCompat.getColor(getContext(), R.color.blue);
        } catch (Exception e) {
            colorBlue = ContextCompat.getColor(getContext(), android.R.color.holo_blue_dark);
        }
        int colorOverdue = ContextCompat.getColor(getContext(), android.R.color.holo_red_light);

        if (isOverdue) {
            if (vh.title != null) vh.title.setTextColor(colorOverdue);
            if (vh.deadline != null && vh.deadline.getVisibility() == View.VISIBLE) vh.deadline.setTextColor(colorOverdue);
        } else {
            if (vh.title != null) vh.title.setTextColor(colorBlue);
            if (vh.deadline != null && vh.deadline.getVisibility() == View.VISIBLE) vh.deadline.setTextColor(colorBlue);
        }

        convertView.setOnClickListener(v -> {
            if (listener != null) listener.onTaskClicked(task);
        });

        convertView.setOnLongClickListener(v -> {
            if (listener != null) listener.onTaskLongPressed(task);
            return true;
        });

        return convertView;
    }
}