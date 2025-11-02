package com.example.wingman;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

public class AnalyticsFragment extends Fragment {

    private TextView tvTotalSessions, tvTotalTime, tvAverageSession, tvProductivityRate;
    private TabLayout tabLayout;
    private RecyclerView rvDailyBreakdown;
    private AnalyticsViewModel viewModel;
    private DailyBreakdownAdapter adapter;
    private int currentTabPosition = 0;

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
            }
        });

        viewModel.getDailyBreakdown().observe(getViewLifecycleOwner(), breakdown -> {
            if (breakdown != null) {
                adapter.setData(breakdown);
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