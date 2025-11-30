package com.example.wingman;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import android.text.TextUtils;
import de.hdodenhof.circleimageview.CircleImageView;

import com.example.wingman.data.NotificationRepository;
import com.example.wingman.data.Sched;
import com.example.wingman.data.Task;
import com.example.wingman.data.TaskRepository;
import com.example.wingman.data.OnFirestoreTasksListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.example.wingman.data.SchedRepository;

public class HomeFragment extends Fragment {
    private TextView greetingText, currentDateTextView, currentTimeTextView, motivationalText;
    private CircleImageView profileImage;
    private Spinner spinnerSort;
    private ListView listViewTasks;
    private Spinner upcomingSchedSortSpinner;
    private ListView upcomingSchedListView;
    private ArrayList<NewSchedule> upcomingSchedules = new ArrayList<>();
    private NewScheduleListAdapter upcomingAdapter;
    private TextView emptyTasksPlaceholder;
    private TextView emptySchedulesPlaceholder;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private TaskRepository repository;
    private SchedRepository schedRepo;
    private ArrayList<Task> taskList = new ArrayList<>();
    private TaskAdapter taskAdapter;
    private ListenerRegistration tasksListener;
    private ListenerRegistration schedsListener;
    private Handler timeHandler = new Handler();
    private final Map<String, Task> prevTaskMap = new HashMap<>();
    private boolean tasksInitialized = false;
    private NotificationRepository notificationRepository;

    private static class TaskPriorityHeap {
        private ArrayList<Task> heap;
        private PriorityCalculator calculator;

        public TaskPriorityHeap() {
            this.heap = new ArrayList<>();
            this.calculator = new PriorityCalculator();
        }

        public static class PriorityCalculator {
            private static final long HOUR_IN_MILLIS = 60 * 60 * 1000L;
            private static final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;

            public double calculatePriority(Task task) {
                if (task == null || task.getDeadline() == 0) {
                    return 0.0;
                }

                long now = System.currentTimeMillis();
                long deadline = task.getDeadline();
                long timeRemaining = deadline - now;

                double priorityScore;

                if (timeRemaining < 0) {
                    long hoursOverdue = Math.abs(timeRemaining) / HOUR_IN_MILLIS;
                    priorityScore = 10000 + hoursOverdue;
                } else if (timeRemaining <= DAY_IN_MILLIS) {
                    double hoursRemaining = timeRemaining / (double) HOUR_IN_MILLIS;
                    priorityScore = 9999 - (hoursRemaining * 100);
                } else if (timeRemaining <= 7 * DAY_IN_MILLIS) {
                    double daysRemaining = timeRemaining / (double) DAY_IN_MILLIS;
                    priorityScore = 999 - (daysRemaining * 100);
                } else {
                    double weeksRemaining = timeRemaining / (double) (7 * DAY_IN_MILLIS);
                    priorityScore = Math.max(0, 99 - (weeksRemaining * 10));
                }

                String course = task.getCourse();
                if (course != null && !course.isEmpty()) {
                    priorityScore += 0.0;
                }

                return priorityScore;
            }
        }

        public void insert(Task task) {
            heap.add(task);
            heapifyUp(heap.size() - 1);
        }

        public Task extractMax() {
            if (heap.isEmpty()) return null;
            Task max = heap.get(0);
            Task last = heap.remove(heap.size() - 1);
            if (!heap.isEmpty()) {
                heap.set(0, last);
                heapifyDown(0);
            }
            return max;
        }

        public void buildHeap(List<Task> tasks) {
            heap.clear();
            heap.addAll(tasks);
            for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
                heapifyDown(i);
            }
        }

        public List<Task> heapSort(List<Task> tasks) {
            buildHeap(tasks);
            List<Task> sorted = new ArrayList<>();
            while (!heap.isEmpty()) {
                sorted.add(extractMax());
            }
            return sorted;
        }

        public List<Task> heapSortReverse(List<Task> tasks) {
            List<Task> sorted = heapSort(tasks);
            List<Task> reversed = new ArrayList<>();
            for (int i = sorted.size() - 1; i >= 0; i--) {
                reversed.add(sorted.get(i));
            }
            return reversed;
        }

