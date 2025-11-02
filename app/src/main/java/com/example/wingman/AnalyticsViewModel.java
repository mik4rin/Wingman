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
import java.util.List;
import java.util.Map;

public class AnalyticsViewModel extends ViewModel {

    private final MutableLiveData<AnalyticsData> analytics = new MutableLiveData<>();
    private final MutableLiveData<List<DailyBreakdownItem>> dailyBreakdown = new MutableLiveData<>();
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

    public void loadDayAnalytics() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        loadAnalytics(startOfDay, System.currentTimeMillis(), "Today");
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
    }

    private void loadAnalytics(long startTime, long endTime, String period) {
        if (userId == null) return;

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