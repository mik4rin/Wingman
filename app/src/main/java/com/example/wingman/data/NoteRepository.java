package com.example.wingman.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NoteRepository {
    private static final String TAG = "NoteRepository";
    private final FirebaseFirestore db;
    private final CollectionReference notesRef;
    private final FirebaseAuth auth;

    public NoteRepository() {
        db = FirebaseFirestore.getInstance();
        notesRef = db.collection("notes");
        auth = FirebaseAuth.getInstance();
    }

    public String resolveCurrentUserId() {
        FirebaseUser u = auth.getCurrentUser();
        return (u != null) ? u.getUid() : null;
    }

    public void addNote(Note note, OnFirestoreResultListener listener) {
        if (note == null) {
            listener.onError(new Exception("Note is null"));
            return;
        }

        String userId = note.getUserId();
        FirebaseUser fUser = auth.getCurrentUser();
        if ((userId == null || userId.trim().isEmpty()) && fUser != null) {
            userId = fUser.getUid();
            note.setUserId(userId);
        }

        if (userId == null || userId.trim().isEmpty()) {
            listener.onError(new Exception("No user id available: ensure the user is signed in"));
            return;
        }

        String noteId = notesRef.document().getId();
        note.setId(noteId);
        note.setTimestamp(System.currentTimeMillis());

        notesRef.document(noteId).set(note)
                .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                .addOnFailureListener(listener::onError);
    }

    public void updateNote(Note note, OnFirestoreResultListener listener) {
        if (note.getId() == null || note.getId().isEmpty()) {
            listener.onError(new IllegalArgumentException("Note ID is missing"));
            return;
        }

        java.util.Map<String, Object> updates = note.toMap();

        updates.remove("userId");

        updates.put("timestamp", System.currentTimeMillis());

        notesRef.document(note.getId())
                .update(updates)
                .addOnSuccessListener(aVoid -> listener.onSuccess(note.getId()))
                .addOnFailureListener(listener::onError);
    }

    public void updateNoteFields(String noteId, Map<String, Object> updates, OnFirestoreResultListener listener) {
        if (noteId == null) {
            listener.onError(new Exception("noteId is null"));
            return;
        }
        updates.put("timestamp", System.currentTimeMillis());

        notesRef.document(noteId).update(updates)
                .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                .addOnFailureListener(listener::onError);
    }

    public void updatePinnedState(String noteId, boolean pinned, OnFirestoreResultListener listener) {
        if (noteId == null) {
            listener.onError(new Exception("noteId is null"));
            return;
        }
        notesRef.document(noteId)
                .update("pinned", pinned, "timestamp", System.currentTimeMillis())
                .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                .addOnFailureListener(listener::onError);
    }

    // Inside com.example.wingman.data.NoteRepository.java

    public void updateSharedWith(String noteId, List<String> sharedUserIds, OnFirestoreResultListener listener) {
        String currentUserId = resolveCurrentUserId();
        if (noteId == null || currentUserId == null) {
            listener.onError(new Exception("User not signed in or noteId is null"));
            return;
        }
        final List<String> finalSharedUserIds;
        if (sharedUserIds == null) {
            finalSharedUserIds = new ArrayList<>();
        } else {
            finalSharedUserIds = sharedUserIds;
        }

        getNoteById(noteId, new OnFirestoreNoteListener() {
            @Override
            public void onSuccess(Note note) {
                if (note == null || !currentUserId.equals(note.getUserId())) {
                    Log.w(TAG, "User " + currentUserId + " attempted to update sharedWith on note " + noteId + " but is not the owner.");
                    listener.onError(new SecurityException("Permission denied. Only the note owner can change sharing settings."));
                    return;
                }

                notesRef.document(noteId)
                        .update("sharedWith", finalSharedUserIds, "timestamp", System.currentTimeMillis())
                        .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                        .addOnFailureListener(listener::onError);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public void deleteNote(String noteId, OnFirestoreResultListener listener) {
        String currentUserId = resolveCurrentUserId();
        if (noteId == null || currentUserId == null) {
            listener.onError(new Exception("User not signed in or noteId is null"));
            return;
        }

        getNoteById(noteId, new OnFirestoreNoteListener() {
            @Override
            public void onSuccess(Note note) {
                if (note == null) {
                    listener.onSuccess(noteId);
                    return;
                }

                if (!currentUserId.equals(note.getUserId())) {
                    Log.w(TAG, "User " + currentUserId + " attempted to delete note " + noteId + " but is not the owner.");
                    listener.onError(new SecurityException("Permission denied. Only the note owner can delete the note."));
                    return;
                }

                notesRef.document(noteId).delete()
                        .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                        .addOnFailureListener(listener::onError);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    public void getNoteById(String noteId, OnFirestoreNoteListener listener) {
        if (noteId == null) {
            listener.onError(new Exception("noteId is null"));
            return;
        }
        notesRef.document(noteId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Note note = doc.toObject(Note.class);
                        if (note != null) {
                            note.setId(doc.getId());
                            listener.onSuccess(note);
                        } else {
                            Log.w(TAG, "Document exists but failed to parse Note for id: " + noteId);
                            listener.onSuccess(null);
                        }
                    } else {
                        listener.onSuccess(null);
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    public void getNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) { listener.onError(new Exception("User not signed in")); return; }

        notesRef.whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Note> notes = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Note note = doc.toObject(Note.class);
                        if (note == null) note = new Note();
                        note.setId(doc.getId());
                        ensureNoteHasUserId(note, userId);
                        notes.add(note);
                    }
                    listener.onSuccess(notes);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching notes", e);
                    listener.onError(e);
                });
    }

    public void getPinnedNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User not signed in"));
            return;
        }

        Query q = notesRef.whereEqualTo("userId", userId)
                .whereEqualTo("pinned", true)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Note> notes = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Note note = doc.toObject(Note.class);
                        if (note == null) note = new Note();
                        note.setId(doc.getId());
                        ensureNoteHasUserId(note, userId);
                        notes.add(note);
                    }
                    listener.onSuccess(notes);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseFirestoreException &&
                            ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        notesRef.whereEqualTo("userId", userId)
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(qs -> {
                                    List<Note> notes = new ArrayList<>();
                                    for (QueryDocumentSnapshot doc : qs) {
                                        Note note = doc.toObject(Note.class);
                                        if (note == null) note = new Note();
                                        note.setId(doc.getId());
                                        ensureNoteHasUserId(note, userId);
                                        if (note.isPinned()) notes.add(note);
                                    }
                                    listener.onSuccess(notes);
                                })
                                .addOnFailureListener(listener::onError);
                    } else {
                        Log.e(TAG, "Error fetching pinned notes", e);
                        listener.onError(e);
                    }
                });
    }

    public void getUnpinnedNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User not signed in"));
            return;
        }

        Query q = notesRef.whereEqualTo("userId", userId)
                .whereEqualTo("pinned", false)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        q.get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Note> notes = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Note note = doc.toObject(Note.class);
                        if (note == null) note = new Note();
                        note.setId(doc.getId());
                        if (note.getUserId() == null) note.setUserId(userId);
                        notes.add(note);
                    }
                    listener.onSuccess(notes);
                })
                .addOnFailureListener(listener::onError);
    }

    public void searchNotes(String keyword, OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User not signed in"));
            return;
        }

        notesRef.whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Note> notes = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Note note = doc.toObject(Note.class);
                        if (note == null) continue;
                        note.setId(doc.getId());
                        if (note.getUserId() == null) note.setUserId(userId);

                        String title = (note.getTitle() == null) ? "" : note.getTitle();
                        String contents = (note.getContents() == null) ? "" : note.getContents();
                        if (title.toLowerCase().contains(keyword.toLowerCase()) ||
                                contents.toLowerCase().contains(keyword.toLowerCase())) {
                            notes.add(note);
                        }
                    }
                    listener.onSuccess(notes);
                })
                .addOnFailureListener(listener::onError);
    }

    public ListenerRegistration listenToNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) { listener.onError(new Exception("User not signed in")); return () -> { }; }

        Query q = notesRef.whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        return q.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                listener.onError(e);
                return;
            }
            List<Note> notes = new ArrayList<>();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Note note = doc.toObject(Note.class);
                    if (note == null) note = new Note();
                    note.setId(doc.getId());
                    ensureNoteHasUserId(note, userId);
                    notes.add(note);
                }
            }
            listener.onSuccess(notes);
        });
    }

    public ListenerRegistration listenToSharedNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User not signed in"));
            return () -> {};
        }

        Query q = notesRef.whereArrayContains("sharedWith", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        return q.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Error listening to shared notes", e);
                listener.onError(e);
                return;
            }
            List<Note> notes = new ArrayList<>();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Note note = doc.toObject(Note.class);
                    if (note == null) note = new Note();
                    note.setId(doc.getId());
                    notes.add(note);
                }
            }
            listener.onSuccess(notes);
        });
    }

    private void ensureNoteHasUserId(Note note, String userId) {
        if (note.getUserId() == null || note.getUserId().isEmpty()) {
            note.setUserId(userId);
            notesRef.document(note.getId()).update("userId", userId)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to repair userId for note " + note.getId(), e));
        }
    }

    public ListenerRegistration listenToPinnedNotes(OnFirestoreNotesListener listener) {
        String userId = resolveCurrentUserId();
        if (userId == null) {
            listener.onError(new Exception("User not signed in"));
            return () -> { /* noop */ };
        }

        Query primary = notesRef.whereEqualTo("userId", userId)
                .whereEqualTo("pinned", true)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        final ListenerRegistration[] regs = new ListenerRegistration[2];

        regs[0] = primary.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "primary pinned listener error: " + e.getMessage());
                if (e instanceof FirebaseFirestoreException &&
                        ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    Query fallback = notesRef.whereEqualTo("userId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                    regs[1] = fallback.addSnapshotListener((snap2, ex2) -> {
                        if (ex2 != null) {
                            Log.e(TAG, "pinned fallback listener error", ex2);
                            listener.onError(ex2);
                            return;
                        }
                        List<Note> pinned = new ArrayList<>();
                        if (snap2 != null) {
                            for (QueryDocumentSnapshot doc : snap2) {
                                Note note = doc.toObject(Note.class);
                                if (note == null) note = new Note();
                                note.setId(doc.getId());
                                ensureNoteHasUserId(note, userId);
                                if (note.isPinned()) pinned.add(note);
                            }
                        }
                        listener.onSuccess(pinned);
                    });
                } else {
                    listener.onError(e);
                }
                return;
            }

            List<Note> notes = new ArrayList<>();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    Note note = doc.toObject(Note.class);
                    if (note == null) note = new Note();
                    note.setId(doc.getId());
                    ensureNoteHasUserId(note, userId);
                    notes.add(note);
                }
            }
            listener.onSuccess(notes);
        });

        return () -> {
            if (regs[0] != null) regs[0].remove();
            if (regs[1] != null) regs[1].remove();
        };
    }
}