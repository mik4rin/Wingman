package com.example.wingman;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NewScheduleDialogFragment extends DialogFragment {

    private static final String ARG_SCHEDULE = "arg_schedule";

    private View closeButton;
    private TextView dialogTitle;

    public interface ScheduleListener {
        void onScheduleSaved(String id, String title, String description, String date, String schedType);
        void onScheduleDeleted(String id);
    }

    private ScheduleListener listener;
    private NewSchedule editSchedule = null;

    private TextInputEditText titleInput;
    private TextInputEditText descInput;
    private MaterialAutoCompleteTextView typeInput;
    private TextView dateText;
    private MaterialButton dateButton;
    private MaterialButton saveButton;
    private MaterialButton deleteButton;
    private Calendar selectedDate = Calendar.getInstance();

    private boolean changesPending = false;

    public NewScheduleDialogFragment() { /* default */ }

    public static NewScheduleDialogFragment newInstance(@Nullable NewSchedule schedule) {
        NewScheduleDialogFragment f = new NewScheduleDialogFragment();
        if (schedule != null) {
            Bundle args = new Bundle();
            args.putString(ARG_SCHEDULE, schedule.getId());
            f.setArguments(args);
            f.setSchedule(schedule);
        }
        return f;
    }

    public void setScheduleListener(ScheduleListener listener) {
        this.listener = listener;
    }

    public void setSchedule(NewSchedule schedule) {
        this.editSchedule = schedule;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);  // prevent accidental dismiss
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                handleUnsavedChangesBeforeClose();
                return true; // consume
            }
            return false;
        });

        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.dialog_add_sched, container, false);

        dialogTitle = view.findViewById(R.id.dialog_title);
        titleInput = view.findViewById(R.id.new_sched_title);
        descInput = view.findViewById(R.id.new_sched_desc);
        typeInput = view.findViewById(R.id.new_sched_type);
        dateText = view.findViewById(R.id.selected_date_text);
        dateButton = view.findViewById(R.id.select_date_button);
        saveButton = view.findViewById(R.id.new_sched_add);
        deleteButton = view.findViewById(R.id.new_sched_del);
        closeButton = view.findViewById(R.id.close_btn);

        String[] schedTypes = new String[]{"Deadline", "Work", "Activity", "Birthday",
                "Anniversary", "Holiday", "Seasonal", "Gathering","Appointment", "Personal", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, schedTypes);
        typeInput.setAdapter(adapter);

        typeInput.setOnClickListener(v -> typeInput.showDropDown());
        typeInput.setOnItemClickListener((parent, view1, position, id) -> changesPending = true);

        if (editSchedule != null) {
            populateFieldsForEdit();
        } else {
            dialogTitle.setText("Add New Schedule");
            updateDateText();
            deleteButton.setVisibility(View.INVISIBLE);
            saveButton.setText("Add");
        }

        titleInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changesPending = true; }
            @Override public void afterTextChanged(Editable s) {}
        });

        descInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changesPending = true; }
            @Override public void afterTextChanged(Editable s) {}
        });

        dateButton.setOnClickListener(v -> showDatePicker());
        closeButton.setOnClickListener(v -> handleUnsavedChangesBeforeClose());

        saveButton.setOnClickListener(v -> {
            if (saveSchedule()) {
                if (isAdded()) dismiss();
            }
        });

        saveButton.setOnClickListener(v -> {
            if (saveSchedule()) {
                if (isAdded()) dismiss();
            }
        });

        deleteButton.setOnClickListener(v -> showDeleteConfirmation());

        return view;

    }

    private void showDeleteConfirmation() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unsaved_changes, null);

        AlertDialog deleteDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView title = dialogView.findViewById(R.id.dialogTitleText);
        TextView message = dialogView.findViewById(R.id.dialogMessageText);
        androidx.appcompat.widget.AppCompatButton noBtn = dialogView.findViewById(R.id.buttonNo);
        androidx.appcompat.widget.AppCompatButton yesBtn = dialogView.findViewById(R.id.btnYes);

        title.setText("Delete Schedule?");
        message.setText("Are you sure you want to delete this schedule?");

        noBtn.setText("Cancel");
        noBtn.setOnClickListener(v -> deleteDialog.dismiss());

        yesBtn.setText("Delete");
        yesBtn.setOnClickListener(v -> {
            deleteDialog.dismiss();
            if (editSchedule != null && listener != null) {
                listener.onScheduleDeleted(editSchedule.getId());
                Toast.makeText(requireContext(), "Schedule deleted.", Toast.LENGTH_SHORT).show();
            }
            if (isAdded()) dismiss();
        });

        deleteDialog.show();
    }


    private void populateFieldsForEdit() {
        if (dialogTitle != null) dialogTitle.setText("Edit Schedule");
        titleInput.setText(editSchedule.getTitle());
        descInput.setText(editSchedule.getDescription());
        typeInput.setText(editSchedule.getSchedType(), false);

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date parsed = sdf.parse(editSchedule.getDate());
            if (parsed != null) selectedDate.setTime(parsed);
        } catch (Exception e) {
            selectedDate = Calendar.getInstance();
        }

        updateDateText();
        saveButton.setText("Save");
        deleteButton.setVisibility(View.VISIBLE);
    }

    private void showDatePicker() {
        int year = selectedDate.get(Calendar.YEAR);
        int month = selectedDate.get(Calendar.MONTH);
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog picker = new DatePickerDialog(requireContext(), (DatePicker view, int y, int m, int d) -> {
            selectedDate.set(Calendar.YEAR, y);
            selectedDate.set(Calendar.MONTH, m);
            selectedDate.set(Calendar.DAY_OF_MONTH, d);
            updateDateText();
            changesPending = true;
        }, year, month, day);

        picker.show();
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dateText.setText(sdf.format(selectedDate.getTime()));
    }

    private boolean saveSchedule() {
        if (!isAdded() || getContext() == null) return false;

        String title = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String desc = descInput.getText() != null ? descInput.getText().toString().trim() : "";
        String type = typeInput.getText() != null ? typeInput.getText().toString().trim() : "";
        String date = dateText.getText() != null ? dateText.getText().toString() : "";

        if (TextUtils.isEmpty(title)) {
            titleInput.setError("Title cannot be empty");
            return false;
        }

        if (!TextUtils.isEmpty(date)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date sel = sdf.parse(date);
                Date currentDate = new Date();

                Calendar cal = Calendar.getInstance();
                cal.setTime(currentDate);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                Date todayStart = cal.getTime();

                if (sel != null && sel.before(todayStart)) {
                    Toast.makeText(requireContext(), "Date cannot be in the past", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (ParseException e) {
                Toast.makeText(requireContext(), "Invalid date format", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        String id = (editSchedule != null) ? editSchedule.getId() : null;
        if (listener != null) {
            listener.onScheduleSaved(id, title, desc, date, type);
            Toast.makeText(requireContext(),
                    id == null ? "Schedule added successfully" : "Schedule updated successfully",
                    Toast.LENGTH_SHORT).show();
        }

        changesPending = false;
        return true;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        handleUnsavedChangesBeforeClose();
    }

    @Override
    public void dismiss() {
        if (changesPending) {
            handleUnsavedChangesBeforeClose();
        } else {
            super.dismiss();
        }
    }

    private void handleUnsavedChangesBeforeClose() {
        if (!changesPending) {
            if (isAdded()) super.dismiss();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unsaved_changes, null);

        AlertDialog unsavedDialog = new AlertDialog.Builder(requireContext())
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
        noBtn.setOnClickListener(v -> unsavedDialog.dismiss());

        yesBtn.setText("Yes");
        yesBtn.setOnClickListener(v -> {
            unsavedDialog.dismiss();
            if (isAdded()) NewScheduleDialogFragment.super.dismiss();
        });

        unsavedDialog.show();
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