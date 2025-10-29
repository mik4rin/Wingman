package com.example.wingman;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.HashMap;
import java.util.Map;

public class ScheduleClassDialog extends DialogFragment {

    private String defaultOutlineCol = "#000000";
    private String defaultBodyCol = "#FFFFFF";

    private final Map<Integer, String[]> colorMap = new HashMap<Integer, String[]>() {{
        put(R.drawable.card_col_1, new String[]{"#000000", "#FFFFFF"});
        put(R.drawable.card_col_2, new String[]{"#E91E63", "#F8BBD0"});
        put(R.drawable.card_col_3, new String[]{"#9C27B0", "#CE93D8"});
        put(R.drawable.card_col_4, new String[]{"#2196F3", "#BBDEFB"});
        put(R.drawable.card_col_5, new String[]{"#FFA000", "#FFF176"});
        put(R.drawable.card_col_6, new String[]{"#F57C00", "#FFE0B2"});
        put(R.drawable.card_col_7, new String[]{"#D32F2F", "#FFCDD2"});
        put(R.drawable.card_col_8, new String[]{"#388E3C", "#C8E6C9"});
        // Special
        put(R.drawable.card_col_9, new String[]{"#8E44AD", "#A3FF8F"});
        put(R.drawable.card_col_10, new String[]{"#6EC6FF", "#FFE1F0"});
        put(R.drawable.card_col_11, new String[]{"#3949AB", "#E8EAF6"});
        put(R.drawable.card_col_12, new String[]{"#F8BBD0", "#F3E5F5"});
        put(R.drawable.card_col_13, new String[]{"#FF95CB", "#FFE0F0"});
    }};

    private NumberPicker hourStartPicker, hourEndPicker, minStartPicker, minEndPicker;
    private EditText schedTitle;
    private MaterialAutoCompleteTextView dayDropdown;
    private Switch alarmSwtich;

    private ScheduleDialogListener listener;
    private ScheduleDialogDeleteListener deleteListener;

    private final String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
    private View buttonClose;
    private TextView titleText;

    // Listener for schedule creation or update
    public interface ScheduleDialogListener {
        void onScheduleAdded(
                String title,
                int startHour, int startMinute,
                int endHour, int endMinute,
                int dayIndex,
                String outlineColor,
                String bodyColor,
                boolean alarmEnabled
        );
    }

    public void setScheduleDialogListener(ScheduleDialogListener listener) {
        this.listener = listener;
    }

    // Listener for schedule deletion - now includes minutes
    public interface ScheduleDialogDeleteListener {
        void onScheduleDeleted(String title, int dayIndex, int startHour, int startMinute, int endHour, int endMinute);
    }

    public void setScheduleDeleteListener(ScheduleDialogDeleteListener listener) {
        this.deleteListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.dialog_add_class_sched, container, false);

        // Bind views
        schedTitle = view.findViewById(R.id.sched_title);
        dayDropdown = view.findViewById(R.id.day_dropdown);
        hourStartPicker = view.findViewById(R.id.sched_hour_start);
        minStartPicker = view.findViewById(R.id.sched_min_start);
        hourEndPicker = view.findViewById(R.id.sched_hour_end);
        minEndPicker = view.findViewById(R.id.sched_min_end);
        buttonClose = view.findViewById(R.id.close_button);
        alarmSwtich = view.findViewById(R.id.alarm_toggle);

