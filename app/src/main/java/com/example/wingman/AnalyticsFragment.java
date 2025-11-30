package com.example.wingman;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

public class AnalyticsFragment extends Fragment {

    private TextView tvTotalSessions, tvTotalTime, tvAverageSession, tvProductivityRate;
    private TextView tvInsightTitle, tvInsightMessage, tvStreak, tvStreakLabel;
    private ImageView ivInsightIcon;
    private CardView cardInsight;
    private TabLayout tabLayout;
    private RecyclerView rvDailyBreakdown;
    private AnalyticsViewModel viewModel;
    private DailyBreakdownAdapter adapter;
    private int currentTabPosition = 0;
    private StreakData currentStreakData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_analytics, container, false);

        ImageButton btnBack = view.findViewById(R.id.btnBack);
        tvTotalSessions = view.findViewById(R.id.tvTotalSessions);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        tvAverageSession = view.findViewById(R.id.tvAverageSession);
        tvProductivityRate = view.findViewById(R.id.tvProductivityRate);
        tabLayout = view.findViewById(R.id.tabLayout);
        rvDailyBreakdown = view.findViewById(R.id.rvDailyBreakdown);

        cardInsight = view.findViewById(R.id.cardInsight);
        tvInsightTitle = view.findViewById(R.id.tvInsightTitle);
        tvInsightMessage = view.findViewById(R.id.tvInsightMessage);
        ivInsightIcon = view.findViewById(R.id.ivInsightIcon);
        tvStreak = view.findViewById(R.id.tvStreak);
        tvStreakLabel = view.findViewById(R.id.tvStreakLabel);

        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        rvDailyBreakdown.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DailyBreakdownAdapter();
        rvDailyBreakdown.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(AnalyticsViewModel.class);

        tabLayout.addTab(tabLayout.newTab().setText("Day"));
        tabLayout.addTab(tabLayout.newTab().setText("Week"));
        tabLayout.addTab(tabLayout.newTab().setText("Month"));

        viewModel.getAnalytics().observe(getViewLifecycleOwner(), analytics -> {
            if (analytics != null) {
                updateUI(analytics);
                updateInsights(analytics);
            }
        });

        viewModel.getDailyBreakdown().observe(getViewLifecycleOwner(), breakdown -> {
            if (breakdown != null) {
                adapter.setData(breakdown);
            }
        });

        viewModel.getStreakData().observe(getViewLifecycleOwner(), streakData -> {
            if (streakData != null) {
                currentStreakData = streakData;
                updateStreakDisplay(streakData);
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                loadDataForCurrentTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDataForCurrentTab();
    }

    private void loadDataForCurrentTab() {
        switch (currentTabPosition) {
            case 0:
                viewModel.loadDayAnalytics();
                break;
            case 1:
                viewModel.loadWeekAnalytics();
                break;
            case 2:
                viewModel.loadMonthAnalytics();
                break;
        }
    }

    private void updateUI(AnalyticsData analytics) {
        tvTotalSessions.setText(String.valueOf(analytics.totalSessions));
        tvTotalTime.setText(formatTime(analytics.totalTimeMinutes));
        tvAverageSession.setText(String.format("%.1f min", analytics.averageSessionMinutes));
        tvProductivityRate.setText(String.format("%.0f%%", analytics.productivityRate));
    }

    private void updateInsights(AnalyticsData analytics) {
        String period = getPeriodText();
        int sessions = analytics.totalSessions;
        double productivity = analytics.productivityRate;

        if (sessions == 0) {
            showInsight("Get Started!",
                    "No sessions completed " + period + ". Start your first Pomodoro to build momentum!",
                    R.color.blue);
        } else if (productivity >= 90) {
            showInsight("Outstanding!",
                    "You're crushing it! " + sessions + " sessions " + period + ". You're in the top 10% of productive people!",
                    R.color.gold);
        } else if (productivity >= 75) {
            showInsight("Excellent Work!",
                    sessions + " sessions " + period + "! You're maintaining strong focus. Keep this momentum going!",
                    R.color.gold);
        } else if (productivity >= 50) {
            showInsight("Good Progress!",
                    "You completed " + sessions + " sessions " + period + ". You're building a solid routine. Try adding 1-2 more sessions!",
                    R.color.blue);
        } else if (productivity >= 25) {
            showInsight("Getting Started!",
                    sessions + " sessions " + period + " is a good start! Aim for 4 sessions per day to reach your potential.",
                    R.color.blue);
        } else {
            showInsight("Room to Grow",
                    "Only " + sessions + " sessions " + period + ". Small consistent effort leads to big results. Start with just 2 sessions tomorrow!",
                    R.color.blue);
        }
    }

    private void showInsight(String title, String message, int colorRes) {
        tvInsightTitle.setText(title);
        tvInsightMessage.setText(message);
        cardInsight.setCardBackgroundColor(getResources().getColor(colorRes));
    }

    private String getPeriodText() {
        switch (currentTabPosition) {
            case 0: return "today";
            case 1: return "this week";
            case 2: return "this month";
            default: return "in this period";
        }
    }

    private void updateStreakDisplay(StreakData streakData) {
        if (streakData == null) {
            tvStreak.setVisibility(View.GONE);
            tvStreakLabel.setVisibility(View.GONE);
            return;
        }

        int displayStreak = 0;
        String label = "";

        switch (currentTabPosition) {
            case 0:
                displayStreak = streakData.dayStreak;
                label = displayStreak == 1 ? "Day Streak" : "Days Streak";
                break;
            case 1:
                displayStreak = streakData.weekStreak;
                label = displayStreak == 1 ? "Week Streak" : "Weeks Streak";
                break;
            case 2:
                displayStreak = streakData.monthStreak;
                label = displayStreak == 1 ? "Month Streak" : "Months Streak";
                break;
        }

        if (displayStreak > 0) {
            tvStreak.setText(String.valueOf(displayStreak));
            tvStreakLabel.setText(label);
            tvStreak.setVisibility(View.VISIBLE);
            tvStreakLabel.setVisibility(View.VISIBLE);
        } else {
            tvStreak.setVisibility(View.GONE);
            tvStreakLabel.setVisibility(View.GONE);
        }
    }

    private String formatTime(int minutes) {
        if (minutes < 60) {
            return minutes + " min";
        } else {
            int hours = minutes / 60;
            int mins = minutes % 60;
            if (mins == 0) {
                return hours + " hr";
            }
            return hours + "h " + mins + "m";
        }
    }
}