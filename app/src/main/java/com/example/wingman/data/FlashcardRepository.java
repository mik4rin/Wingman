package com.example.wingman.data;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.wingman.FlashcardItem;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlashcardRepository {
    private static final String TAG = "FlashcardRepository";
    private static final String COLLECTION_SETS = "flashcard_sets";
    private static final String COLLECTION_CARDS = "flashcards";

    private final FirebaseFirestore db;

    public FlashcardRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    public static class FlashcardSetData {
        private String id;
        private String userId;
        private String name;
        private String description;
        private int cardCount;
        private long createdAt;
        private long updatedAt;

        public FlashcardSetData() {}

        public FlashcardSetData(String userId, String name, String description) {
            this.userId = userId;
            this.name = name;
            this.description = description;
            this.cardCount = 0;
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getCardCount() { return cardCount; }
        public void setCardCount(int cardCount) { this.cardCount = cardCount; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    }

    public void createFlashcardSet(String userId, String name, String description,
                                   List<FlashcardItem> cards, OnFirestoreResultListener listener) {
        FlashcardSetData setData = new FlashcardSetData(userId, name, description);
        setData.setCardCount(cards.size());

        db.collection(COLLECTION_SETS)
                .add(setData)
                .addOnSuccessListener(documentReference -> {
                    String setId = documentReference.getId();

                    WriteBatch batch = db.batch();

                    for (int i = 0; i < cards.size(); i++) {
                        FlashcardItem card = cards.get(i);
                        card.setUserId(userId);
                        card.setSetId(setId);
                        card.setPosition(i);
                        card.setCreatedAt(System.currentTimeMillis());
                        card.setUpdatedAt(System.currentTimeMillis());

                        DocumentReference cardRef = db.collection(COLLECTION_CARDS).document();
                        card.setId(cardRef.getId());

                        Map<String, Object> cardData = flashcardItemToMap(card);
                        batch.set(cardRef, cardData);
                    }

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Flashcard set created with ID: " + setId);
                                listener.onSuccess(setId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error creating flashcards", e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating flashcard set", e);
                    listener.onError(e);
                });
    }

    public ListenerRegistration listenToFlashcardSets(String userId, OnFirestoreFlashcardsListener<FlashcardSetData> listener) {
        return db.collection(COLLECTION_SETS)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to flashcard sets", error);
                        listener.onError(error);
                        return;
                    }

                    if (querySnapshot != null) {
                        List<FlashcardSetData> sets = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            FlashcardSetData set = doc.toObject(FlashcardSetData.class);
                            set.setId(doc.getId());
                            sets.add(set);
                        }
                        listener.onSuccess(sets);
                    }
                });
    }

    public ListenerRegistration getFlashcardsInSet(String setId, OnFirestoreFlashcardsListener<FlashcardItem> listener) {
        return db.collection(COLLECTION_CARDS)
                .whereEqualTo("setId", setId)
                .orderBy("position")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error getting flashcards", error);
                        listener.onError(error);
                        return;
                    }

                    if (querySnapshot != null) {
                        List<FlashcardItem> cards = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            FlashcardItem card = doc.toObject(FlashcardItem.class);
                            card.setId(doc.getId());
                            cards.add(card);
                        }
                        listener.onSuccess(cards);
                    }
                });
    }

    public void deleteFlashcardSet(String setId, OnFirestoreResultListener listener) {
        db.collection(COLLECTION_CARDS)
                .whereEqualTo("setId", setId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WriteBatch batch = db.batch();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        batch.delete(doc.getReference());
                    }

                    DocumentReference setRef = db.collection(COLLECTION_SETS).document(setId);
                    batch.delete(setRef);

                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Flashcard set and cards deleted: " + setId);
                                listener.onSuccess(setId);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting flashcard set", e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting cards for deletion", e);
                    listener.onError(e);
                });
    }

    public void updateFlashcardSet(String setId, String name, String description,
                                   OnFirestoreResultListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("description", description);
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection(COLLECTION_SETS)
                .document(setId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Flashcard set updated: " + setId);
                    listener.onSuccess(setId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating flashcard set", e);
                    listener.onError(e);
                });
    }

    public void updateFlashcard(FlashcardItem card, OnFirestoreResultListener listener) {
        card.setUpdatedAt(System.currentTimeMillis());
        Map<String, Object> cardData = flashcardItemToMap(card);

        db.collection(COLLECTION_CARDS)
                .document(card.getId())
                .update(cardData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Flashcard updated: " + card.getId());
                    listener.onSuccess(card.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating flashcard", e);
                    listener.onError(e);
                });
    }

    private Map<String, Object> flashcardItemToMap(FlashcardItem card) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", card.getUserId());
        map.put("setId", card.getSetId());
        map.put("question", card.getQuestion());
        map.put("answer", card.getAnswer());
        map.put("position", card.getPosition());
        map.put("createdAt", card.getCreatedAt());
        map.put("updatedAt", card.getUpdatedAt());
        return map;
    }
}