        // Setup dropdown
        ArrayAdapter<String> dayAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, daysOfWeek);
        dayDropdown.setAdapter(dayAdapter);
        dayDropdown.setDropDownBackgroundResource(android.R.color.white);
        dayDropdown.setOnClickListener(v -> dayDropdown.showDropDown());

        // Setup number pickers
        configureHourPicker(hourStartPicker);
        configureHourPicker(hourEndPicker);
        configureMinPicker(minStartPicker);
        configureMinPicker(minEndPicker);

        // Add color options
        addColorOptions(view);

        // Extract passed arguments
        Bundle args = getArguments();
        boolean isEdit = args != null && args.getBoolean("isEdit", false);

        if (args != null) {
            String initialTitle = args.getString("title", "");
            int dayIndex = args.getInt("dayIndex", 0);
            int start = args.getInt("startHour", 8);
            int startMin = args.getInt("startMinute", 0);
            int end = args.getInt("endHour", 9);
            int endMin = args.getInt("endMinute", 0);
            boolean alarmEnabled = args.getBoolean("alarmEnabled", false);

            String outlineColor = args.getString("outlineColor", "#000000");
            String bodyColor = args.getString("bodyColor", "#FFFFFF");

            schedTitle.setText(initialTitle);
            dayDropdown.setText(daysOfWeek[dayIndex], false);
            hourStartPicker.setValue(start);
            minStartPicker.setValue(startMin);
            hourEndPicker.setValue(end);
            minEndPicker.setValue(endMin);

            defaultOutlineCol = outlineColor;
            defaultBodyCol = bodyColor;

            alarmSwtich.setChecked(alarmEnabled);
        }

        // Handle Add/Update
        MaterialButton dialogSchedAdd = view.findViewById(R.id.dialog_sched_add);
        dialogSchedAdd.setText(isEdit ? "Save" : "Add");
        titleText = view.findViewById(R.id.title_text);
        titleText.setText(isEdit ? "Edit Class Schedule" : "New Class Schedule");

        dialogSchedAdd.setOnClickListener(v -> {
            String title = schedTitle.getText().toString().trim();
            String day = dayDropdown.getText().toString().trim();

            int startHour = hourStartPicker.getValue();
            int startMinute = minStartPicker.getValue();
            int endHour = hourEndPicker.getValue();
            int endMinute = minEndPicker.getValue();
            int dayIndex = getDayIndex(day);

            if (title.isEmpty()) {
                Toast.makeText(getContext(), "Please enter a schedule title.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (dayIndex == -1) {
                Toast.makeText(getContext(), "Please select a valid day.", Toast.LENGTH_SHORT).show();
                return;
            }

            int startTotal = startHour * 60 + startMinute;
            int endTotal = endHour * 60 + endMinute;
            if (startTotal >= endTotal) {
                Toast.makeText(getContext(), "Start time must be earlier than end time.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (listener != null) {
                Log.d("ScheduleDialog", String.format(
                        "Adding: %s (%02d:%02d–%02d:%02d) on day %d with colors %s / %s",
                        title, startHour, startMinute, endHour, endMinute,
                        dayIndex, defaultOutlineCol, defaultBodyCol));

                listener.onScheduleAdded(
                        title,
                        startHour, startMinute,
                        endHour, endMinute,
                        dayIndex,
                        defaultOutlineCol,
                        defaultBodyCol,
                        alarmSwtich.isChecked()
                );
            }

            if (isEdit){
                Toast.makeText(requireContext(), "Class Schedule Updated.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Class Schedule Added.", Toast.LENGTH_SHORT).show();
            }

            dismiss();
        });
        buttonClose.setOnClickListener(v -> dismiss());

        MaterialButton deleteButton = view.findViewById(R.id.dialog_sched_del);
        deleteButton.setVisibility(isEdit ? View.VISIBLE : View.INVISIBLE);



        deleteButton.setOnClickListener(v -> {
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

            title.setText("Confirm Class Schedule Deletion");
            message.setText("Are you sure you want to delete this part of your class schedule?");

            noBtn.setText("No");
            noBtn.setOnClickListener(btn -> deleteDialog.dismiss());

            yesBtn.setText("Yes");
            yesBtn.setOnClickListener(btn -> {
                deleteDialog.dismiss();
                if (deleteListener != null) {
                    String titleStr = schedTitle.getText().toString().trim();
                    String day = dayDropdown.getText().toString().trim();
                    int dayIndex = getDayIndex(day);
                    int start = hourStartPicker.getValue();
                    int startMin = minStartPicker.getValue();
                    int end = hourEndPicker.getValue();
                    int endMin = minEndPicker.getValue();

                    deleteListener.onScheduleDeleted(titleStr, dayIndex, start, startMin, end, endMin);
                    Toast.makeText(requireContext(), "Class Schedule Block deleted", Toast.LENGTH_SHORT).show();
                }
                dismiss();
            });

            deleteDialog.show();
        });

        return view;
    }

    private void configureHourPicker(NumberPicker picker) {
        picker.setMinValue(0);
        picker.setMaxValue(23);
        picker.setWrapSelectorWheel(true);

        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        picker.setDisplayedValues(hours);
    }

    private void configureMinPicker(NumberPicker picker) {
        picker.setMinValue(0);
        picker.setMaxValue(59);
        picker.setWrapSelectorWheel(true);

        String[] minutes = new String[60];
        for (int i = 0; i < 60; i++) {
            minutes[i] = String.format("%02d", i);
        }
        picker.setDisplayedValues(minutes);
    }

    private void addColorOptions(View rootView) {
        LinearLayout container = rootView.findViewById(R.id.color_buttons_container);

        int[] colorDrawables = {
                R.drawable.card_col_1,
                R.drawable.card_col_2,
                R.drawable.card_col_3,
                R.drawable.card_col_4,
                R.drawable.card_col_5,
                R.drawable.card_col_6,
                R.drawable.card_col_7,
                R.drawable.card_col_8,
                R.drawable.card_col_9,
                R.drawable.card_col_10,
                R.drawable.card_col_11,
                R.drawable.card_col_12,
                R.drawable.card_col_13
        };

        for (int drawableId : colorDrawables) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(35), dpToPx(35));
            params.setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            card.setLayoutParams(params);
            card.setCardElevation(0f);
            card.setStrokeColor(Color.BLACK);
            card.setStrokeWidth(dpToPx(1));
            card.setRadius(dpToPx(17.5f));
            card.setClickable(true);

            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setImageResource(drawableId);
            card.addView(image);

            card.setOnClickListener(v -> {
                String[] colors = colorMap.get(drawableId);
                if (colors != null) {
                    defaultOutlineCol = colors[0];
                    defaultBodyCol = colors[1];
                    Log.d("ScheduleDialog", "Selected Colors: " + defaultOutlineCol + " / " + defaultBodyCol);
                }

                // Highlight selected
                for (int i = 0; i < container.getChildCount(); i++) {
                    container.getChildAt(i).setAlpha(0.5f);
                }
                card.setAlpha(1f);
            });

            container.addView(card);
        }
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getDayIndex(String day) {
        for (int i = 0; i < daysOfWeek.length; i++) {
            if (daysOfWeek[i].equalsIgnoreCase(day)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getTheme() {
        return R.style.ScheduleDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.80); // 70% of screen height
            getDialog().getWindow().setLayout(width, maxHeight);
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }



}