package com.example.wingman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.ClassSched;
import com.example.wingman.data.ClassSchedRepository;
import com.example.wingman.data.NotificationRepository;
import com.example.wingman.data.Sched;
import com.example.wingman.data.SchedRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class ScheduleFragment extends Fragment {
    private boolean isFabMenuOpen = false;
    private WebView webView;
    private final List<NewSchedule> schedules = new ArrayList<>();
    private NewScheduleAdapter adapter;
    private SchedRepository schedRepo;
    private ClassSchedRepository classSchedRepo;
    private FirebaseFirestore firestore;
    private ListenerRegistration schedListener;
    private String currentUid;
    private final Map<String, NewSchedule> prevSchedMap = new HashMap<>();
    private boolean schedulesInitialized = false;
    private int latestClassSchedCount = 0;
    private SearchView schedSearchView;
    private NotificationRepository notificationRepository;

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

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        schedRepo = new SchedRepository();
        classSchedRepo = new ClassSchedRepository();
        notificationRepository = new NotificationRepository();

        firestore = FirebaseFirestore.getInstance();

        currentUid = SessionManager.getUserId(requireContext());
        if (currentUid == null) return view;

        webView = view.findViewById(R.id.wv_scheduler);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_asset/scheduler.html");

        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                super.onPageFinished(v, url);
                loadClassSchedulesIntoWebView();
            }
        });

        webView.addJavascriptInterface(
                new SchedulerClassInterface(getContext(), webView),
                "AndroidInterface"
        );

        RecyclerView recycler = view.findViewById(R.id.sched_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NewScheduleAdapter(requireContext(), schedules, this::openEditGeneralSchedule);
        recycler.setAdapter(adapter);
        loadSchedulesFromFirestore();

        Button addClassFab = view.findViewById(R.id.schedule_addBtn);

        addClassFab.setOnClickListener(v -> {
            ScheduleClassDialog dlg = new ScheduleClassDialog();
            dlg.setScheduleDialogListener(
                    (title, startHour, startMinute, endHour, endMinute,
                     dayIndex, outlineColor, bodyColor, alarmEnabled) -> {

                        // Create temporary schedule for collision check
                        ClassSched newSched = new ClassSched();
                        newSched.setUserId(currentUid);
                        newSched.setTitle(title);
                        newSched.setStartHour(startHour);
                        newSched.setStartMinute(startMinute);
                        newSched.setEndHour(endHour);
                        newSched.setEndMinute(endMinute);
                        newSched.setDayIndex(dayIndex);
                        newSched.setMainColor(outlineColor);
                        newSched.setAccentColor(bodyColor);
                        newSched.setAlarmEnabled(alarmEnabled);

                        // Get existing schedules and check for collisions
                        classSchedRepo.getAllForUser(getTask -> {
                            if (!getTask.isSuccessful()) return;

                            List<ClassSched> existingSchedules = getTask.getResult();
                            if (existingSchedules == null) existingSchedules = new ArrayList<>();

                            // Check for collisions
                            boolean hasCollision = ScheduleCollisionDetector.hasClassScheduleCollision(
                                    newSched, existingSchedules, null);

                            List<ClassSched> finalExistingSchedules = existingSchedules;
                            requireActivity().runOnUiThread(() -> {
                                if (hasCollision) {
                                    // Show collision warning
                                    List<ClassSched> conflicts = ScheduleCollisionDetector.getConflictingClassSchedules(
                                            newSched, finalExistingSchedules, null);
                                    String conflictMsg = ScheduleCollisionDetector.createClassConflictMessage(
                                            conflicts, dayIndex);

                                    showCollisionDialog(conflictMsg);
                                } else {
                                    // No collision, proceed with insertion
                                    insertClassSchedule(newSched);
                                }
                            });
                        });
                    }
            );
            dlg.show(getChildFragmentManager(), "ScheduleClassDialog");
        });

        Button addGeneralFab = view.findViewById(R.id.lower_sched_addBtn);
        addGeneralFab.setOnClickListener(v -> showNewScheduleDialog());

        Spinner sortSpinner = view.findViewById(R.id.sched_type_filter);
        String[] sortOptions = {
                "Sort by Title (A-Z)",
                "Sort by Title (Z-A)",
                "Sort by Date (Asc)",
                "Sort by Date (Desc)",
                "Sort by Priority (Highest First)",
                "Sort by Priority (Lowest First)"
        };
        ArrayAdapter<String> spnAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                sortOptions
        );
        spnAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(spnAdapter);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view1, int pos, long id) {
                switch (pos) {
                    case 0: adapter.sortByTitleAsc(); break;
                    case 1: adapter.sortByTitleDesc(); break;
                    case 2: adapter.sortByDateAsc(); break;
                    case 3: adapter.sortByDateDesc(); break;
                    case 4: sortByPriorityHighest(); break;
                    case 5: sortByPriorityLowest(); break;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        schedSearchView = view.findViewById(R.id.sched_searchview);
        schedSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String q) {
                adapter.filter(q);
                updateNoSchedulesPlaceholder();
                return true;
            }
            @Override public boolean onQueryTextChange(String q) {
                adapter.filter(q);
                updateNoSchedulesPlaceholder();
                return true;
            }
        });

        schedSearchView.setOnClickListener(v -> { schedSearchView.setIconified(false); schedSearchView.requestFocus(); });
        schedSearchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> { if (hasFocus) schedSearchView.setIconified(false); });

        FloatingActionButton fabClock = view.findViewById(R.id.fabSchedule);
        fabClock.setOnClickListener(v -> {
            isFabMenuOpen = !isFabMenuOpen;
            int visibility = isFabMenuOpen ? View.VISIBLE : View.GONE;
            addClassFab.setVisibility(visibility);
            addGeneralFab.setVisibility(visibility);
            fabClock.setRotation(isFabMenuOpen ? 45f : 0f);
        });

        return view;
    }

    private void insertClassSchedule(ClassSched newSched) {
        classSchedRepo.insert(newSched, task -> {
            requireActivity().runOnUiThread(() -> {
                if (newSched.isAlarmEnabled()) {
                    ScheduleUtils.scheduleAlarm(requireContext(), newSched, true);
                }

                String safeTitle = newSched.getTitle().replace("\\", "\\\\").replace("'", "\\'");
                String js = String.format(
                        "addScheduleBlock(%d, %d, %d, %d, %d, '%s', '%s', '%s', %b);",
                        newSched.getDayIndex(),
                        newSched.getStartHour(), newSched.getStartMinute(),
                        newSched.getEndHour(), newSched.getEndMinute(),
                        safeTitle,
                        newSched.getMainColor(),
                        newSched.getAccentColor(),
                        newSched.isAlarmEnabled()
                );
                webView.evaluateJavascript(js, null);

                latestClassSchedCount++;
                updateNoSchedulesPlaceholder();

                Toast.makeText(requireContext(), "Class schedule added successfully", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void sortByPriorityHighest() {
        SchedulePriorityHeap heap = new SchedulePriorityHeap();
        List<NewSchedule> sorted = heap.heapSort(new ArrayList<>(schedules));
        schedules.clear();
        schedules.addAll(sorted);
        adapter.updateData(schedules);
    }

    private void sortByPriorityLowest() {
        SchedulePriorityHeap heap = new SchedulePriorityHeap();
        List<NewSchedule> sorted = heap.heapSortReverse(new ArrayList<>(schedules));
        schedules.clear();
        schedules.addAll(sorted);
        adapter.updateData(schedules);
    }

    private void loadClassSchedulesIntoWebView() {
        classSchedRepo.getAllForUser(new OnCompleteListener<List<ClassSched>>() {
            @Override
            public void onComplete(com.google.android.gms.tasks.Task<List<ClassSched>> task) {
                if (!task.isSuccessful() || task.getResult() == null) {
                    latestClassSchedCount = 0;
                    requireActivity().runOnUiThread(() -> updateNoSchedulesPlaceholder());
                    return;
                }
                List<ClassSched> classScheds = task.getResult();

                latestClassSchedCount = classScheds.size();
                requireActivity().runOnUiThread(() -> updateNoSchedulesPlaceholder());

                for (ClassSched sched : classScheds) {
                    String safeTitle = sched.getTitle().replace("\\", "\\\\").replace("'", "\\'");
                    String js = String.format(
                            "addScheduleBlock(%d, %d, %d, %d, %d, '%s', '%s', '%s', %b);",
                            sched.getDayIndex(),
                            sched.getStartHour(), sched.getStartMinute(),
                            sched.getEndHour(), sched.getEndMinute(),
                            safeTitle,
                            sched.getMainColor(),
                            sched.getAccentColor(),
                            sched.isAlarmEnabled()
                    );

                    final String jsFinal = js;
                    requireActivity().runOnUiThread(() -> {
                        webView.evaluateJavascript(jsFinal, null);
                        if (sched.isAlarmEnabled()) {
                            ScheduleUtils.scheduleAlarm(requireContext(), sched, false);
                        }
                    });
                }
            }
        });
    }

    private void loadSchedulesFromFirestore() {
        if (schedListener != null) schedListener.remove();

        schedListener = firestore.collection("schedules")
                .whereEqualTo("userId", currentUid)
                .orderBy("date")
                .addSnapshotListener((querySnapshot, e) -> {
                    if (e != null) {
                        Log.e("ScheduleFragment", "Listener error: " + e.getMessage());
                        return;
                    }

                    if (querySnapshot == null) return;

                    Map<String, NewSchedule> incoming = new HashMap<>();
                    List<NewSchedule> updatedList = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Sched s = doc.toObject(Sched.class);
                        if (s != null) {
                            s.setId(doc.getId());
                            NewSchedule ns = new NewSchedule(
                                    s.getId(),
                                    s.getUserId(),
                                    s.getTitle(),
                                    s.getDescription(),
                                    s.getDate(),
                                    s.getSchedType(),
                                    s.getTimestamp()
                            );
                            updatedList.add(ns);
                            incoming.put(ns.getId(), ns);
                        }
                    }

                    if (schedulesInitialized) {
                        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                        for (Map.Entry<String, NewSchedule> en : incoming.entrySet()) {
                            String id = en.getKey();
                            NewSchedule incomingSched = en.getValue();
                            NewSchedule prev = prevSchedMap.get(id);

                            boolean incomingDue = isDateReached(incomingSched.getDate());
                            boolean prevDue = prev != null && isDateReached(prev.getDate());

                            if (prev == null) {
                                if (incomingDue && userUid != null) {
                                    String title = "Schedule added: " + incomingSched.getTitle();
                                    String msg = buildNotifMessage(incomingSched.getDescription());
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "schedule_added", t -> {
                                        NotificationCenter.notifyAndLog(requireContext(), userUid, id, "schedule_added", title, msg, "schedules");
                                    });
                                }
                            } else {
                                boolean changed = !safeString(prev.getTitle()).equals(safeString(incomingSched.getTitle()))
                                        || !safeString(prev.getDescription()).equals(safeString(incomingSched.getDescription()))
                                        || !safeString(prev.getDate()).equals(safeString(incomingSched.getDate()))
                                        || !safeString(prev.getSchedType()).equals(safeString(incomingSched.getSchedType()));

                                if (changed && incomingDue && userUid != null) {
                                    String title = "Schedule updated: " + incomingSched.getTitle();
                                    String msg = buildNotifMessage(incomingSched.getDescription());
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "schedule_updated", t -> {
                                        NotificationCenter.notifyAndLog(requireContext(), userUid, id, "schedule_updated", title, msg, "schedules");
                                    });
                                }
                            }
                        }

                        for (String prevId : new ArrayList<>(prevSchedMap.keySet())) {
                            if (!incoming.containsKey(prevId) && userUid != null) {
                                NewSchedule deleted = prevSchedMap.get(prevId);
                                if (deleted != null && isDateReached(deleted.getDate())) {
                                    String title = "Schedule deleted: " + deleted.getTitle();
                                    String msg = "Schedule removed.";
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, prevId, "schedule_deleted", t -> {
                                        NotificationCenter.notifyAndLog(requireContext(), userUid, prevId, "schedule_deleted", title, msg, "schedules");
                                    });
                                }
                            }
                        }
                    }

                    requireActivity().runOnUiThread(() -> {
                        schedules.clear();
                        schedules.addAll(updatedList);
                        adapter.updateData(schedules);

                        updateNoSchedulesPlaceholder();
                        checkAndNotifySchedules();
                    });

                    prevSchedMap.clear();
                    prevSchedMap.putAll(incoming);
                    schedulesInitialized = true;
                });
    }

    private void openEditGeneralSchedule(NewSchedule item, int position) {
        NewScheduleDialogFragment dlg = new NewScheduleDialogFragment();
        dlg.setSchedule(item);
        dlg.setScheduleListener(new NewScheduleDialogFragment.ScheduleListener() {
            @Override
            public void onScheduleSaved(String id, String title, String desc, String date, String type) {
                item.setTitle(title);
                item.setDescription(desc);
                item.setDate(date);
                item.setSchedType(type);
                adapter.notifyItemChanged(position);

                if (id != null) {
                    com.example.wingman.data.Sched dbSched = new com.example.wingman.data.Sched();
                    dbSched.setId(id);
                    dbSched.setUserId(currentUid);
                    dbSched.setTitle(title);
                    dbSched.setDescription(desc);
                    dbSched.setDate(date);
                    dbSched.setSchedType(type);
                    dbSched.setTimestamp(System.currentTimeMillis());
                    schedRepo.update(dbSched, new com.example.wingman.data.OnFirestoreResultListener() {
                        @Override public void onSuccess(String id) { }
                        @Override public void onError(Exception e) { }
                    });
                }
            }

            @Override
            public void onScheduleDeleted(String id) {
                if (id != null) {
                    adapter.removeScheduleById(id);
                    schedRepo.deleteById(id, new com.example.wingman.data.OnFirestoreResultListener() {
                        @Override public void onSuccess(String id) { }
                        @Override public void onError(Exception e) { }
                    });
                } else {
                    adapter.removeScheduleById(item.getId());
                }

                updateNoSchedulesPlaceholder();
            }
        });
        dlg.show(getChildFragmentManager(), "EditGeneralScheduleDialog");
    }

    public void showNewScheduleDialog() {
        NewScheduleDialogFragment dlg = new NewScheduleDialogFragment();
        dlg.setScheduleListener(new NewScheduleDialogFragment.ScheduleListener() {
            @Override
            public void onScheduleSaved(String id, String title, String desc, String date, String type) {
                if (id == null || id.isEmpty()) {
                    // Creating new schedule - check for collisions
                    NewSchedule tmp = new NewSchedule(currentUid, title, desc, date, type, System.currentTimeMillis());

                    // Check for collision
                    boolean hasCollision = ScheduleCollisionDetector.hasGeneralScheduleCollision(
                            tmp, schedules, null);

                    if (hasCollision) {
                        List<NewSchedule> conflicts = ScheduleCollisionDetector.getConflictingGeneralSchedules(
                                tmp, schedules, null);
                        String conflictMsg = ScheduleCollisionDetector.createGeneralConflictMessage(conflicts);
                        showCollisionDialog(conflictMsg);
                        return;
                    }

                    // No collision, proceed with insertion
                    Sched dbSched = new Sched(null, currentUid, title, desc, date, type, System.currentTimeMillis());
                    schedRepo.insert(dbSched, new com.example.wingman.data.OnFirestoreResultListener() {
                        @Override
                        public void onSuccess(String newId) {
                            tmp.setId(newId);
                            requireActivity().runOnUiThread(() -> {
                                schedules.add(0, tmp);
                                adapter.addSchedule(tmp);
                                updateNoSchedulesPlaceholder();
                            });
                        }
                        @Override public void onError(Exception e) { }
                    });
                } else {
                    // Updating existing schedule - check for collisions excluding current
                    NewSchedule tmp = new NewSchedule(id, currentUid, title, desc, date, type, System.currentTimeMillis());
                    boolean hasCollision = ScheduleCollisionDetector.hasGeneralScheduleCollision(
                            tmp, schedules, id);

                    if (hasCollision) {
                        List<NewSchedule> conflicts = ScheduleCollisionDetector.getConflictingGeneralSchedules(
                                tmp, schedules, id);
                        String conflictMsg = ScheduleCollisionDetector.createGeneralConflictMessage(conflicts);
                        showCollisionDialog(conflictMsg);
                        return;
                    }

                    // No collision, proceed with update
                    for (int i = 0; i < schedules.size(); i++) {
                        NewSchedule s = schedules.get(i);
                        if (s.getId() != null && s.getId().equals(id)) {
                            s.setTitle(title);
                            s.setDescription(desc);
                            s.setDate(date);
                            s.setSchedType(type);
                            adapter.notifyItemChanged(i);
                            Sched dbSched = new Sched(id, currentUid, title, desc, date, type, System.currentTimeMillis());
                            schedRepo.update(dbSched, new com.example.wingman.data.OnFirestoreResultListener() {
                                @Override public void onSuccess(String id) { }
                                @Override public void onError(Exception e) { }
                            });
                            break;
                        }
                    }
                }
            }

            @Override
            public void onScheduleDeleted(String id) {
                if (id != null) {
                    adapter.removeScheduleById(id);
                    schedRepo.deleteById(id, new com.example.wingman.data.OnFirestoreResultListener() {
                        @Override public void onSuccess(String id) {}
                        @Override public void onError(Exception e) { }
                    });
                }
                updateNoSchedulesPlaceholder();
            }
        });
        dlg.show(getChildFragmentManager(), "NewScheduleDialog");
    }

    private void showCollisionDialog(String message) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_unsaved_changes, null);

        AlertDialog collisionDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        TextView title = dialogView.findViewById(R.id.dialogTitleText);
        TextView messageText = dialogView.findViewById(R.id.dialogMessageText);
        androidx.appcompat.widget.AppCompatButton noBtn = dialogView.findViewById(R.id.buttonNo);
        androidx.appcompat.widget.AppCompatButton yesBtn = dialogView.findViewById(R.id.btnYes);

        title.setText("Schedule Conflict");
        messageText.setText(message);

        noBtn.setVisibility(View.GONE);

        yesBtn.setText("OK");
        yesBtn.setOnClickListener(v -> collisionDialog.dismiss());

        collisionDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (schedListener != null) schedListener.remove();
    }

    private String buildNotifMessage(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "No description provided.";
        }
        return description.trim();
    }

    private String safeString(String s) { return s == null ? "" : s; }

    private void updateNoSchedulesPlaceholder() {
        View root = getView();
        if (root == null) return;

        TextView empty = root.findViewById(R.id.text_no_schedules);
        if (empty == null) return;

        int displayed = adapter == null ? 0 : adapter.getItemCount();
        int totalStored = (schedules == null ? 0 : schedules.size()) + latestClassSchedCount;

        String query = "";
        if (schedSearchView != null) {
            CharSequence q = schedSearchView.getQuery();
            query = (q == null) ? "" : q.toString().trim();
        }

        if (displayed == 0) {
            if (totalStored > 0 && !query.isEmpty()) {
                empty.setText("No schedules found");
                empty.setVisibility(View.VISIBLE);
            } else if (totalStored == 0) {
                empty.setText("You have no schedules yet");
                empty.setVisibility(View.VISIBLE);
            } else {
                empty.setText("You have no schedules yet");
                empty.setVisibility(View.VISIBLE);
            }
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private boolean isDateReached(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            sdf.setLenient(false);
            java.util.Date parsed = sdf.parse(dateStr.trim());
            if (parsed == null) return false;

            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(parsed);
            c.set(java.util.Calendar.HOUR_OF_DAY, 0);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);

            long schedStartMillis = c.getTimeInMillis();
            return System.currentTimeMillis() >= schedStartMillis;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkAndNotifySchedules() {
        if (schedules == null || schedules.isEmpty()) return;

        Calendar now = Calendar.getInstance();
        long todayMillis = now.getTimeInMillis();

        for (NewSchedule sched : schedules) {
            if (sched == null || sched.getDate() == null) continue;

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date schedDate = sdf.parse(sched.getDate());
                if (schedDate == null) continue;

                long schedMillis = schedDate.getTime();

                if (todayMillis >= schedMillis && !hasNotifiedToday(sched.getId())) {
                    String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                            : null;

                    if (userUid == null) continue;

                    String title = "Schedule Reminder: " + sched.getTitle();
                    String message = (sched.getDescription() == null || sched.getDescription().isEmpty())
                            ? "Your scheduled event is happening today."
                            : sched.getDescription();

                    NotificationCenter.notifyAndLog(
                            requireContext(),
                            userUid,
                            sched.getId(),
                            "schedule_due",
                            title,
                            message,
                            "schedules"
                    );

                    markNotifiedToday(sched.getId());
                }
            } catch (Exception e) {
                Log.e("ScheduleFragment", "Error checking schedule date", e);
            }
        }
    }

    private boolean hasNotifiedToday(String scheduleId) {
        if (scheduleId == null) return true;
        Context context = requireContext();
        String prefsName = "schedule_notifications";
        String key = scheduleId + "_date";

        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        String lastNotified = prefs.getString(key, null);

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        return today.equals(lastNotified);
    }

    private void markNotifiedToday(String scheduleId) {
        if (scheduleId == null) return;
        Context context = requireContext();
        String prefsName = "schedule_notifications";
        String key = scheduleId + "_date";

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        prefs.edit().putString(key, today).apply();
    }
}