package com.example.wingman;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.example.wingman.data.ClassSched;
import com.example.wingman.data.ClassSchedRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SchedulerClassInterface {
    private final Context context;
    private final WebView webView;
    private final ClassSchedRepository classSchedRepo;

    public SchedulerClassInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
        this.classSchedRepo = new ClassSchedRepository();
    }

    @JavascriptInterface
    public void onBlockClicked(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String title = obj.getString("title");
            int day = obj.getInt("day");
            int startHour = obj.getInt("startHour");
            int startMinute = obj.optInt("startMinute", 0);
            int endHour = obj.getInt("endHour");
            int endMinute = obj.getInt("endMinute");
            String outlineColor = obj.optString("borderColor", "#4CAF50");
            String bodyColor = obj.optString("backgroundColor", "#C8E6C9");
            boolean alarmEnabled = obj.optBoolean("alarmEnabled", false);

            new Handler(Looper.getMainLooper()).post(() -> {
                ScheduleClassDialog dialog = new ScheduleClassDialog();

                Bundle args = new Bundle();
                args.putString("title", title);
                args.putInt("dayIndex", day);
                args.putInt("startHour", startHour);
                args.putInt("startMinute", startMinute);
                args.putInt("endHour", endHour);
                args.putInt("endMinute", endMinute);
                args.putString("outlineColor", outlineColor);
                args.putString("bodyColor", bodyColor);
                args.putBoolean("alarmEnabled", alarmEnabled);
                args.putBoolean("isEdit", true);
                dialog.setArguments(args);

                dialog.setScheduleDialogListener(
                        (newTitle, newStartHour, newStartMinute, newEndHour, newEndMinute,
                         newDayIndex, newOutline, newBody, newAlarmEnabled) -> {

                            String uid = SessionManager.getUserId(context);
                            classSchedRepo.getAllForUser(getTask -> {
                                if (!getTask.isSuccessful() || getTask.getResult() == null) return;
                                List<ClassSched> scheds = getTask.getResult();
                                for (ClassSched sched : scheds) {
                                    if (sched.getTitle().equals(title) &&
                                            sched.getDayIndex() == day &&
                                            sched.getStartHour() == startHour &&
                                            sched.getStartMinute() == startMinute &&
                                            sched.getEndHour() == endHour &&
                                            sched.getEndMinute() == endMinute) {

                                        int oldDay = sched.getDayIndex();
                                        int oldStartHour = sched.getStartHour();
                                        int oldStartMin = sched.getStartMinute();
                                        boolean oldAlarmEnabled = sched.isAlarmEnabled();

                                        sched.setTitle(newTitle);
                                        sched.setStartHour(newStartHour);
                                        sched.setStartMinute(newStartMinute);
                                        sched.setEndHour(newEndHour);
                                        sched.setEndMinute(newEndMinute);
                                        sched.setDayIndex(newDayIndex);
                                        sched.setMainColor(newOutline);
                                        sched.setAccentColor(newBody);
                                        sched.setAlarmEnabled(newAlarmEnabled);

                                        classSchedRepo.update(sched, updateTask -> {
                                            if (updateTask.isSuccessful()) {
                                            } else {
                                                Exception e = updateTask.getException();
                                                if (e != null) e.printStackTrace();
                                            }
                                        });

                                        if (newAlarmEnabled) {
                                            ScheduleUtils.scheduleAlarm(context.getApplicationContext(), sched, true);
                                        } else {
                                            ScheduleUtils.cancelAlarm(context.getApplicationContext(), sched);
                                        }

                                        boolean dayChanged = (oldDay != newDayIndex);
                                        boolean timeChanged = (oldStartHour != newStartHour || oldStartMin != newStartMinute);

                                        if (newAlarmEnabled && (!oldAlarmEnabled || dayChanged || timeChanged)) {
                                            String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
                                            String dayName = daysOfWeek[newDayIndex];
                                            String timeStr = formatTime(newStartHour, newStartMinute);
                                            String message = "Alarm is set for " + newTitle + " at " + dayName + ", " + timeStr;
                                            new Handler(Looper.getMainLooper()).post(() ->
                                                    com.google.android.material.snackbar.Snackbar.make(webView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show());
                                        } else if (!newAlarmEnabled && oldAlarmEnabled) {
                                            String message = "Alarm is cancelled for " + newTitle;
                                            new Handler(Looper.getMainLooper()).post(() ->
                                                    com.google.android.material.snackbar.Snackbar.make(webView, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show());
                                        }

                                        String js = String.format(
                                                "updateScheduleBlock('%s', %d, %d, %d, %d, %d, %d, %d, %d, '%s', '%s', '%s', %b);",
                                                escapeJs(title),
                                                day, startHour, startMinute,
                                                newDayIndex, newStartHour, newStartMinute,
                                                newEndHour, newEndMinute,
                                                escapeJs(newTitle),
                                                newOutline, newBody,
                                                newAlarmEnabled
                                        );

                                        new Handler(Looper.getMainLooper()).post(() -> webView.evaluateJavascript(js, null));
                                        break;
                                    }
                                }
                            });
                        });

                dialog.setScheduleDeleteListener((delTitle, delDay, delStartHour, delStartMinute, delEndHour, delEndMinute) -> {
                    classSchedRepo.getAllForUser(getTask -> {
                        if (!getTask.isSuccessful() || getTask.getResult() == null) return;
                        List<ClassSched> scheds = getTask.getResult();
                        for (ClassSched sched : scheds) {
                            if (sched.getTitle().equals(delTitle) &&
                                    sched.getDayIndex() == delDay &&
                                    sched.getStartHour() == delStartHour &&
                                    sched.getStartMinute() == delStartMinute &&
                                    sched.getEndHour() == delEndHour &&
                                    sched.getEndMinute() == delEndMinute) {

                                classSchedRepo.delete(sched.getId(), delTask -> {
                                    ScheduleUtils.cancelAlarm(context, sched);
                                    deleteBlockFromJS(delTitle, delDay, delStartHour, delStartMinute, delEndHour, delEndMinute);
                                });
                                break;
                            }
                        }
                    });
                });

                if (context instanceof androidx.fragment.app.FragmentActivity) {
                    androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), "EditScheduleDialog");
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void deleteBlockFromJS(String title, int day, int startHour, int startMinute, int endHour, int endMinute) {
        String js = String.format(
                "deleteBlock('%s', %d, %d, %d, %d, %d);",
                escapeJs(title), day, startHour, startMinute, endHour, endMinute
        );
        new Handler(Looper.getMainLooper()).post(() -> webView.evaluateJavascript(js, null));
    }

    private String escapeJs(String s) {
        return s.replace("'", "\\'");
    }

    private String formatTime(int hour24, int minute) {
        int hour12 = (hour24 % 12 == 0) ? 12 : (hour24 % 12);
        String ampm = (hour24 < 12) ? "AM" : "PM";
        return String.format("%02d:%02d %s", hour12, minute, ampm);
    }
}