package com.example.wingman;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.wingman.data.OnFirestoreResultListener;
import com.example.wingman.data.OnFirestoreTasksListener;
import com.example.wingman.data.Task;
import com.example.wingman.data.TaskRepository;
import com.example.wingman.util.NotificationScheduler;

import java.util.Calendar;
import java.util.List;

public class AddTaskDialogFragment extends DialogFragment {
    private static final String ARG_TASK = "arg_task";
    private static final String TAG = "AddTaskDialog";

    private Task taskToEdit;
    private EditText editTextTaskName, editTextCourse, editTextDescription;
    private Switch switchDeadline;
    private Button btnSetDate, btnSetTime, btnClear, btnAdd;
    private TextView textViewDeadline, dialogTitle;
    private ImageButton btnClose;
    private View layoutDateTimePickers;

    private int selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute;
    private boolean deadlineSet = false;
    private boolean isDateSelected = false;
    private boolean isTimeSelected = false;

    private Animation shake;
    private Vibrator vibrator;

    private TaskRepository repository;

    public interface BadgeUpdateListener {
        void onBadgeUpdate();
    }
    private BadgeUpdateListener badgeUpdateListener;

    public void setBadgeUpdateListener(BadgeUpdateListener listener) {
        this.badgeUpdateListener = listener;
    }

    public AddTaskDialogFragment() {
        repository = new TaskRepository();
    }

