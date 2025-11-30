package com.example.wingman;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalyticsViewModel extends ViewModel {

    private final MutableLiveData<AnalyticsData> analytics = new MutableLiveData<>();
    private final MutableLiveData<List<DailyBreakdownItem>> dailyBreakdown = new MutableLiveData<>();
    private final MutableLiveData<StreakData> streakData = new MutableLiveData<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String userId;

    public AnalyticsViewModel() {
        userId = FirebaseAuth.getInstance().getUid();
    }

    public LiveData<AnalyticsData> getAnalytics() {
        return analytics;
    }

    public LiveData<List<DailyBreakdownItem>> getDailyBreakdown() {
        return dailyBreakdown;
    }

    public LiveData<StreakData> getStreakData() {
        return streakData;
    }

    public void loadDayAnalytics() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        loadAnalytics(startOfDay, System.currentTimeMillis(), "Today");
        calculateAllStreaks();
    }

    public void loadWeekAnalytics() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfWeek = cal.getTimeInMillis();

        loadAnalytics(startOfWeek, System.currentTimeMillis(), "This Week");
        calculateAllStreaks();
    }

    public void loadMonthAnalytics() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfMonth = cal.getTimeInMillis();

        loadAnalytics(startOfMonth, System.currentTimeMillis(), "This Month");
        calculateAllStreaks();
    }

    private void loadAnalytics(long startTime, long endTime, String period) {
        if (userId == null) {
            analytics.postValue(new AnalyticsData(0, 0, 0, 0, period));
            dailyBreakdown.postValue(new ArrayList<>());
            return;
        }

        db.collection("users")
                .document(userId)
                .collection("pomodoro_sessions")
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int totalSessions = 0;
                    int totalMinutes = 0;
                    Map<String, Integer> dailySessionsMap = new HashMap<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Long duration = doc.getLong("durationMinutes");
                        Long timestamp = doc.getLong("timestamp");

                        if (duration != null) {
                            totalSessions++;
                            totalMinutes += duration;

                            if (timestamp != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTimeInMillis(timestamp);
                                String dateKey = String.format("%02d/%02d",
                                        cal.get(Calendar.MONTH) + 1,
                                        cal.get(Calendar.DAY_OF_MONTH));

                                dailySessionsMap.put(dateKey,
                                        dailySessionsMap.getOrDefault(dateKey, 0) + 1);
                            }
                        }
                    }

                    double avgSession = totalSessions > 0 ? (double) totalMinutes / totalSessions : 0;

                    int daysInPeriod = calculateDaysInPeriod(startTime, endTime);
                    int expectedSessions = daysInPeriod * 4;
                    double productivityRate = expectedSessions > 0 ?
                            (totalSessions / (double) expectedSessions) * 100 : 0;
                    productivityRate = Math.min(100, productivityRate);

                    AnalyticsData data = new AnalyticsData(
                            totalSessions,
                            totalMinutes,
                            avgSession,
                            productivityRate,
                            period
                    );
                    analytics.postValue(data);

                    List<DailyBreakdownItem> breakdown = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : dailySessionsMap.entrySet()) {
                        breakdown.add(new DailyBreakdownItem(entry.getKey(), entry.getValue()));
                    }
                    breakdown.sort((a, b) -> b.date.compareTo(a.date));
                    dailyBreakdown.postValue(breakdown);
                })
                .addOnFailureListener(e -> {
                    analytics.postValue(new AnalyticsData(0, 0, 0, 0, period));
                    dailyBreakdown.postValue(new ArrayList<>());
                });
    }

    private void calculateAllStreaks() {
        if (userId == null) {
            streakData.postValue(new StreakData(0, 0, 0));
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -1);
        long oneYearAgo = cal.getTimeInMillis();

        db.collection("users")
                .document(userId)
                .collection("pomodoro_sessions")
                .whereGreaterThanOrEqualTo("timestamp", oneYearAgo)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Set<String> daysWithSessions = new HashSet<>();

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Long timestamp = doc.getLong("timestamp");
                        if (timestamp != null) {
                            Calendar sessionCal = Calendar.getInstance();
                            sessionCal.setTimeInMillis(timestamp);

                            String dayKey = String.format("%d-%02d-%02d",
                                    sessionCal.get(Calendar.YEAR),
                                    sessionCal.get(Calendar.MONTH) + 1,
                                    sessionCal.get(Calendar.DAY_OF_MONTH));

                            daysWithSessions.add(dayKey);
                        }
                    }

                    int dayStreak = calculateConsecutiveDays(daysWithSessions);

                    int weekStreak = dayStreak / 7;

                    int monthStreak = dayStreak / 30;

                    streakData.postValue(new StreakData(dayStreak, weekStreak, monthStreak));
                })
                .addOnFailureListener(e -> {
                    streakData.postValue(new StreakData(0, 0, 0));
                });
    }

    private int calculateConsecutiveDays(Set<String> daysWithSessions) {
        int streak = 0;
        Calendar checkDate = Calendar.getInstance();

        String todayKey = String.format("%d-%02d-%02d",
                checkDate.get(Calendar.YEAR),
                checkDate.get(Calendar.MONTH) + 1,
                checkDate.get(Calendar.DAY_OF_MONTH));

        if (!daysWithSessions.contains(todayKey)) {
            checkDate.add(Calendar.DAY_OF_MONTH, -1);
            String yesterdayKey = String.format("%d-%02d-%02d",
                    checkDate.get(Calendar.YEAR),
                    checkDate.get(Calendar.MONTH) + 1,
                    checkDate.get(Calendar.DAY_OF_MONTH));

            if (!daysWithSessions.contains(yesterdayKey)) {
                return 0;
            }
        }

        for (int i = 0; i < 365; i++) {
            String dayKey = String.format("%d-%02d-%02d",
                    checkDate.get(Calendar.YEAR),
                    checkDate.get(Calendar.MONTH) + 1,
                    checkDate.get(Calendar.DAY_OF_MONTH));

            if (daysWithSessions.contains(dayKey)) {
                streak++;
                checkDate.add(Calendar.DAY_OF_MONTH, -1);
            } else {
                break;
            }
        }

        return streak;
    }

    private int calculateDaysInPeriod(long startTime, long endTime) {
        long diff = endTime - startTime;
        return Math.max(1, (int) (diff / (1000 * 60 * 60 * 24)));
    }
}

class AnalyticsData {
    int totalSessions;
    int totalTimeMinutes;
    double averageSessionMinutes;
    double productivityRate;
    String period;

    public AnalyticsData(int totalSessions, int totalTimeMinutes,
                         double averageSessionMinutes, double productivityRate, String period) {
        this.totalSessions = totalSessions;
        this.totalTimeMinutes = totalTimeMinutes;
        this.averageSessionMinutes = averageSessionMinutes;
        this.productivityRate = productivityRate;
        this.period = period;
    }
}

class DailyBreakdownItem {
    String date;
    int sessions;

    public DailyBreakdownItem(String date, int sessions) {
        this.date = date;
        this.sessions = sessions;
    }
}

class StreakData {
    int dayStreak;
    int weekStreak;
    int monthStreak;

    public StreakData(int dayStreak, int weekStreak, int monthStreak) {
        this.dayStreak = dayStreak;
        this.weekStreak = weekStreak;
        this.monthStreak = monthStreak;
    }
}