        private void heapifyUp(int index) {
            while (index > 0) {
                int parentIndex = (index - 1) / 2;
                if (comparePriority(heap.get(index), heap.get(parentIndex)) > 0) {
                    swap(index, parentIndex);
                    index = parentIndex;
                } else {
                    break;
                }
            }
        }

        private void heapifyDown(int index) {
            int size = heap.size();
            while (true) {
                int largest = index;
                int leftChild = 2 * index + 1;
                int rightChild = 2 * index + 2;

                if (leftChild < size && comparePriority(heap.get(leftChild), heap.get(largest)) > 0) {
                    largest = leftChild;
                }
                if (rightChild < size && comparePriority(heap.get(rightChild), heap.get(largest)) > 0) {
                    largest = rightChild;
                }
                if (largest != index) {
                    swap(index, largest);
                    index = largest;
                } else {
                    break;
                }
            }
        }

        private int comparePriority(Task t1, Task t2) {
            double priority1 = calculator.calculatePriority(t1);
            double priority2 = calculator.calculatePriority(t2);
            return Double.compare(priority1, priority2);
        }

        private void swap(int i, int j) {
            Task temp = heap.get(i);
            heap.set(i, heap.get(j));
            heap.set(j, temp);
        }

        public double getPriorityScore(Task task) {
            return calculator.calculatePriority(task);
        }
    }

    private static class SchedulePriorityHeap {
        private ArrayList<NewSchedule> heap;
        private PriorityCalculator calculator;

        public SchedulePriorityHeap() {
            this.heap = new ArrayList<>();
            this.calculator = new PriorityCalculator();
        }

        public static class PriorityCalculator {
            private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000L;
            private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

            public double calculatePriority(NewSchedule schedule) {
                if (schedule == null || schedule.getDate() == null) {
                    return 0.0;
                }

                try {
                    Date scheduleDate = sdf.parse(schedule.getDate().trim());
                    if (scheduleDate == null) return 0.0;

                    long now = System.currentTimeMillis();
                    long schedTime = scheduleDate.getTime();
                    long timeUntil = schedTime - now;

                    double priorityScore;

                    if (timeUntil < 0) {
                        return 0.0;
                    } else if (timeUntil < DAY_IN_MILLIS) {
                        priorityScore = 10000;
                    } else if (timeUntil < 2 * DAY_IN_MILLIS) {
                        priorityScore = 5000;
                    } else if (timeUntil < 3 * DAY_IN_MILLIS) {
                        double daysRemaining = timeUntil / (double) DAY_IN_MILLIS;
                        priorityScore = 5000 - (daysRemaining * 1000);
                    } else {
                        double daysRemaining = timeUntil / (double) DAY_IN_MILLIS;
                        priorityScore = Math.max(0, 1000 - (daysRemaining * 100));
                    }

                    String schedType = schedule.getSchedType();
                    if (schedType != null) {
                        priorityScore += getTypeWeight(schedType);
                    }

                    return priorityScore;
                } catch (Exception e) {
                    return 0.0;
                }
            }

            private double getTypeWeight(String schedType) {
                switch (schedType.toLowerCase()) {
                    case "exam":
                    case "quiz":
                        return 500;
                    case "meeting":
                    case "appointment":
                        return 300;
                    case "event":
                        return 100;
                    default:
                        return 0;
                }
            }
        }

        public void insert(NewSchedule schedule) {
            heap.add(schedule);
            heapifyUp(heap.size() - 1);
        }

        public NewSchedule extractMax() {
            if (heap.isEmpty()) return null;
            NewSchedule max = heap.get(0);
            NewSchedule last = heap.remove(heap.size() - 1);
            if (!heap.isEmpty()) {
                heap.set(0, last);
                heapifyDown(0);
            }
            return max;
        }

        public void buildHeap(List<NewSchedule> schedules) {
            heap.clear();
            heap.addAll(schedules);
            for (int i = (heap.size() / 2) - 1; i >= 0; i--) {
                heapifyDown(i);
            }
        }

        public List<NewSchedule> heapSort(List<NewSchedule> schedules) {
            buildHeap(schedules);
            List<NewSchedule> sorted = new ArrayList<>();
            while (!heap.isEmpty()) {
                sorted.add(extractMax());
            }
            return sorted;
        }

        public List<NewSchedule> heapSortReverse(List<NewSchedule> schedules) {
            List<NewSchedule> sorted = heapSort(schedules);
            List<NewSchedule> reversed = new ArrayList<>();
            for (int i = sorted.size() - 1; i >= 0; i--) {
                reversed.add(sorted.get(i));
            }
            return reversed;
        }

        private void heapifyUp(int index) {
            while (index > 0) {
                int parentIndex = (index - 1) / 2;
                if (comparePriority(heap.get(index), heap.get(parentIndex)) > 0) {
                    swap(index, parentIndex);
                    index = parentIndex;
                } else {
                    break;
                }
            }
        }

        private void heapifyDown(int index) {
            int size = heap.size();
            while (true) {
                int largest = index;
                int leftChild = 2 * index + 1;
                int rightChild = 2 * index + 2;

                if (leftChild < size && comparePriority(heap.get(leftChild), heap.get(largest)) > 0) {
                    largest = leftChild;
                }
                if (rightChild < size && comparePriority(heap.get(rightChild), heap.get(largest)) > 0) {
                    largest = rightChild;
                }
                if (largest != index) {
                    swap(index, largest);
                    index = largest;
                } else {
                    break;
                }
            }
        }

        private int comparePriority(NewSchedule s1, NewSchedule s2) {
            double priority1 = calculator.calculatePriority(s1);
            double priority2 = calculator.calculatePriority(s2);
            return Double.compare(priority1, priority2);
        }

        private void swap(int i, int j) {
            NewSchedule temp = heap.get(i);
            heap.set(i, heap.get(j));
            heap.set(j, temp);
        }

        public double getPriorityScore(NewSchedule schedule) {
            return calculator.calculatePriority(schedule);
        }
    }

    private String getRandomMotivationalPhrase() {
        String[] phrases = {
                "Have a productive day!",
                "You've got this!",
                "Make today amazing!",
                "Stay focused and positive!",
                "Time to accomplish great things!",
                "Believe in yourself today!",
                "Today is full of possibilities!",
                "Let's make progress together!",
                "Your goals are within reach!",
                "Start strong, finish stronger!",
                "Keep pushing forward!",
                "Success starts with action!",
                "Dream big, work hard!",
                "Turn your plans into reality!",
                "Make every moment count!"
        };
        int randomIndex = new java.util.Random().nextInt(phrases.length);
        return phrases[randomIndex];
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        FloatingActionButton fabADD = view.findViewById(R.id.fabHome);
        Button btnNewNote = view.findViewById(R.id.btnNewNote);
        Button btnNewSchedule = view.findViewById(R.id.btnNewSchedule);
        Button btnNewTask = view.findViewById(R.id.btnNewTask);

        emptyTasksPlaceholder = view.findViewById(R.id.emptyTasksPlaceholder);
        emptySchedulesPlaceholder = view.findViewById(R.id.emptySchedulesPlaceholder);

        spinnerSort = view.findViewById(R.id.spinnerSort2);
        profileImage = view.findViewById(R.id.profile_image);
        greetingText = view.findViewById(R.id.greeting_text);
        motivationalText = view.findViewById(R.id.motivational_text);
        listViewTasks = view.findViewById(R.id.listViewItems);
        if (listViewTasks == null) {
            listViewTasks = view.findViewById(R.id.listViewTasks);
        }
        currentDateTextView = view.findViewById(R.id.current_date);
        currentTimeTextView = view.findViewById(R.id.current_time);

        if (motivationalText != null) {
            motivationalText.setText(getRandomMotivationalPhrase());
        }

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        repository = new TaskRepository();
        schedRepo = new SchedRepository();
        notificationRepository = new NotificationRepository();

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{ "Sort by Priority (Highest First)", "Sort by Priority (Lowest First)" }
        );

        spinnerSort.setAdapter(sortAdapter);
        spinnerSort.setSelection(0);
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) { updateListDisplay(); }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateDate();
        startClock();
        setupUpcomingSchedules(view);

        taskAdapter = new TaskAdapter(requireContext(), taskList, new TaskAdapter.TaskListener() {
            @Override
            public void onTaskClicked(Task task) {}
            @Override
            public void onTaskLongPressed(Task task) {}
            @Override
            public void onTaskCheckedChanged(Task task, boolean checked) {}
        }) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View viewInside = super.getView(position, convertView, parent);
                View checkbox = viewInside.findViewById(R.id.checkBoxComplete);
                if (checkbox != null) {
                    checkbox.setVisibility(View.GONE);
                }
                Task task = getItem(position);
                if (task != null) {
                    TextView deadlineTextView = viewInside.findViewById(R.id.textViewDeadline);
                    if (deadlineTextView != null) deadlineTextView.setText(getDeadlineText(task));
                    if (isTaskOverdue(task)) {
                        viewInside.setBackgroundResource(R.drawable.blue_border);
                        setTextColorRecursively(viewInside,
                                getContext().getResources().getColor(android.R.color.holo_red_light));
                    } else {
                        viewInside.setBackgroundResource(R.drawable.blue_border);
                        setTextColorRecursively(viewInside,
                                getContext().getResources().getColor(R.color.blue));
                    }
                }
                return viewInside;
            }

            private void setTextColorRecursively(View view, int color) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(color);
                } else if (view instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) view;
                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                        setTextColorRecursively(viewGroup.getChildAt(i), color);
                    }
                }
            }
        };

        listViewTasks.setAdapter(taskAdapter);
        attachTaskListener();

        fabADD.setOnClickListener(v -> {
            boolean open = !fabADD.isActivated();
            fabADD.setActivated(open);
            int visibility = open ? View.VISIBLE : View.GONE;
            btnNewNote.setVisibility(visibility);
            btnNewSchedule.setVisibility(visibility);
            btnNewTask.setVisibility(visibility);
            fabADD.setRotation(open ? 45f : 0f);
        });

        btnNewNote.setOnClickListener(v -> startActivity(new Intent(getActivity(), Notes_Edit_MainActivity.class)));

        btnNewSchedule.setOnClickListener(v -> {
            NewScheduleDialogFragment dialog = new NewScheduleDialogFragment();
            dialog.setScheduleListener(new NewScheduleDialogFragment.ScheduleListener() {
                @Override
                public void onScheduleSaved(String id,
                                            String title,
                                            String description,
                                            String date,
                                            String schedType) {

                    String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                            : null;

                    if (uid == null) {
                        return;
                    }

                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    if (TextUtils.isEmpty(id)) {
                        String newId = db.collection("schedules").document().getId();
                        Map<String, Object> scheduleMap = new HashMap<>();
                        scheduleMap.put("id", newId);
                        scheduleMap.put("userId", uid);
                        scheduleMap.put("title", title);
                        scheduleMap.put("description", description);
                        scheduleMap.put("date", date);
                        scheduleMap.put("schedType", schedType);
                        scheduleMap.put("timestamp", System.currentTimeMillis());

                        db.collection("schedules").document(newId)
                                .set(scheduleMap)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(requireContext(), "Schedule saved", Toast.LENGTH_SHORT).show();

                                    String actor = getActorName();
                                    String notifTitle = buildNotifTitle(actor, "added", "schedule", title, schedType != null ? schedType : "");
                                    String notifMsg = buildNotifMessage(description);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(requireContext(), "Failed to save schedule: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show());
                    } else {
                        Map<String, Object> scheduleMap = new HashMap<>();
                        scheduleMap.put("title", title);
                        scheduleMap.put("description", description);
                        scheduleMap.put("date", date);
                        scheduleMap.put("schedType", schedType);

                        db.collection("schedules").document(id)
                                .update(scheduleMap)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(requireContext(), "Schedule updated", Toast.LENGTH_SHORT).show();

                                    String actor = getActorName();
                                    String notifTitle = buildNotifTitle(actor, "updated", "schedule", title, schedType != null ? schedType : "");
                                    String notifMsg = buildNotifMessage(description);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(requireContext(), "Failed to update schedule: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onScheduleDeleted(String idToDelete) {
                    if (TextUtils.isEmpty(idToDelete)) return;
                    FirebaseFirestore.getInstance().collection("schedules").document(idToDelete)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                                String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                                        ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                                String actor = getActorName();
                                String notifTitle = buildNotifTitle(actor, "deleted", "schedule", "", "");
                                String notifMsg = "Schedule removed.";
                            })
                            .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
            dialog.show(getChildFragmentManager(), "NewScheduleDialog");
        });

        btnNewTask.setOnClickListener(v -> {
            AddTaskDialogFragment dialog = new AddTaskDialogFragment();
            dialog.setBadgeUpdateListener(() -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).loadNotificationsFromDatabase();
                }
            });
            dialog.show(getChildFragmentManager(), "AddTaskDialog");
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        PermissionHelper.checkAndRequestStoragePermission(this, () -> {
            displayUserNameAndProfilePic();
            PermissionHelper.checkExactAlarmPermission(requireActivity());
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timeHandler.removeCallbacksAndMessages(null);
        if (tasksListener != null) tasksListener.remove();
        if (schedsListener != null) schedsListener.remove();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        PermissionHelper.handleStoragePermissionResult(
                requestCode,
                grantResults,
                () -> {
                    displayUserNameAndProfilePic();
                    PermissionHelper.checkExactAlarmPermission(requireActivity());
                },
                () -> {
                    profileImage.setImageResource(R.drawable.default_profile_picture);
                }
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        PermissionHelper.handleExactAlarmResult(requireActivity(), requestCode);
    }

    private boolean isTaskOverdue(Task task) {
        long deadline = task.getDeadline();
        if (deadline == 0) return false;
        long currentTime = System.currentTimeMillis();
        return currentTime > deadline && !task.isCompleted();
    }

    private String getDeadlineText(Task task) {
        long deadline = task.getDeadline();
        if (deadline == 0) return "No deadline";
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String deadlineDate = sdf.format(new Date(deadline));
        if (currentTime > deadline && !task.isCompleted()) {
            return "Overdue - " + deadlineDate;
        } else {
            return deadlineDate;
        }
    }

    private void updateDate() {
        String currentDate = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(new Date());
        currentDateTextView.setText(currentDate);
    }

    private void startClock() {
        timeHandler.postDelayed(new Runnable() {
            @Override public void run() {
                String currentTime = new SimpleDateFormat("hh:mm", Locale.getDefault()).format(new Date());
                currentTimeTextView.setText(currentTime);
                timeHandler.postDelayed(this, 60000);
            }
        }, 0);
    }

    private void displayUserNameAndProfilePic() {
        String uid = getCurrentUid();
        if (uid == null) {
            greetingText.setText("Hi, User");
            profileImage.setImageResource(R.drawable.default_profile_picture);
            return;
        }

        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        greetingText.setText("Hi, User");
                        profileImage.setImageResource(R.drawable.default_profile_picture);
                        return;
                    }

                    String username = doc.getString("username");
                    String profilePic = doc.getString("profilePicture");

                    if (!isAdded() || getContext() == null) return;

                    greetingText.setText("Hi, " + (username != null ? username : "User"));

                    if (username != null) {
                        getContext().getSharedPreferences("loginPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("username", username)
                                .apply();
                    }

                    if (profilePic == null || profilePic.equals("default") || profilePic.equals("default_profile_picture.jpg")) {
                        profileImage.setImageResource(R.drawable.default_profile_picture);
                        return;
                    }

                    String lower = profilePic.toLowerCase(Locale.ROOT);
                    boolean looksLikeUri =
                            lower.startsWith("http://") || lower.startsWith("https://") ||
                                    lower.startsWith("content://") || lower.startsWith("file://") ||
                                    lower.contains("/storage/") || lower.contains("media/") || lower.contains("android.resource:");
                    if (looksLikeUri) {
                        if (PermissionHelper.hasStoragePermission(requireContext())) {
                            try {
                                profileImage.setImageURI(Uri.parse(profilePic));
                            } catch (Exception e) {
                                profileImage.setImageResource(R.drawable.default_profile_picture);
                            }
                        } else {
                            profileImage.setImageResource(R.drawable.default_profile_picture);
                        }
                    } else {
                        try {
                            byte[] bytes = Base64.decode(profilePic, Base64.DEFAULT);
                            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (bmp != null) profileImage.setImageBitmap(bmp);
                            else profileImage.setImageResource(R.drawable.default_profile_picture);
                        } catch (Exception e) {
                            profileImage.setImageResource(R.drawable.default_profile_picture);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("HomeFragment", "Failed to load user profile: " + e.getMessage());
                    greetingText.setText("Hi, User");
                    profileImage.setImageResource(R.drawable.default_profile_picture);
                });
    }

    private boolean hasDeadline(Task task) {
        return task.getDeadline() != 0;
    }

    private String buildNotifTitle(String actor, String action, String itemType, String itemTitle, String extra) {
        StringBuilder sb = new StringBuilder();
        if (actor == null || actor.isEmpty()) actor = "A user";
        sb.append(actor).append(" ").append(action).append(" ").append(itemType);
        if (itemTitle != null && !itemTitle.isEmpty()) {
            sb.append(": ").append(itemTitle);
        }
        if (extra != null && !extra.isEmpty()) {
            sb.append(" (").append(extra).append(")");
        }
        return sb.toString();
    }

    private String buildNotifMessage(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "No description provided.";
        }
        return description.trim();
    }

    private void setupUpcomingSchedules(View view) {
        upcomingSchedSortSpinner = view.findViewById(R.id.upcoming_sched_sort_spinner);
        upcomingSchedListView = view.findViewById(R.id.upcoming_sched_lv);
        emptySchedulesPlaceholder = view.findViewById(R.id.emptySchedulesPlaceholder);

        upcomingSchedules = new ArrayList<>();
        upcomingAdapter = new NewScheduleListAdapter(requireContext(), upcomingSchedules);
        upcomingSchedListView.setAdapter(upcomingAdapter);
        upcomingSchedListView.setItemsCanFocus(false);
        upcomingSchedListView.setClickable(true);

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "Sort by Priority (Highest First)",
                        "Sort by Priority (Lowest First)"
                }
        );
        if (upcomingSchedSortSpinner != null) {
            upcomingSchedSortSpinner.setAdapter(sortAdapter);
            upcomingSchedSortSpinner.setSelection(0);
            upcomingSchedSortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    sortUpcomingSchedules(position);
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        upcomingSchedListView.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= upcomingSchedules.size()) return;
        });

        loadUpcomingSchedules();
        updateSchedulesPlaceholder();
    }

    private String getActorName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String dn = user.getDisplayName();
            if (!TextUtils.isEmpty(dn)) return dn;
        }
        try {
            String pref = requireActivity().getSharedPreferences("loginPrefs", getContext().MODE_PRIVATE)
                    .getString("username", null);
            if (!TextUtils.isEmpty(pref)) return pref;
        } catch (Exception ignored) {}
        return "A user";
    }

    private void sortUpcomingSchedules(int position) {
        if (upcomingSchedules == null || upcomingSchedules.isEmpty()) return;

        switch (position) {
            case 0:
                SchedulePriorityHeap heapHigh = new SchedulePriorityHeap();
                List<NewSchedule> sortedHigh = heapHigh.heapSort(new ArrayList<>(upcomingSchedules));
                upcomingSchedules.clear();
                upcomingSchedules.addAll(sortedHigh);
                break;

            case 1:
                SchedulePriorityHeap heapLow = new SchedulePriorityHeap();
                List<NewSchedule> sortedLow = heapLow.heapSortReverse(new ArrayList<>(upcomingSchedules));
                upcomingSchedules.clear();
                upcomingSchedules.addAll(sortedLow);
                break;
        }

        upcomingAdapter.updateList(upcomingSchedules);
    }

    private Date parseDateSafely(NewSchedule s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(s.getDate());
        } catch (Exception e) {
            return new Date(0);
        }
    }

    private void loadUpcomingSchedules() {
        String uid = getCurrentUid();
        if (uid == null) return;

        if (schedsListener != null) schedsListener.remove();

        schedsListener = firestore.collection("schedules")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((qs, e) -> {
                    if (e != null) {
                        Log.e("HomeFragment", "Schedules listener error: " + e.getMessage());
                        return;
                    }
                    if (qs == null) return;
                    ArrayList<NewSchedule> newList = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        Sched s = doc.toObject(Sched.class);
                        if (s == null) continue;
                        s.setId(doc.getId());
                        if (isUpcoming(s.getDate())) {
                            NewSchedule ns = new NewSchedule(s.getId(), s.getUserId(), s.getTitle(), s.getDescription(), s.getDate(), s.getSchedType(), s.getTimestamp());
                            newList.add(ns);
                        }
                    }
                    requireActivity().runOnUiThread(() -> {
                        upcomingSchedules.clear();
                        upcomingSchedules.addAll(newList);
                        upcomingAdapter.updateList(upcomingSchedules);
                        sortUpcomingSchedules(upcomingSchedSortSpinner != null ? upcomingSchedSortSpinner.getSelectedItemPosition() : 0);
                        updateSchedulesPlaceholder();
                    });

                });
    }

    private void updateSchedulesPlaceholder() {
        if (emptySchedulesPlaceholder == null || upcomingSchedListView == null) return;

        if (upcomingSchedules.isEmpty()) {
            upcomingSchedListView.setVisibility(View.GONE);
            emptySchedulesPlaceholder.setVisibility(View.VISIBLE);
        } else {
            upcomingSchedListView.setVisibility(View.VISIBLE);
            emptySchedulesPlaceholder.setVisibility(View.GONE);
        }
    }

    private boolean isUpcoming(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            Date target = sdf.parse(dateString.trim());
            if (target == null) return false;
            long now = System.currentTimeMillis();
            long in3days = now + (3L * 24 * 60 * 60 * 1000);
            Date todayStart = sdf.parse(sdf.format(new Date(now)));
            long start = (todayStart != null) ? todayStart.getTime() : now;
            long t = target.getTime();
            return t >= start && t <= in3days;
        } catch (Exception ex) {
            Log.w("HomeFragment", "isUpcoming parse error for date: " + dateString + " -> " + ex.getMessage());
            return false;
        }
    }

    private void attachTaskListener() {
        String uid = getCurrentUid();
        if (uid == null) return;
        if (tasksListener != null) tasksListener.remove();

        tasksListener = repository.listenToTasks(new OnFirestoreTasksListener() {
            @Override
            public void onSuccess(java.util.List<Task> tasks) {
                requireActivity().runOnUiThread(() -> {
                    Map<String, Task> incoming = new HashMap<>();
                    for (Task t : tasks) {
                        if (t != null && t.getId() != null) incoming.put(t.getId(), t);
                    }

                    if (tasksInitialized) {
                        String userUid = getCurrentUid();
                        String actor = getActorName();

                        for (Map.Entry<String, Task> e : incoming.entrySet()) {
                            String id = e.getKey();
                            Task incomingTask = e.getValue();
                            Task prev = prevTaskMap.get(id);

                            if (prev == null) {
                                if (hasDeadline(incomingTask) && !incomingTask.isCompleted()) {
                                    String title = buildNotifTitle(actor, "added", "task", incomingTask.getTitle(),
                                            incomingTask.getCourse() != null ? incomingTask.getCourse() : "");
                                    String msg = buildNotifMessage(incomingTask.getDescription());
                                    if (userUid != null) {
                                        notificationRepository.markNotificationsReadForUserTaskType(userUid, incomingTask.getId(), "task_added", result -> {
                                        });
                                    }
                                }
                            } else {
                                boolean titleEqual = safeString(prev.getTitle()).equals(safeString(incomingTask.getTitle()));
                                boolean descEqual = safeString(prev.getDescription()).equals(safeString(incomingTask.getDescription()));
                                boolean deadlineEqual = prev.getDeadline() == incomingTask.getDeadline();
                                boolean completedEqual = prev.isCompleted() == incomingTask.isCompleted();

                                boolean allFieldsEqual = titleEqual && descEqual && deadlineEqual && completedEqual;
                                if (allFieldsEqual) continue;

                                boolean onlyCompletedToggled = !completedEqual && titleEqual && descEqual && deadlineEqual;
                                if (onlyCompletedToggled) {
                                    continue;
                                }

                                String title = buildNotifTitle(actor, "updated", "task", incomingTask.getTitle(),
                                        incomingTask.getCourse() != null ? incomingTask.getCourse() : "");
                                String msg = buildNotifMessage(incomingTask.getDescription());
                                if (userUid != null) {
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, incomingTask.getId(), "task_updated", result -> {
                                    });
                                }
                            }
                        }
                    }

                    taskList.clear();
                    for (Task t : tasks) {
                        if (hasDeadline(t) && !t.isCompleted()) {
                            taskList.add(t);
                        }
                    }
                    updateListDisplay();
                    updateTasksPlaceholder();

                    prevTaskMap.clear();
                    prevTaskMap.putAll(incoming);
                    tasksInitialized = true;
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e("HomeFragment", "Tasks listener error", e);
            }
        });
    }

    private void updateTasksPlaceholder() {
        if (emptyTasksPlaceholder == null || listViewTasks == null) return;

        if (taskList.isEmpty()) {
            listViewTasks.setVisibility(View.GONE);
            emptyTasksPlaceholder.setVisibility(View.VISIBLE);
        } else {
            listViewTasks.setVisibility(View.VISIBLE);
            emptyTasksPlaceholder.setVisibility(View.GONE);
        }
    }

    private void showConfirmCompleteDialog(Task task, boolean checked) {
        if (task == null) return;

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_confirm_delete, null);

        View btnNo = dialogView.findViewById(R.id.btnNo);
        View btnYes = dialogView.findViewById(R.id.btnYes);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnNo.setOnClickListener(v -> {
            dialog.dismiss();
            task.setCompleted(!checked);
            taskAdapter.notifyDataSetChanged();
        });

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            if (checked) {
                repository.deleteTask(task.getId(), new com.example.wingman.data.OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        requireActivity().runOnUiThread(() -> {
                            taskList.remove(task);
                            taskAdapter.notifyDataSetChanged();
                            try { com.example.wingman.util.NotificationScheduler.cancelDeadline(requireContext(), id); } catch (Exception ignored) {}

                            String userUid = getCurrentUid();
                            String actor = getActorName();

                            String notifTitle = buildNotifTitle(actor, "completed", "task",
                                    task.getTitle(), task.getCourse() != null ? task.getCourse() : "");
                            String notifMsg = buildNotifMessage(task.getDescription());

                            if (userUid != null) {
                                notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "task_complete", result -> {
                                });
                            }

                            Toast.makeText(requireContext(), "Task marked as done and deleted.", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Failed to delete task: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            task.setCompleted(false);
                            taskAdapter.notifyDataSetChanged();
                        });
                    }
                });
            } else {
                repository.markTaskCompleted(task.getId(), false, new com.example.wingman.data.OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        requireActivity().runOnUiThread(() -> {
                            task.setCompleted(false);
                            taskAdapter.notifyDataSetChanged();
                            if (task.getDeadline() > 0) {
                                try { com.example.wingman.util.NotificationScheduler.scheduleDeadlineAlarm(requireContext(), task); } catch (Exception ignored) {}
                            }

                            String userUid = getCurrentUid();
                            String actor = getActorName();

                            String notifTitle = buildNotifTitle(actor, "reopened", "task",
                                    task.getTitle(), task.getCourse() != null ? task.getCourse() : "");
                            String notifMsg = buildNotifMessage(task.getDescription());

                            if (userUid != null) {
                                notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "task_reopen", result -> {
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Failed to reopen task: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            task.setCompleted(true);
                            taskAdapter.notifyDataSetChanged();
                        });
                    }
                });
            }
        });

        dialog.show();
    }

    private void updateListDisplay() {
        int sortOption = spinnerSort.getSelectedItemPosition();

        switch (sortOption) {
            case 0:
                TaskPriorityHeap heapHigh = new TaskPriorityHeap();
                List<Task> sortedHigh = heapHigh.heapSort(new ArrayList<>(taskList));
                taskList.clear();
                taskList.addAll(sortedHigh);
                break;

            case 1:
                TaskPriorityHeap heapLow = new TaskPriorityHeap();
                List<Task> sortedLow = heapLow.heapSortReverse(new ArrayList<>(taskList));
                taskList.clear();
                taskList.addAll(sortedLow);
                break;
        }

        taskAdapter.notifyDataSetChanged();
    }

    private Date getTaskDate(Task task) {
        if (task.getDeadline() == 0) return null;
        return new Date(task.getDeadline());
    }

    private String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }
}