    public static AddTaskDialogFragment newInstance(Task task) {
        AddTaskDialogFragment fragment = new AddTaskDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_TASK, task);
        fragment.setArguments(args);
        return fragment;
    }

    public void setTaskToEdit(Task task) {
        this.taskToEdit = task;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_add_task, container, false);

        editTextTaskName = view.findViewById(R.id.editTextTaskName);
        editTextCourse = view.findViewById(R.id.editTextCourse);
        editTextDescription = view.findViewById(R.id.editTextDescription);
        switchDeadline = view.findViewById(R.id.switchDeadline);
        btnSetDate = view.findViewById(R.id.btnSetDate);
        btnSetTime = view.findViewById(R.id.btnSetTime);
        btnClear = view.findViewById(R.id.btnClear);
        btnAdd = view.findViewById(R.id.btnAdd);
        textViewDeadline = view.findViewById(R.id.textViewDeadline);
        btnClose = view.findViewById(R.id.btnClose);
        layoutDateTimePickers = view.findViewById(R.id.layoutDateTimePickers);
        dialogTitle = view.findViewById(R.id.dialogTitle);

        if (taskToEdit != null) {
            dialogTitle.setText("Edit Task");
            btnAdd.setText("Save");
        } else {
            dialogTitle.setText("Add New Task");
            btnAdd.setText("Add");
        }

        shake = AnimationUtils.loadAnimation(requireContext(), R.anim.shake);
        vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        layoutDateTimePickers.setVisibility(View.GONE);
        textViewDeadline.setText("No deadline set");

        InputFilter smartNumberFilter = (source, start, end, dest, dstart, dend) -> {
            String result = dest.toString().substring(0, dstart) +
                    source.subSequence(start, end) +
                    dest.toString().substring(dend);

            if (result.matches(".*[a-zA-Z].*")) return null;
            if (result.matches("^[0-9\\s\\p{Punct}]*$") && !result.trim().isEmpty()) return "";
            return null;
        };

        setupSmartNumberWatcher();
        editTextTaskName.setFilters(new InputFilter[]{smartNumberFilter});
        editTextCourse.setFilters(new InputFilter[]{smartNumberFilter});

        TextWatcher formWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateOutsideTouchBehavior(); }
            @Override public void afterTextChanged(Editable s) {}
        };

        editTextTaskName.addTextChangedListener(formWatcher);
        editTextCourse.addTextChangedListener(formWatcher);
        blockDigitsAfterPaste(editTextTaskName);
        blockDigitsAfterPaste(editTextCourse);

        if (taskToEdit != null) {
            editTextTaskName.setText(taskToEdit.getTitle());
            editTextCourse.setText(taskToEdit.getCourse());
            editTextDescription.setText(taskToEdit.getDescription());

            long deadlineMillis = taskToEdit.getDeadline() > 0 ? taskToEdit.getDeadline() : taskToEdit.getDeadline();
            if (deadlineMillis > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(deadlineMillis);
                selectedYear = cal.get(Calendar.YEAR);
                selectedMonth = cal.get(Calendar.MONTH);
                selectedDay = cal.get(Calendar.DAY_OF_MONTH);
                selectedHour = cal.get(Calendar.HOUR_OF_DAY);
                selectedMinute = cal.get(Calendar.MINUTE);
                deadlineSet = true;
                isDateSelected = true;
                isTimeSelected = true;
                switchDeadline.setChecked(true);
                layoutDateTimePickers.setVisibility(View.VISIBLE);
                updateDeadlineText();
            }
            btnAdd.setText("Save");
        } else {
            btnAdd.setText("Add");
        }

        btnClose.setOnClickListener(v -> { if (!isFormEmpty()) showExitConfirmation(); else dismiss(); });

        switchDeadline.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutDateTimePickers.setVisibility(View.VISIBLE);
                if (taskToEdit == null || taskToEdit.getDeadline() <= 0) {
                    Calendar now = Calendar.getInstance();
                    selectedYear = now.get(Calendar.YEAR);
                    selectedMonth = now.get(Calendar.MONTH);
                    selectedDay = now.get(Calendar.DAY_OF_MONTH);
                    selectedHour = now.get(Calendar.HOUR_OF_DAY);
                    selectedMinute = now.get(Calendar.MINUTE);
                    isDateSelected = true;
                    isTimeSelected = true;
                    deadlineSet = true;
                    updateDeadlineText();
                }
            } else {
                layoutDateTimePickers.setVisibility(View.GONE);
                deadlineSet = false;
                isDateSelected = false;
                isTimeSelected = false;
                textViewDeadline.setText("No deadline set");
                textViewDeadline.setError(null);
            }

            layoutDateTimePickers.postDelayed(this::resizeDialogToContent, 200);
        });

        btnSetDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(),
                    (view1, y, m, d) -> {
                        selectedYear = y; selectedMonth = m; selectedDay = d;
                        isDateSelected = true; updateDeadlineText();
                        textViewDeadline.setError(null);
                        if (isDateSelected && isTimeSelected) deadlineSet = validateDeadline();
                    },
                    isDateSelected ? selectedYear : c.get(Calendar.YEAR),
                    isDateSelected ? selectedMonth : c.get(Calendar.MONTH),
                    isDateSelected ? selectedDay : c.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            datePickerDialog.show();
        });

        btnSetTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            TimePickerDialog timePickerDialog = new TimePickerDialog(requireContext(),
                    (view12, h, m) -> {
                        selectedHour = h; selectedMinute = m;
                        isTimeSelected = true; updateDeadlineText();
                        textViewDeadline.setError(null);
                        if (isDateSelected && isTimeSelected) deadlineSet = validateDeadline();
                    },
                    isTimeSelected ? selectedHour : c.get(Calendar.HOUR_OF_DAY),
                    isTimeSelected ? selectedMinute : c.get(Calendar.MINUTE),
                    false);
            timePickerDialog.show();
        });

        btnClear.setOnClickListener(v -> {
            editTextTaskName.setText("");
            editTextCourse.setText("");
            editTextDescription.setText("");
            switchDeadline.setChecked(false);
            deadlineSet = false; isDateSelected = false; isTimeSelected = false;
            textViewDeadline.setText("No deadline set");
            textViewDeadline.setError(null);
        });

        btnAdd.setOnClickListener(v -> handleSave());

        return view;
    }

    private void resizeDialogToContent() {
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            View rootView = dialog.findViewById(R.id.dialog_root);
            if (rootView != null) {
                int widthSpec = View.MeasureSpec.makeMeasureSpec(
                        ((ViewGroup) rootView.getParent()).getWidth(),
                        View.MeasureSpec.EXACTLY);
                int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                rootView.measure(widthSpec, heightSpec);

                int newHeight = rootView.getMeasuredHeight();
                int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.9);

                dialog.getWindow().setLayout(width, newHeight);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            taskToEdit = getArguments().getParcelable(ARG_TASK);
        }
    }

    private void handleSave() {
        editTextTaskName.setError(null);
        editTextCourse.setError(null);
        editTextDescription.setError(null);
        textViewDeadline.setError(null);

        final String taskName = editTextTaskName.getText().toString().trim();
        final String course = editTextCourse.getText().toString().trim();
        String desc = editTextDescription.getText().toString().trim();
        if (desc.isEmpty()) desc = "none";
        final String description = desc;

        if (!validateInputs(taskName, course, description)) return;

        Calendar calDeadline = null;
        if (switchDeadline.isChecked() && deadlineSet) {
            calDeadline = Calendar.getInstance();
            calDeadline.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0);
        }
        final Calendar deadline = calDeadline;

        repository.getTasks(new OnFirestoreTasksListener() {
            @Override
            public void onSuccess(List<Task> tasks) {
                boolean duplicate = false;
                String conflictId = null;
                for (Task t : tasks) {
                    if (t.getTitle() != null && t.getTitle().equalsIgnoreCase(taskName)) {
                        duplicate = true;
                        conflictId = t.getId();
                        break;
                    }
                }

                if (taskToEdit == null && duplicate) {
                    showError(editTextTaskName, "Task title already exists");
                    return;
                }
                if (taskToEdit != null && duplicate && (conflictId == null || !conflictId.equals(taskToEdit.getId()))) {
                    showError(editTextTaskName, "Another task with this title already exists");
                    return;
                }

                if (taskToEdit != null) {
                    // UPDATE existing task
                    taskToEdit.setTitle(taskName);
                    taskToEdit.setCourse(course);
                    taskToEdit.setDescription(description);
                    taskToEdit.setDeadline(deadline != null ? deadline.getTimeInMillis() : 0);

                    repository.updateTask(taskToEdit, new OnFirestoreResultListener() {
                        @Override
                        public void onSuccess(String id) {
                            requireActivity().runOnUiThread(() -> {
                                // ✅ Schedule or cancel notifications based on deadline
                                if (taskToEdit.getDeadline() > 0 && !taskToEdit.isCompleted()) {
                                    NotificationScheduler.scheduleDeadlineAlarm(
                                            requireContext(), taskToEdit);
                                    Log.d(TAG, "Scheduled notifications for updated task: " + taskToEdit.getTitle());
                                } else {
                                    NotificationScheduler.cancelDeadline(
                                            requireContext(), taskToEdit.getId());
                                    Log.d(TAG, "Cancelled notifications for task: " + taskToEdit.getId());
                                }

                                Toast.makeText(requireContext(), "Task updated", Toast.LENGTH_SHORT).show();
                                if (badgeUpdateListener != null) badgeUpdateListener.onBadgeUpdate();
                                dismiss();
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "Error updating", e);
                            Toast.makeText(requireContext(), "Error updating task", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    // ADD new task
                    Task newTask = new Task();
                    newTask.setTitle(taskName);
                    newTask.setCourse(course);
                    newTask.setDescription(description);
                    newTask.setDeadline(deadline != null ? deadline.getTimeInMillis() : 0);
                    newTask.setCompleted(false);

                    repository.addTask(newTask, new OnFirestoreResultListener() {
                        @Override
                        public void onSuccess(String id) {
                            requireActivity().runOnUiThread(() -> {
                                // ✅ Set the ID returned from Firestore
                                newTask.setId(id);

                                // ✅ Schedule notifications if deadline is set
                                if (newTask.getDeadline() > 0) {
                                    NotificationScheduler.scheduleDeadlineAlarm(
                                            requireContext(), newTask);
                                    Log.d(TAG, "Scheduled notifications for new task: " + newTask.getTitle());
                                }

                                Toast.makeText(requireContext(), "Task added", Toast.LENGTH_SHORT).show();
                                if (badgeUpdateListener != null) badgeUpdateListener.onBadgeUpdate();
                                dismiss();
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "Error adding", e);
                            Toast.makeText(requireContext(), "Error adding task", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error checking duplicates", e);
                Toast.makeText(requireContext(), "Error validating task", Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validateInputs(String taskName, String course, String description) {
        if (taskName.isEmpty()) return showError(editTextTaskName, "Task name is required");
        if (taskName.length() < 3) return showError(editTextTaskName, "Task name must be at least 3 characters");
        if (taskName.length() > 50) return showError(editTextTaskName, "Task name cannot exceed 50 characters");
        if (taskName.matches("^[0-9\\s\\p{Punct}]*$")) return showError(editTextTaskName, "Task name must contain at least one letter");
        if (taskName.startsWith(" ") || taskName.endsWith(" ")) return showError(editTextTaskName, "Task name cannot start or end with spaces");

        if (course.isEmpty()) return showError(editTextCourse, "Course cannot be empty");
        if (course.length() < 2) return showError(editTextCourse, "Course name must be at least 2 characters");
        if (course.length() > 30) return showError(editTextCourse, "Course name cannot exceed 30 characters");
        if (course.matches("^[0-9\\s\\p{Punct}]*$")) return showError(editTextCourse, "Course name must contain at least one letter");
        if (course.startsWith(" ") || course.endsWith(" ")) return showError(editTextCourse, "Course cannot start or end with spaces");

        if (!description.equals("none")) {
            if (description.length() < 6) return showError(editTextDescription, "Description must be at least 6 characters");
            if (description.length() > 200) return showError(editTextDescription, "Description cannot exceed 200 characters");
            if (description.startsWith(" ") || description.endsWith(" ")) return showError(editTextDescription, "Description cannot start or end with spaces");
        }

        if (switchDeadline.isChecked() && (!isDateSelected || !isTimeSelected || !validateDeadline())) {
            textViewDeadline.startAnimation(shake); vibrateOnError();
            Toast.makeText(requireContext(), "Please set a valid deadline", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private boolean showError(EditText field, String msg) {
        field.setError(msg);
        field.startAnimation(shake);
        field.requestFocus();
        vibrateOnError();
        return false;
    }

    private void vibrateOnError() {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else vibrator.vibrate(200);
        }
    }

    private void updateDeadlineText() {
        textViewDeadline.setText("Deadline: " + (selectedMonth+1) + "/" + selectedDay + "/" + selectedYear +
                " " + String.format("%02d:%02d", selectedHour, selectedMinute));
    }

    private boolean validateDeadline() {
        if (!switchDeadline.isChecked()) return true;
        if (!isDateSelected || !isTimeSelected) return false;

        Calendar selected = Calendar.getInstance();
        selected.set(selectedYear, selectedMonth, selectedDay, selectedHour, selectedMinute, 0);
        return !selected.before(Calendar.getInstance());
    }

    private void setupSmartNumberWatcher() {
        editTextTaskName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().matches("^[0-9\\s\\p{Punct}]*$") && !s.toString().trim().isEmpty()) {
                    Toast.makeText(requireContext(), "Task name must contain letters", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void blockDigitsAfterPaste(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String current = s.toString();
                if (current.matches("^[0-9\\s\\p{Punct}]*$") && !current.trim().isEmpty()) {
                    editText.setText("");
                }
            }
        });
    }

    private boolean isFormEmpty() {
        return TextUtils.isEmpty(editTextTaskName.getText().toString().trim())
                && TextUtils.isEmpty(editTextCourse.getText().toString().trim())
                && !switchDeadline.isChecked();
    }

    private void updateOutsideTouchBehavior() {
        if (getDialog() != null) getDialog().setCanceledOnTouchOutside(isFormEmpty());
    }

    private void showExitConfirmation() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unsaved_changes, null);

        AlertDialog exitDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView title = dialogView.findViewById(R.id.dialogTitleText);
        TextView message = dialogView.findViewById(R.id.dialogMessageText);
        androidx.appcompat.widget.AppCompatButton noBtn = dialogView.findViewById(R.id.buttonNo);
        androidx.appcompat.widget.AppCompatButton yesBtn = dialogView.findViewById(R.id.btnYes);

        title.setText("Discard Changes?");
        message.setText("You have unsaved changes. Are you sure you want to discard them?");

        noBtn.setText("No");
        noBtn.setOnClickListener(v -> exitDialog.dismiss());

        yesBtn.setText("Yes");
        yesBtn.setOnClickListener(v -> {
            exitDialog.dismiss();
            if (isAdded()) dismiss();
        });

        exitDialog.show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.9);
            getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}