package com.example.wingman;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.wingman.data.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class TaskDetailsDialogFragment extends DialogFragment {

    private static final String ARG_TASK = "task_arg";
    private Task task;

    public static TaskDetailsDialogFragment newInstance(Task task) {
        TaskDetailsDialogFragment fragment = new TaskDetailsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TASK, task);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            task = getArguments().getParcelable(ARG_TASK);
        }
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog_NoActionBar);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.task_details_layout, container, false);

        ImageView closeButton = view.findViewById(R.id.closeButton);
        TextView taskTitle = view.findViewById(R.id.task_title);
        TextView taskCourse = view.findViewById(R.id.task_course);
        TextView taskDeadline = view.findViewById(R.id.task_deadline);
        TextView taskDescription = view.findViewById(R.id.task_description);
        Button editButton = view.findViewById(R.id.edit_btn);
        Button deleteButton = view.findViewById(R.id.delete_btn);

        if (task != null) {
            taskTitle.setText(task.getTitle() != null ? task.getTitle() : "Untitled");
            taskCourse.setText(task.getCourse() != null ? task.getCourse() : "");
            taskDescription.setText(task.getDescription() != null ? task.getDescription() : "");
            long deadlineMillis = task.getDeadline();
            if (deadlineMillis > 0) {
                try {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(deadlineMillis);
                    String deadlineStr = new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(calendar.getTime());
                    taskDeadline.setText(deadlineStr);
                } catch (Exception e) {
                    taskDeadline.setText("Invalid deadline");
                }
            } else {
                taskDeadline.setText("No deadline");
            }
        }

        closeButton.setOnClickListener(v -> dismiss());

        editButton.setOnClickListener(v -> {
            if (listener != null) listener.onTaskEditRequested(task);
            dismiss();
        });

        deleteButton.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_unsaved_changes, null);

            androidx.appcompat.app.AlertDialog deleteDialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            TextView title = dialogView.findViewById(R.id.dialogTitleText);
            TextView message = dialogView.findViewById(R.id.dialogMessageText);
            androidx.appcompat.widget.AppCompatButton cancelBtn = dialogView.findViewById(R.id.buttonNo);
            androidx.appcompat.widget.AppCompatButton deleteBtn = dialogView.findViewById(R.id.btnYes);

            title.setText("Delete Task?");
            message.setText("Are you sure you want to delete this task?");

            cancelBtn.setText("Cancel");
            deleteBtn.setText("Delete");

            cancelBtn.setOnClickListener(btn -> deleteDialog.dismiss());

            deleteBtn.setOnClickListener(btn -> {
                deleteDialog.dismiss();
                if (listener != null) listener.onTaskDeleted(task);
                dismiss();
            });

            deleteDialog.show();
        });


        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            Window window = dialog.getWindow();
            if (window != null) {
                int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.9);
                window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }


    public interface TaskDetailsListener {
        void onTaskDeleted(Task task);
        void onTaskEditRequested(Task task);
    }

    private TaskDetailsListener listener;

    public void setListener(TaskDetailsListener listener) {
        this.listener = listener;
    }
}