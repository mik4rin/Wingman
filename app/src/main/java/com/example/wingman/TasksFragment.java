package com.example.wingman;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.wingman.data.OnFirestoreResultListener;
import com.example.wingman.data.OnFirestoreTasksListener;
import com.example.wingman.data.Task;
import com.example.wingman.data.TaskRepository;
import com.example.wingman.data.NotificationRepository;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.android.material.textfield.TextInputEditText;
import android.text.Editable;
import android.text.TextWatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class TasksFragment extends Fragment {
    private final android.os.Handler handler = new android.os.Handler();
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (taskAdapter != null) {
                requireActivity().runOnUiThread(() -> taskAdapter.notifyDataSetChanged());
            }
            handler.postDelayed(this, 60000);
        }
    };

    private Spinner spinnerSort;
    private ListView listViewTasks;
    private TextInputEditText searchView;

    private final ArrayList<Task> taskListFull = new ArrayList<>();
    private final ArrayList<Task> taskListFiltered = new ArrayList<>();
    private TaskAdapter taskAdapter;
    private TaskRepository repository;
    private String currentUserId;
    private ListenerRegistration tasksListenerRegistration;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tasks, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return view;
        }
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        repository = new TaskRepository();
        notificationRepository = new NotificationRepository();

        listViewTasks = view.findViewById(R.id.listViewTasks);
        spinnerSort = view.findViewById(R.id.spinnerSort);
        searchView = view.findViewById(R.id.search_edit_text);

        view.setOnTouchListener((v, event) -> {
            if (searchView.hasFocus()) {
                hideKeyboard();
                searchView.clearFocus();
            }
            return false;
        });

        taskAdapter = new TaskAdapter(requireContext(), taskListFiltered, new TaskAdapter.TaskListener() {
            @Override
            public void onTaskClicked(Task task) {
                TaskDetailsDialogFragment details = TaskDetailsDialogFragment.newInstance(task);
                details.setListener(new TaskDetailsDialogFragment.TaskDetailsListener() {
                    @Override
                    public void onTaskDeleted(Task t) {
                        performDeleteTask(t);
                    }

                    @Override
                    public void onTaskEditRequested(Task t) {
                        AddTaskDialogFragment editDialog = AddTaskDialogFragment.newInstance(t);
                        editDialog.setBadgeUpdateListener(() -> {
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).loadNotificationsFromDatabase();
                            }
                        });
                        editDialog.show(getChildFragmentManager(), "EditTaskDialog");
                    }
                });
                details.show(getChildFragmentManager(), "TaskDetails");
            }

            @Override
            public void onTaskLongPressed(Task task) {
                onTaskClicked(task);
            }

            @Override
            public void onTaskCheckedChanged(Task task, boolean checked) {
                task.setCompleted(!checked);
                taskAdapter.notifyDataSetChanged();
                showConfirmCompleteDialog(task, checked);
            }
        });

        listViewTasks.setAdapter(taskAdapter);

        tasksListenerRegistration = repository.listenToTasks(new OnFirestoreTasksListener() {
            @Override
            public void onSuccess(List<Task> tasks) {
                requireActivity().runOnUiThread(() -> {
                    Map<String, Task> incoming = new HashMap<>();
                    for (Task t : tasks) {
                        if (t != null && t.getId() != null) incoming.put(t.getId(), t);
                    }

                    if (tasksInitialized) {
                        String userUid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                                FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                        for (Map.Entry<String, Task> e : incoming.entrySet()) {
                            String id = e.getKey();
                            Task incomingTask = e.getValue();
                            Task prev = prevTaskMap.get(id);

                            if (prev == null) {
                                if (hasDeadline(incomingTask) && !incomingTask.isCompleted() && userUid != null) {
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, incomingTask.getId(), "task_added", t -> {});
                                }
                            } else {
                                boolean titleEqual = safeString(prev.getTitle()).equals(safeString(incomingTask.getTitle()));
                                boolean descEqual = safeString(prev.getDescription()).equals(safeString(incomingTask.getDescription()));
                                boolean deadlineEqual = prev.getDeadline() == incomingTask.getDeadline();
                                boolean completedEqual = prev.isCompleted() == incomingTask.isCompleted();

                                boolean allFieldsEqual = titleEqual && descEqual && deadlineEqual && completedEqual;
                                if (allFieldsEqual) continue;

                                boolean onlyCompletedToggled = !completedEqual && titleEqual && descEqual && deadlineEqual;
                                if (onlyCompletedToggled) continue;

                                if (userUid != null) {
                                    notificationRepository.markNotificationsReadForUserTaskType(userUid, incomingTask.getId(), "task_updated", t -> {});
                                }
                            }
                        }

                        for (String prevId : new ArrayList<>(prevTaskMap.keySet())) {
                            if (!incoming.containsKey(prevId) && userUid != null) {
                                notificationRepository.markNotificationsReadForUserTaskType(userUid, prevId, "task_deleted", t -> {});
                            }
                        }
                    }

                    taskListFull.clear();
                    taskListFull.addAll(tasks);
                    filterTasks(searchView.getText() == null ? "" : searchView.getText().toString());

                    prevTaskMap.clear();
                    prevTaskMap.putAll(incoming);
                    tasksInitialized = true;
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e("TasksFragment", "Error listening to tasks", e);
            }
        });

        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTasks(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{
                        "Title (A-Z)",
                        "Title (Z-A)",
                        "Deadline (Earliest First)",
                        "Deadline (Latest First)",
                        "Priority (Highest First)",
                        "Priority (Lowest First)"
                }
        );
        spinnerSort.setAdapter(spinnerAdapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                sortTasks(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        handler.post(updateRunnable);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(updateRunnable);
        if (tasksListenerRegistration != null) {
            tasksListenerRegistration.remove();
            tasksListenerRegistration = null;
        }
    }

    private void performDeleteTask(Task task) {
        if (task == null) return;
        repository.deleteTask(task.getId(), new OnFirestoreResultListener() {
            @Override
            public void onSuccess(String id) {
                requireActivity().runOnUiThread(() -> {
                    taskListFull.remove(task);
                    taskListFiltered.remove(task);
                    taskAdapter.notifyDataSetChanged();
                    updateEmptyPlaceholder();

                    try {
                        com.example.wingman.util.NotificationScheduler.cancelDeadline(requireContext(), id);
                    } catch (Exception ignored) {}

                    String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                    if (userUid != null) {
                        notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "task_deleted", t -> {});
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Failed to delete task", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void updateEmptyPlaceholder() {
        if (getView() == null) return;

        TextView emptyView = getView().findViewById(R.id.emptyView);
        if (emptyView == null) return;

        String query = "";
        if (searchView != null && searchView.getText() != null) {
            query = searchView.getText().toString().trim();
        }

        if (taskListFiltered.isEmpty()) {
            if (!taskListFull.isEmpty() && !query.isEmpty()) {
                emptyView.setText("No tasks found");
                emptyView.setVisibility(View.VISIBLE);
                listViewTasks.setVisibility(View.GONE);
            } else if (taskListFull.isEmpty()) {
                emptyView.setText("You have no tasks yet");
                emptyView.setVisibility(View.VISIBLE);
                listViewTasks.setVisibility(View.GONE);
            } else {
                emptyView.setText("You have no tasks yet");
                emptyView.setVisibility(View.VISIBLE);
                listViewTasks.setVisibility(View.GONE);
            }
        } else {
            emptyView.setVisibility(View.GONE);
            listViewTasks.setVisibility(View.VISIBLE);
        }
    }

    private void showConfirmCompleteDialog(Task task, boolean checked) {
        task.setCompleted(!checked);
        taskAdapter.notifyDataSetChanged();

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_confirm_delete, null);

        View btnNo = dialogView.findViewById(R.id.btnNo);
        View btnYes = dialogView.findViewById(R.id.btnYes);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            task.setCompleted(checked);
            taskAdapter.notifyDataSetChanged();

            if (checked) {
                repository.deleteTask(task.getId(), new OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        requireActivity().runOnUiThread(() -> {
                            taskListFull.remove(task);
                            taskListFiltered.remove(task);
                            taskAdapter.notifyDataSetChanged();
                            updateEmptyPlaceholder();

                            try {
                                com.example.wingman.util.NotificationScheduler.cancelDeadline(requireContext(), id);
                            } catch (Exception ignored) {}

                            String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                            if (userUid != null) {
                                notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "task_complete", t ->
                                        NotificationCenter.notifyAndLog(requireContext(), userUid, id, "task_complete",
                                                "Task completed: " + task.getTitle(),
                                                buildNotifMessage(task.getDescription()), "tasks")
                                );
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            task.setCompleted(false);
                            taskAdapter.notifyDataSetChanged();
                        });
                    }
                });
            } else {
                repository.markTaskCompleted(task.getId(), false, new OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        requireActivity().runOnUiThread(() -> {
                            task.setCompleted(false);
                            taskAdapter.notifyDataSetChanged();

                            if (task.getDeadline() > 0) {
                                try {
                                    com.example.wingman.util.NotificationScheduler.scheduleDeadlineAlarm(requireContext(), task);
                                } catch (Exception ignored) {}
                            }

                            String userUid = FirebaseAuth.getInstance().getCurrentUser() != null
                                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                            if (userUid != null) {
                                notificationRepository.markNotificationsReadForUserTaskType(userUid, id, "task_reopen", t -> {});
                            }
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            task.setCompleted(true);
                            taskAdapter.notifyDataSetChanged();
                        });
                    }
                });
            }
        });

        dialog.show();
    }

    private void hideKeyboard() {
        View view = requireActivity().getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void filterTasks(String query) {
        if (query == null) query = "";
        query = query.toLowerCase().trim();
        taskListFiltered.clear();

        if (query.isEmpty()) {
            taskListFiltered.addAll(taskListFull);
        } else {
            for (Task task : taskListFull) {
                String title = task.getTitle() == null ? "" : task.getTitle().toLowerCase();
                String desc = task.getDescription() == null ? "" : task.getDescription().toLowerCase();
                if (title.contains(query) || desc.contains(query)) {
                    taskListFiltered.add(task);
                }
            }
        }

        sortTasks(spinnerSort.getSelectedItemPosition());
        updateEmptyPlaceholder();
    }

    private void sortTasks(int sortPosition) {
        switch (sortPosition) {
            case 0: // Title A-Z
                Collections.sort(taskListFiltered, (t1, t2) ->
                        t1.getTitle().compareToIgnoreCase(t2.getTitle()));
                break;
            case 1: // Title Z-A
                Collections.sort(taskListFiltered, (t1, t2) ->
                        t2.getTitle().compareToIgnoreCase(t1.getTitle()));
                break;
            case 2: // Deadline Earliest First
                Collections.sort(taskListFiltered, (t1, t2) ->
                        Long.compare(t1.getDeadline(), t2.getDeadline()));
                break;
            case 3: // Deadline Latest First
                Collections.sort(taskListFiltered, (t1, t2) ->
                        Long.compare(t2.getDeadline(), t1.getDeadline()));
                break;
            case 4: // Priority Highest First
                TaskPriorityHeap heapHigh = new TaskPriorityHeap();
                List<Task> sortedHigh = heapHigh.heapSort(new ArrayList<>(taskListFiltered));
                taskListFiltered.clear();
                taskListFiltered.addAll(sortedHigh);
                break;
            case 5: // Priority Lowest First
                TaskPriorityHeap heapLow = new TaskPriorityHeap();
                List<Task> sortedLow = heapLow.heapSortReverse(new ArrayList<>(taskListFiltered));
                taskListFiltered.clear();
                taskListFiltered.addAll(sortedLow);
                break;
            default:
                Collections.sort(taskListFiltered, (t1, t2) ->
                        t1.getTitle().compareToIgnoreCase(t2.getTitle()));
        }
        taskAdapter.notifyDataSetChanged();
    }

    public void showAddTaskDialog() {
        AddTaskDialogFragment dialog = new AddTaskDialogFragment();
        dialog.setBadgeUpdateListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadNotificationsFromDatabase();
            }
        });
        dialog.show(getChildFragmentManager(), "AddTaskDialog");
    }

    public void showEditTaskDialog(Task task) {
        AddTaskDialogFragment dialog = AddTaskDialogFragment.newInstance(task);
        dialog.setBadgeUpdateListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).loadNotificationsFromDatabase();
            }
        });
        dialog.show(getChildFragmentManager(), "EditTaskDialog");
    }

    private String buildNotifMessage(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "No description provided.";
        }
        return description.trim();
    }

    private boolean hasDeadline(Task task) {
        return task != null && task.getDeadline() != 0;
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }
}