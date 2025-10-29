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

    private String resolveCurrentUserId() {
        FirebaseUser u = auth.getCurrentUser();
        return (u != null) ? u.getUid() : null;
    }

    // --- Add new note ---
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

    // --- Full update (overwrite) ---
    public void updateNote(Note note, OnFirestoreResultListener listener) {
        if (note.getId() == null || note.getId().isEmpty()) {
            listener.onError(new IllegalArgumentException("Note ID is missing"));
            return;
        }
        // Always refresh timestamp so listener fires
        note.setTimestamp(System.currentTimeMillis());

        notesRef.document(note.getId())
                .set(note)
                .addOnSuccessListener(aVoid -> listener.onSuccess(note.getId()))
                .addOnFailureListener(listener::onError);
    }

    // --- Partial update (specific fields) ---
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

    // --- Pin/unpin ---
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

    // --- Delete ---
    public void deleteNote(String noteId, OnFirestoreResultListener listener) {
        if (noteId == null) {
            listener.onError(new Exception("noteId is null"));
            return;
        }
        notesRef.document(noteId).delete()
                .addOnSuccessListener(unused -> listener.onSuccess(noteId))
                .addOnFailureListener(listener::onError);
    }

    // --- Get single note ---
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
                            // Document existed but failed to parse -> treat as null result
                            Log.w(TAG, "Document exists but failed to parse Note for id: " + noteId);
                            listener.onSuccess(null);
                        }
                    } else {
                        // Document does not exist - return null as a valid "not found"
                        listener.onSuccess(null);
                    }
                })
                .addOnFailureListener(listener::onError);
    }

    // --- One-time fetch: all notes ---
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

    // --- One-time fetch: pinned notes ---
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
                        ensureNoteHasUserId(note, userId); // repair missing userId
                        notes.add(note);
                    }
                    listener.onSuccess(notes);
                })
                .addOnFailureListener(e -> {
                    // fallback if Firestore asks for composite index
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
                                        ensureNoteHasUserId(note, userId); // repair missing userId
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

    // --- One-time fetch: unpinned notes ---
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

    // --- Search (client-side filter) ---
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

    // --- Realtime: all notes ---
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

    private void ensureNoteHasUserId(Note note, String userId) {
        if (note.getUserId() == null || note.getUserId().isEmpty()) {
            note.setUserId(userId);
            // Update Firestore so other devices can see it
            notesRef.document(note.getId()).update("userId", userId)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to repair userId for note " + note.getId(), e));
        }
    }

    // --- Realtime: pinned notes (with fallback) ---
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
                    // fallback: listen to all user's notes and filter pinned locally
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
                                ensureNoteHasUserId(note, userId); // repair missing userId
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
                    ensureNoteHasUserId(note, userId); // repair missing userId
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
