package com.example.wingman;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.wingman.data.OnFirestoreTasksListener;
import com.example.wingman.data.Task;
import com.example.wingman.data.TaskRepository;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TaskDeadlineWorker extends Worker {
    private static final String TAG = "TaskDeadlineWorker";

    public TaskDeadlineWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        TaskRepository repo = new TaskRepository();
        final CountDownLatch latch = new CountDownLatch(1);

        repo.getTasks(new OnFirestoreTasksListener() {
            @Override
            public void onSuccess(List<Task> tasks) {
                long now = System.currentTimeMillis();
                for (Task t : tasks) {
                    try {
                        if (t == null || t.isCompleted()) continue;
                        long deadline = t.getDeadline();
                        if (deadline <= 0) continue;

                        long diff = deadline - now;

                        if (diff < 0) {
                            continue;
                        }

                        if (diff <= TimeUnit.MINUTES.toMillis(15)) {
                            // within 15 minutes
                            NotificationUtils.notifyNearlyDue(getApplicationContext(), t.getTitle(), t.getUserId(), t.getId());
                        } else if (diff <= TimeUnit.HOURS.toMillis(1)) {
                            // within 1 hour
                            NotificationUtils.notifyNearlyDue(getApplicationContext(), t.getTitle(), t.getUserId(), t.getId());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing task in worker", e);
                    }
                }
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error fetching tasks in worker", e);
                latch.countDown();
            }
        });

        try {
            latch.await(25, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return Result.success();
    }
}