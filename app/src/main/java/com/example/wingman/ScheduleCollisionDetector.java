package com.example.wingman;

import com.example.wingman.data.ClassSched;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleCollisionDetector {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static boolean hasClassScheduleCollision(ClassSched newSched,
                                                    List<ClassSched> existingSchedules,
                                                    String excludeId) {
        if (newSched == null || existingSchedules == null) {
            return false;
        }

        int newDay = newSched.getDayIndex();
        int newStartTime = newSched.getStartHour() * 60 + newSched.getStartMinute();
        int newEndTime = newSched.getEndHour() * 60 + newSched.getEndMinute();

        for (ClassSched existing : existingSchedules) {
            // Skip if this is the same schedule being updated
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }

            // Only check schedules on the same day
            if (existing.getDayIndex() != newDay) {
                continue;
            }

            int existingStartTime = existing.getStartHour() * 60 + existing.getStartMinute();
            int existingEndTime = existing.getEndHour() * 60 + existing.getEndMinute();

            // Check for time overlap
            if (hasTimeOverlap(newStartTime, newEndTime, existingStartTime, existingEndTime)) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasGeneralScheduleCollision(NewSchedule newSchedule,
                                                      List<NewSchedule> existingSchedules,
                                                      String excludeId) {
        if (newSchedule == null || existingSchedules == null || newSchedule.getDate() == null) {
            return false;
        }

        String newDate = newSchedule.getDate().trim();
        String newTitle = newSchedule.getTitle() != null ? newSchedule.getTitle().trim().toLowerCase() : "";

        for (NewSchedule existing : existingSchedules) {
            // Skip if this is the same schedule being updated
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }

            String existingDate = existing.getDate() != null ? existing.getDate().trim() : "";
            String existingTitle = existing.getTitle() != null ? existing.getTitle().trim().toLowerCase() : "";

            // Check if same date and similar/same title (potential duplicate)
            if (newDate.equals(existingDate) && newTitle.equals(existingTitle)) {
                return true;
            }
        }

        return false;
    }

    public static List<ClassSched> getConflictingClassSchedules(ClassSched newSched,
                                                                List<ClassSched> existingSchedules,
                                                                String excludeId) {
        List<ClassSched> conflicts = new ArrayList<>();

        if (newSched == null || existingSchedules == null) {
            return conflicts;
        }

        int newDay = newSched.getDayIndex();
        int newStartTime = newSched.getStartHour() * 60 + newSched.getStartMinute();
        int newEndTime = newSched.getEndHour() * 60 + newSched.getEndMinute();

        for (ClassSched existing : existingSchedules) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }

            if (existing.getDayIndex() != newDay) {
                continue;
            }

            int existingStartTime = existing.getStartHour() * 60 + existing.getStartMinute();
            int existingEndTime = existing.getEndHour() * 60 + existing.getEndMinute();

            if (hasTimeOverlap(newStartTime, newEndTime, existingStartTime, existingEndTime)) {
                conflicts.add(existing);
            }
        }

        return conflicts;
    }

    public static List<NewSchedule> getConflictingGeneralSchedules(NewSchedule newSchedule,
                                                                   List<NewSchedule> existingSchedules,
                                                                   String excludeId) {
        List<NewSchedule> conflicts = new ArrayList<>();

        if (newSchedule == null || existingSchedules == null || newSchedule.getDate() == null) {
            return conflicts;
        }

        String newDate = newSchedule.getDate().trim();
        String newTitle = newSchedule.getTitle() != null ? newSchedule.getTitle().trim().toLowerCase() : "";

        for (NewSchedule existing : existingSchedules) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }

            String existingDate = existing.getDate() != null ? existing.getDate().trim() : "";
            String existingTitle = existing.getTitle() != null ? existing.getTitle().trim().toLowerCase() : "";

            if (newDate.equals(existingDate) && newTitle.equals(existingTitle)) {
                conflicts.add(existing);
            }
        }

        return conflicts;
    }

    private static boolean hasTimeOverlap(int start1, int end1, int start2, int end2) {
        // No overlap if one ends before the other starts
        return !(end1 <= start2 || end2 <= start1);
    }

    public static String formatTime(int hour24, int minute) {
        int hour12 = (hour24 % 12 == 0) ? 12 : (hour24 % 12);
        String ampm = (hour24 < 12) ? "AM" : "PM";
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, ampm);
    }

    public static String createClassConflictMessage(List<ClassSched> conflicts, int dayIndex) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "";
        }

        String[] daysOfWeek = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
        String dayName = (dayIndex >= 0 && dayIndex < daysOfWeek.length) ? daysOfWeek[dayIndex] : "Unknown";

        StringBuilder message = new StringBuilder();
        message.append("Schedule conflict detected on ").append(dayName).append(":\n\n");

        for (ClassSched conflict : conflicts) {
            String startTime = formatTime(conflict.getStartHour(), conflict.getStartMinute());
            String endTime = formatTime(conflict.getEndHour(), conflict.getEndMinute());
            message.append("• ").append(conflict.getTitle())
                    .append(" (").append(startTime).append(" - ").append(endTime).append(")\n");
        }

        message.append("\nPlease choose a different time slot.");
        return message.toString();
    }

    public static String createGeneralConflictMessage(List<NewSchedule> conflicts) {
        if (conflicts == null || conflicts.isEmpty()) {
            return "";
        }

        StringBuilder message = new StringBuilder();
        message.append("A schedule with the same title already exists on this date:\n\n");

        for (NewSchedule conflict : conflicts) {
            message.append("• ").append(conflict.getTitle())
                    .append(" (").append(conflict.getDate()).append(")\n");
        }

        message.append("\nPlease use a different title or date.");
        return message.toString();
    }
}