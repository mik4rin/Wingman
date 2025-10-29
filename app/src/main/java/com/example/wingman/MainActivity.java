package com.example.wingman;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.wingman.data.Notification;
import com.example.wingman.data.NotificationRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    ImageButton optionsButton;
    ImageButton notificationButton;

    FloatingActionButton fabAdd;

    private View notificationBadge;
    private View headerLayout;
    private LinearLayout navBar;

    private List<Notification> notifications;
    private PopupWindow notificationPopup;

    private int currentFragmentPosition = 0;
    private final int HOME_POSITION = 0;
    private final int TASKS_POSITION = 1;
    private final int SCHEDULE_POSITION = 2;
    private final int NOTES_POSITION = 3;
    private final int STUDY_POSITION = 4;
    TimerViewModel timerViewModel;
    private static final int REQUEST_CODE_NOTIFICATION = 1001;
    private static final String TAG = "MainActivity";

    private NotificationRepository notificationRepository;
    private ListenerRegistration notificationsRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerViewModel = new ViewModelProvider(this).get(TimerViewModel.class);

        timerViewModel.isRunning.observe(this, isRunning -> {
            if (isRunning != null) {
                Intent musicIntent = new Intent(MainActivity.this, MusicService.class);
                if (isRunning) {
                    musicIntent.setAction("PLAY");
                    startService(musicIntent);
                } else {
                    musicIntent.setAction("STOP");
                    startService(musicIntent);
                }
            }
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        hideSystemUI();


        notificationBadge = findViewById(R.id.notification_badge);
        headerLayout = findViewById(R.id.app_header);

        navBar = findViewById(R.id.bottom_navigation);
        setupBottomNavigation();


        requestNotificationPermission();

        notificationRepository = new NotificationRepository();
        notifications = new ArrayList<>();

        notificationButton = findViewById(R.id.notification_btn);
        notificationButton.setOnClickListener(this::showNotificationDropdown);
        fabAdd = findViewById(R.id.fab_add_task);
        fabAdd.setOnClickListener(v -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment instanceof TasksFragment) {
                ((TasksFragment) currentFragment).showAddTaskDialog();
            }
        });

        optionsButton = findViewById(R.id.settings_btn);
        optionsButton.setOnClickListener(this::showPopupMenu);




        Log.d(TAG, "Login successful. Launching MainActivity.");

        loadFragmentWithPosition(new HomeFragment(), HOME_POSITION, false);
        highlightNavItem(R.id.nav_home);


        attachNotificationsListener();

        updateNotificationBadge();
        scheduleTaskDeadlineWorker();


        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (userUid != null) {
            PeriodicWorkRequest checkDeadlineRequest = new PeriodicWorkRequest.Builder(
                    TaskDeadlineWorker.class,
                    15, TimeUnit.MINUTES)
                    .setInputData(new Data.Builder().putString("userUid", userUid).build())
                    .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "task_deadline_checker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    checkDeadlineRequest
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "task_channel",
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifies when a task is nearing its due time");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }






        handleIntent(getIntent());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachNotificationsListener();
    }

    public void onPause() {
        super.onPause();

        Intent pauseIntent = new Intent(this, MusicService.class);
        pauseIntent.setAction("PAUSE");
        startService(pauseIntent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachNotificationsListener();

        if (isFinishing()) {
            Intent stopIntent = new Intent(this, MusicService.class);
            stopIntent.setAction("STOP");
            startService(stopIntent);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        if (intent.getExtras() != null) {
            for (String k : intent.getExtras().keySet()) {
                Object v = intent.getExtras().get(k);
                Log.d(TAG, "handleIntent extra: " + k + " -> " + String.valueOf(v));
            }
        }

        String notifId = intent.getStringExtra("notif_id");
        if (notifId != null && !notifId.trim().isEmpty()) {
            markNotificationReadById(notifId);
        }

        String navigateTo = intent.getStringExtra("navigate_to");
        String itemId = intent.getStringExtra("item_id");
        String notificationType = intent.getStringExtra("notification_type");

        if (navigateTo == null) {
            navigateTo = intent.getStringExtra("destination");
        }
        if (navigateTo == null) {
            navigateTo = intent.getStringExtra("targetFragment");
            if (navigateTo != null) {
                if (navigateTo.equalsIgnoreCase("TasksFragment")) navigateTo = "task";
                else if (navigateTo.equalsIgnoreCase("ScheduleFragment")) navigateTo = "schedule";
                else if (navigateTo.equalsIgnoreCase("NotesFragment")) navigateTo = "notes";
                else if (navigateTo.equalsIgnoreCase("StudyFragment")) navigateTo = "study";
                else if (navigateTo.equalsIgnoreCase("HomeFragment")) navigateTo = "home";
            }
        }

        if (navigateTo == null) return;

        if (notifId == null || notifId.trim().isEmpty()) {
            markMatchingNotificationsAsRead(itemId, notificationType);
        }

        navigateToTarget(navigateTo, itemId, notificationType);
    }

    private void markMatchingNotificationsAsRead(String itemId, String type) {
        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (userUid == null) return;

        if ((itemId == null || itemId.trim().isEmpty()) && (type == null || type.trim().isEmpty())) {
            return;
        }

        notificationRepository.markNotificationsReadForUserTaskType(userUid, itemId, type, task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Marked matching notifications read for itemId=" + itemId + " type=" + type);
            } else {
                Log.e(TAG, "Failed markMatchingNotificationsAsRead", task.getException());
            }
        });
    }



    private void highlightNavItem(int navItemId) {
        LinearLayout navBar = findViewById(R.id.bottom_navigation);


        for (int i = 0; i < navBar.getChildCount(); i++) {
            View child = navBar.getChildAt(i);
            TextView label = null;

            if (child.getId() == R.id.nav_home) label = child.findViewById(R.id.nav_label_home);
            else if (child.getId() == R.id.nav_notes) label = child.findViewById(R.id.nav_label_notes);
            else if (child.getId() == R.id.nav_schedule) label = child.findViewById(R.id.nav_label_schedule);
            else if (child.getId() == R.id.nav_tasks) label = child.findViewById(R.id.nav_label_tasks);
            else if (child.getId() == R.id.nav_study) label = child.findViewById(R.id.nav_label_study);

            if (label != null) label.setVisibility(View.GONE);
            child.setBackgroundColor(Color.TRANSPARENT);
        }


        View selected = navBar.findViewById(navItemId);
        if (selected != null) {
            selected.setBackgroundResource(R.drawable.nav_active_indicator);

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) selected.getLayoutParams();
            params.height = (int) (40 * getResources().getDisplayMetrics().density);
            selected.setLayoutParams(params);
            selected.setPadding(0, 5, 0, 5);

            TextView selectedLabel = null;
            if (navItemId == R.id.nav_home) selectedLabel = selected.findViewById(R.id.nav_label_home);
            else if (navItemId == R.id.nav_notes) selectedLabel = selected.findViewById(R.id.nav_label_notes);
            else if (navItemId == R.id.nav_schedule) selectedLabel = selected.findViewById(R.id.nav_label_schedule);
            else if (navItemId == R.id.nav_tasks) selectedLabel = selected.findViewById(R.id.nav_label_tasks);
            else if (navItemId == R.id.nav_study) selectedLabel = selected.findViewById(R.id.nav_label_study);

            if (selectedLabel != null) selectedLabel.setVisibility(View.VISIBLE);
        }
    }





    private void navigateToTarget(String navigateTo, String itemId, String notificationType) {
        if (navigateTo == null) return;

        Fragment fragment = null;
        int newPosition = currentFragmentPosition;

        switch (navigateTo.toLowerCase(Locale.ROOT)) {
            case "task":
            case "tasks":
                fragment = new TasksFragment();
                newPosition = TASKS_POSITION;
                highlightNavItem(R.id.nav_tasks);
                break;

            case "schedule":
            case "schedules":
                fragment = new ScheduleFragment();
                newPosition = SCHEDULE_POSITION;
                highlightNavItem(R.id.nav_schedule);
                break;

            case "notes":
                fragment = new Notes_MainWindow();
                newPosition = NOTES_POSITION;
                highlightNavItem(R.id.nav_notes);
                break;

            case "study":
                fragment = new StudyFragment();
                newPosition = STUDY_POSITION;
                highlightNavItem(R.id.nav_study);
                break;

            case "home":
            default:
                fragment = new HomeFragment();
                newPosition = HOME_POSITION;
                highlightNavItem(R.id.nav_home);
                break;
        }

        if (fragment != null) {
            if (itemId != null || notificationType != null) {
                Bundle args = new Bundle();
                if (itemId != null) args.putString("item_id", itemId);
                if (notificationType != null) args.putString("notification_type", notificationType);
                fragment.setArguments(args);
            }
            loadFragmentWithPosition(fragment, newPosition, true);
        }
    }


    private void initializeNotifications() {
        notifications = new ArrayList<>();
    }

    private void showNotificationDropdown(View view) {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            updateNotificationBadge();
            return;
        }

        loadNotificationsFromDatabase();

        new Handler(Looper.getMainLooper()).postDelayed(() -> showNotificationDropdownInternal(view), 200);
    }

    private void showNotificationDropdownInternal(View view) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.notification_dropdown, null);

        notificationPopup = new PopupWindow(popupView,
                (int) (320 * getResources().getDisplayMetrics().density),
                (int) (400 * getResources().getDisplayMetrics().density),
                true);

        notificationPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        notificationPopup.setElevation(8);
        notificationPopup.setOutsideTouchable(true);

        LinearLayout notificationContainer = popupView.findViewById(R.id.notification_container);
        TextView notificationTitle = popupView.findViewById(R.id.notification_title);
        TextView clearAllBtn = popupView.findViewById(R.id.clear_all_btn);

        int unreadCount = 0;
        for (Notification item : notifications) {
            if (!item.isRead()) unreadCount++;
        }

        notificationTitle.setText("Notifications (" + unreadCount + ")");

        clearAllBtn.setOnClickListener(v -> {
            clearAllNotificationsForUser();
        });

        notificationContainer.removeAllViews();

        List<Notification> sortedNotifications = new ArrayList<>(notifications);

        for (int i = 0; i < sortedNotifications.size(); i++) {
            Notification item = sortedNotifications.get(i);
            View notificationView = createNotificationView(item, i);
            notificationContainer.addView(notificationView);
        }

        if (sortedNotifications.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No notifications");
            emptyView.setTextSize(14);
            emptyView.setTextColor(getResources().getColor(android.R.color.darker_gray));
            int padding = (int) (16 * getResources().getDisplayMetrics().density);
            emptyView.setPadding(padding, padding, padding, padding);
            emptyView.setGravity(Gravity.CENTER);
            notificationContainer.addView(emptyView);
        }

        int[] location = new int[2];
        view.getLocationOnScreen(location);
        notificationPopup.showAtLocation(view, android.view.Gravity.NO_GRAVITY,
                location[0] - 250, location[1] + view.getHeight() + 10);
    }

    private void markNotificationReadById(String notifId) {
        if (notifId == null || notifId.trim().isEmpty()) return;
        FirebaseFirestore.getInstance()
                .collection("notifications")
                .document(notifId)
                .update("isRead", true)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Marked notification " + notifId + " as read");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed marking notification read by id", e);
                });
    }

    private void clearAllNotificationsForUser() {
        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (userUid == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notifications")
                .whereEqualTo("userId", userUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        batch.delete(doc.getReference());
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                if (notifications != null) {
                                    notifications.clear();
                                }

                                if (notificationPopup != null && notificationPopup.isShowing()) {
                                    View popupView = notificationPopup.getContentView();
                                    LinearLayout container = popupView.findViewById(R.id.notification_container);
                                    TextView title = popupView.findViewById(R.id.notification_title);

                                    if (container != null) {
                                        container.removeAllViews();
                                        TextView emptyView = new TextView(this);
                                        emptyView.setText("No notifications");
                                        emptyView.setTextSize(14);
                                        emptyView.setTextColor(getResources().getColor(android.R.color.darker_gray));
                                        int padding = (int) (16 * getResources().getDisplayMetrics().density);
                                        emptyView.setPadding(padding, padding, padding, padding);
                                        emptyView.setGravity(Gravity.CENTER);
                                        container.addView(emptyView);
                                    }
                                    if (title != null) {
                                        title.setText("Notifications (0)");
                                    }
                                }
                                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                if (manager != null) {
                                    manager.cancelAll();
                                }
                                updateNotificationBadge();
                                Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show();

                                if (notificationPopup != null && notificationPopup.isShowing()) {
                                    notificationPopup.dismiss();
                                }
                            })
                            .addOnFailureListener(e -> {});
                })
                .addOnFailureListener(e -> {});
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationBadge();

        timerViewModel = new ViewModelProvider(this).get(TimerViewModel.class);

        timerViewModel.isRunning.observe(this, isRunning -> {
            if (isRunning != null) {
                Intent resumeIntent = new Intent(this, MusicService.class);
                if (isRunning) {
                    resumeIntent.setAction("RESUME");
                    startService(resumeIntent);
                }
            }
        });
    }

    private View createNotificationView(Notification item, int position) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.notification_item, null);

        TextView titleView = view.findViewById(R.id.notification_item_title);
        TextView messageView = view.findViewById(R.id.notification_item_message);
        TextView timeView = view.findViewById(R.id.notification_item_time);
        View unreadIndicator = view.findViewById(R.id.unread_indicator);
        ImageButton deleteButton = view.findViewById(R.id.delete_notification_btn);

        titleView.setText(item.getTitle());
        messageView.setText(item.getMessage());
        timeView.setText(item.getTime());

        unreadIndicator.setVisibility(item.isRead() ? View.GONE : View.VISIBLE);

        if (!item.isRead()) {
            view.setBackgroundColor(getResources().getColor(R.color.light_blue, null));
        } else {
            view.setBackgroundColor(Color.TRANSPARENT);
        }

        view.setOnClickListener(v -> {
            if (item.getId() != null) {
                FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(item.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            notifications.remove(item);
                            updateNotificationBadge();

                            if (notificationPopup != null && notificationPopup.isShowing()) {
                                View popupContent = notificationPopup.getContentView();
                                LinearLayout container = popupContent.findViewById(R.id.notification_container);
                                if (container != null) {
                                    container.removeView(v);
                                }
                                notificationPopup.dismiss();
                            } else {
                                ViewGroup parent = (ViewGroup) v.getParent();
                                if (parent != null) parent.removeView(v);
                            }

                            navigateFromNotification(item);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting notification on click", e);
                            Toast.makeText(v.getContext(), "Failed to remove notification", Toast.LENGTH_SHORT).show();

                            if (notificationPopup != null && notificationPopup.isShowing()) notificationPopup.dismiss();
                            navigateFromNotification(item);
                        });
            } else {
                if (notificationPopup != null && notificationPopup.isShowing()) notificationPopup.dismiss();
                navigateFromNotification(item);
            }
        });

        deleteButton.setOnClickListener(v -> {
            if (item.getId() != null) {
                FirebaseFirestore.getInstance()
                        .collection("notifications")
                        .document(item.getId())
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            notifications.remove(item);

                            ((ViewGroup) v.getParent().getParent()).removeView((View) v.getParent());

                            updateNotificationBadge();

                            Toast.makeText(v.getContext(), "Notification deleted", Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting notification", e);
                            Toast.makeText(v.getContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                        });
            }
        });

        return view;
    }

    private void navigateFromNotification(Notification item) {
        if (item == null) return;

        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.dismiss();
        }

        String rawType = item.getType();
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        String taskId = item.getTaskId();

        runOnUiThread(() -> {
            try {
                if (!type.isEmpty()) {
                    if (type.contains("task") || type.contains("deadline") || type.contains("overdue")
                            || type.contains("remind") || type.contains("reminder")) {
                        navigateToTarget("task", taskId, rawType);
                        return;
                    }

                    if (type.contains("schedule") || type.contains("alarm") || type.contains("schedule_reminder")) {
                        navigateToTarget("schedule", taskId, rawType);
                        return;
                    }

                    if (type.contains("timer") || type.contains("pomodoro")) {
                        navigateToTarget("timer", null, rawType);
                        return;
                    }

                    if (type.contains("note") || type.contains("notes")) {
                        navigateToTarget("notes", taskId, rawType);
                        return;
                    }

                    if (type.contains("study")) {
                        navigateToTarget("study", taskId, rawType);
                        return;
                    }
                }

                if (taskId != null && !taskId.trim().isEmpty()) {
                    navigateToTarget("task", taskId, rawType);
                } else {
                    navigateToTarget("home", null, rawType);
                }
            } catch (Exception e) {
                Log.e(TAG, "navigateFromNotification error", e);
                navigateToTarget("home", null, rawType);
            }
        });
    }

    public void loadNotificationsFromDatabase() {
        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (userUid == null) {
            Log.e(TAG, "No logged-in user");
            if (notifications != null) notifications.clear();
            updateNotificationBadge();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", userUid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    notifications.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Notification notif = doc.toObject(Notification.class);
                        if (notif != null) {
                            notif.setId(doc.getId());
                            notifications.add(notif);
                        }
                    }

                    updateNotificationBadge();
                    Log.d(TAG, "Loaded " + notifications.size() + " notifications from Firestore");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading notifications", e);
                });
    }

    private void attachNotificationsListener() {
        if (notificationsRegistration != null) return;

        notificationsRegistration = notificationRepository.listenToNotificationsRealtime(new NotificationRepository.OnNotificationsChanged() {
            @Override
            public void onChanged(List<Notification> notifs) {
                runOnUiThread(() -> {
                    notifications.clear();
                    if (notifs != null) notifications.addAll(notifs);
                    updateNotificationBadge();
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Notifications realtime error", e);
            }
        });
    }

    private void detachNotificationsListener() {
        if (notificationsRegistration != null) {
            notificationsRegistration.remove();
            notificationsRegistration = null;
        }
    }

    private void updateNotificationBadge() {
        int unreadCount = 0;
        if (notifications != null) {
            for (Notification item : notifications) {
                if (!item.isRead()) {
                    unreadCount++;
                }
            }
        }

        if (notificationBadge != null) {
            if (unreadCount > 0) {
                notificationBadge.setVisibility(View.VISIBLE);
            } else {
                notificationBadge.setVisibility(View.GONE);
            }
        }

        if (notificationPopup != null && notificationPopup.isShowing()) {
            View popupView = notificationPopup.getContentView();
            TextView titleView = popupView.findViewById(R.id.notification_title);
            if (titleView != null) {
                titleView.setText("Notifications (" + unreadCount + ")");
            }
        }
    }


    private void scheduleTaskDeadlineWorker() {
        PeriodicWorkRequest taskCheckRequest =
                new PeriodicWorkRequest.Builder(TaskDeadlineWorker.class, 30, TimeUnit.MINUTES)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "taskDeadlineChecker",
                ExistingPeriodicWorkPolicy.KEEP,
                taskCheckRequest
        );
    }

    @Override
    public void onBackPressed() {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.dismiss();
            return;
        }

        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof HomeFragment) {
            showLogoutConfirmationDialog();
        } else {
            super.onBackPressed();
            updateBottomNavigationSelection();
        }
    }

    private void updateBottomNavigationSelection() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if (currentFragment instanceof HomeFragment) {
            highlightNavItem(R.id.nav_home);
            currentFragmentPosition = HOME_POSITION;
        } else if (currentFragment instanceof TasksFragment) {
            highlightNavItem(R.id.nav_tasks);
            currentFragmentPosition = TASKS_POSITION;

        } else if (currentFragment instanceof ScheduleFragment) {
            highlightNavItem(R.id.nav_schedule);
            currentFragmentPosition = SCHEDULE_POSITION;
        } else if (currentFragment instanceof Notes_MainWindow) {
            highlightNavItem(R.id.nav_notes);
            currentFragmentPosition = NOTES_POSITION;
        } else if (currentFragment instanceof StudyFragment) {
            highlightNavItem(R.id.nav_study);
            currentFragmentPosition = STUDY_POSITION;
        }

        updateFabVisibility(currentFragment);
        updateHeaderVisibility(currentFragment);
    }

    private void updateFabVisibility(Fragment fragment) {
        if (fragment instanceof TasksFragment) {
            fabAdd.show();
            fabAdd.setOnClickListener(v -> {
                ((TasksFragment) fragment).showAddTaskDialog();
            });
        } else {
            fabAdd.hide();
        }
    }


    public void hideAppBars() {
        runOnUiThread(() -> {
            View header = findViewById(R.id.app_header);
            View bottomNav = findViewById(R.id.bottom_navigation);
            if (header != null) header.setVisibility(View.GONE);
            if (bottomNav != null) bottomNav.setVisibility(View.GONE);
        });
    }

    public void restoreAppBarsIfNeeded() {
        runOnUiThread(() -> {
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);


            updateHeaderVisibility(currentFragment);
        });
    }

    private void updateHeaderVisibility(Fragment fragment) {
        if (fragment == null || headerLayout == null) return;

        TextView headerTitle = headerLayout.findViewById(R.id.home_title);
        View notificationContainer = headerLayout.findViewById(R.id.notification_btn_container);

        headerLayout.setVisibility(View.VISIBLE);

        if (headerTitle != null) {
            headerTitle.setText("");
        }
        if (notificationContainer != null) {
            notificationContainer.setVisibility(View.VISIBLE);
        }

        if (fragment instanceof HomeFragment ||
                fragment instanceof TasksFragment ||
                fragment instanceof ScheduleFragment ||
                fragment instanceof Notes_MainWindow ||
                fragment instanceof StudyFragment) {
            navBar.setVisibility(View.VISIBLE);
        } else {
            navBar.setVisibility(View.GONE);
        }
    }

    private void showLogoutConfirmationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_unsaved_changes, null);

        androidx.appcompat.app.AlertDialog logoutDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView title = dialogView.findViewById(R.id.dialogTitleText);
        TextView message = dialogView.findViewById(R.id.dialogMessageText);
        androidx.appcompat.widget.AppCompatButton noBtn = dialogView.findViewById(R.id.buttonNo);
        androidx.appcompat.widget.AppCompatButton yesBtn = dialogView.findViewById(R.id.btnYes);

        title.setText("Logout Confirmation");
        message.setText("Continuing will log you out. Are you sure you want to exit?");

        noBtn.setText("Cancel");
        yesBtn.setText("Yes, Logout");

        noBtn.setOnClickListener(v -> logoutDialog.dismiss());

        yesBtn.setOnClickListener(v -> {
            logoutDialog.dismiss();

            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(MainActivity.this, StartActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        logoutDialog.show();
    }

    private void showPopupMenu(View anchorView) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View popupView = inflater.inflate(R.layout.custom_options_popup, null);

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);

        popupWindow.setWidth((int) (180 * getResources().getDisplayMetrics().density));


        popupView.findViewById(R.id.action_edit_profile).setOnClickListener(v -> {
            popupWindow.dismiss();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
            );

                transaction.replace(R.id.fragment_container, new EditProfileFragment());
                transaction.addToBackStack(null);
                transaction.commit();

        });

        popupView.findViewById(R.id.logout_btn).setOnClickListener(v -> {
            popupWindow.dismiss();
            showLogoutConfirmationDialog();
        });


        popupWindow.showAsDropDown(anchorView, -20, 10);

    }

    private boolean loadFragmentWithPosition(Fragment fragment, int newPosition, boolean animate) {
        if (fragment != null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            if (animate && newPosition != currentFragmentPosition) {
                boolean slideRight = shouldSlideRight(currentFragmentPosition, newPosition);

                if (slideRight) {
                    transaction.setCustomAnimations(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left,
                            R.anim.slide_in_left,
                            R.anim.slide_out_right
                    );
                } else {
                    transaction.setCustomAnimations(
                            R.anim.slide_in_left,
                            R.anim.slide_out_right,
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                    );
                }
            }

            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();

            currentFragmentPosition = newPosition;
            updateFabVisibility(fragment);
            updateHeaderVisibility(fragment);

            return true;
        }
        return false;
    }

    private boolean shouldSlideRight(int fromPosition, int toPosition) {

        if (fromPosition == HOME_POSITION && toPosition == NOTES_POSITION) return true;
        if (fromPosition == NOTES_POSITION && toPosition == SCHEDULE_POSITION) return true;
        if (fromPosition == SCHEDULE_POSITION && toPosition == TASKS_POSITION) return true;
        if (fromPosition == TASKS_POSITION && toPosition == STUDY_POSITION) return true;
        if (fromPosition == HOME_POSITION && toPosition == STUDY_POSITION) return true;
        if (fromPosition == HOME_POSITION && toPosition == SCHEDULE_POSITION) return true;
        if (fromPosition == HOME_POSITION && toPosition == TASKS_POSITION) return true;

        if (fromPosition == STUDY_POSITION && toPosition == TASKS_POSITION) return false;
        if (fromPosition == TASKS_POSITION && toPosition == SCHEDULE_POSITION) return false;
        if (fromPosition == TASKS_POSITION && toPosition == HOME_POSITION) return false;
        if (fromPosition == SCHEDULE_POSITION && toPosition == NOTES_POSITION) return false;
        if (fromPosition == SCHEDULE_POSITION && toPosition == HOME_POSITION) return false;
        if (fromPosition == NOTES_POSITION && toPosition == HOME_POSITION) return false;
        if (fromPosition == STUDY_POSITION && toPosition == HOME_POSITION) return false;

        return false;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_NOTIFICATION);
            }
        }
    }

    private void setupBottomNavigation() {
        navBar.findViewById(R.id.nav_home).setOnClickListener(v -> navigateToTarget("home", null, null));
        navBar.findViewById(R.id.nav_tasks).setOnClickListener(v -> navigateToTarget("tasks", null, null));
        navBar.findViewById(R.id.nav_schedule).setOnClickListener(v -> navigateToTarget("schedule", null, null));
        navBar.findViewById(R.id.nav_notes).setOnClickListener(v -> navigateToTarget("notes", null, null));
        navBar.findViewById(R.id.nav_study).setOnClickListener(v -> navigateToTarget("study", null, null));
    }


}

