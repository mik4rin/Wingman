package com.example.wingman.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchedRepository {
    private final CollectionReference schedCollection;

    public SchedRepository() {
        schedCollection = FirebaseFirestore.getInstance().collection("schedules");
    }

    // Insert new schedule
    public void insert(Sched schedule, OnFirestoreResultListener listener) {
        if (schedule.getId() == null || schedule.getId().isEmpty()) {
            // Generate Firestore document automatically
            schedCollection.add(schedule)
                    .addOnSuccessListener(documentRef -> {
                        schedule.setId(documentRef.getId());
                        listener.onSuccess(schedule.getId());
                    })
                    .addOnFailureListener(listener::onError);
        } else {
            // Use provided ID
            schedCollection.document(schedule.getId()).set(schedule)
                    .addOnSuccessListener(unused -> listener.onSuccess(schedule.getId()))
                    .addOnFailureListener(listener::onError);
        }
    }

    // Full update (replace entire doc)
    public void update(Sched schedule, OnFirestoreResultListener listener) {
        if (schedule.getId() == null) {
            listener.onError(new IllegalArgumentException("Schedule ID is missing"));
            return;
        }
        schedCollection.document(schedule.getId()).set(schedule)
                .addOnSuccessListener(unused -> listener.onSuccess(schedule.getId()))
                .addOnFailureListener(listener::onError);
    }

    // 🔹 Partial update (like Room's @Update)
    public void updatePartial(String id, Map<String, Object> updates, OnFirestoreResultListener listener) {
        if (id == null || id.isEmpty()) {
            listener.onError(new IllegalArgumentException("Schedule ID is missing"));
            return;
        }
        schedCollection.document(id).update(updates)
                .addOnSuccessListener(unused -> listener.onSuccess(id))
                .addOnFailureListener(listener::onError);
    }

    // Delete by schedule object
    public void delete(Sched schedule, OnFirestoreResultListener listener) {
        if (schedule.getId() == null) {
            listener.onError(new IllegalArgumentException("Schedule ID is missing"));
            return;
        }
        deleteById(schedule.getId(), listener);
    }

    // Delete by ID
    public void deleteById(String id, OnFirestoreResultListener listener) {
        schedCollection.document(id).delete()
                .addOnSuccessListener(unused -> listener.onSuccess(id))
                .addOnFailureListener(listener::onError);
    }

    // Get all schedules for a user
    public void getAllByUserId(String userId, OnFirestoreListListener<Sched> listener) {
        schedCollection.whereEqualTo("userId", userId)
                .orderBy("date")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Sched> schedules = toSchedList(querySnapshot);
                    listener.onSuccess(schedules);
                })
                .addOnFailureListener(listener::onError);
    }

    // Get schedule by ID
    public void getById(String id, OnFirestoreObjectListener<Sched> listener) {
        schedCollection.document(id).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        listener.onSuccess(documentSnapshot.toObject(Sched.class));
                    } else {
                        listener.onError(new Exception("Schedule not found"));
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    // Find schedule by ID (alias)
    public void findSchedById(String id, OnFirestoreObjectListener<Sched> listener) {
        getById(id, listener);
    }

    // Get schedules before or equal to a given date
    public void getSchedulesByDate(String userId, String date, OnFirestoreListListener<Sched> listener) {
        schedCollection.whereEqualTo("userId", userId)
                .whereLessThanOrEqualTo("date", date)
                .orderBy("date")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Sched> schedules = toSchedList(querySnapshot);
                    listener.onSuccess(schedules);
                })
                .addOnFailureListener(listener::onError);
    }

    // Get schedules in a date range
    public void getSchedulesByDateRange(String userId, String startDate, String endDate, OnFirestoreListListener<Sched> listener) {
        schedCollection.whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .orderBy("date")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Sched> schedules = toSchedList(querySnapshot);
                    listener.onSuccess(schedules);
                })
                .addOnFailureListener(listener::onError);
    }

    private List<Sched> toSchedList(QuerySnapshot querySnapshot) {
        List<Sched> schedules = new ArrayList<>();
        for (DocumentSnapshot doc : querySnapshot) {
            Sched schedule = doc.toObject(Sched.class);
            if (schedule != null) {
                schedule.setId(doc.getId()); // ensure ID is set
                schedules.add(schedule);
            }
        }
        return schedules;
    }
}