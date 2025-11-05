package com.example.wingman;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wingman.data.Note;
import com.example.wingman.data.NoteRepository;
import com.example.wingman.data.OnFirestoreNotesListener;
import com.example.wingman.data.OnFirestoreResultListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;

public class NotesViewModel extends AndroidViewModel {
    private static final String TAG = "NotesViewModel";
    private final NoteRepository noteRepo;
    private final MutableLiveData<List<Note>> unpinnedNotesLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Note>> pinnedNotesLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Note>> sharedNotesLive = new MutableLiveData<>(new ArrayList<>());
    private ListenerRegistration notesListenerReg;
    private ListenerRegistration sharedNotesListenerReg;
    private final List<Note> cachedAllNotes = new ArrayList<>();
    private final List<Note> cachedSharedNotes = new ArrayList<>();
    private String currentSortBy = "date";
    private boolean currentAscending = false;
    private String currentFilterQuery = "";

    public NotesViewModel(@NonNull Application application) {
        super(application);
        noteRepo = new NoteRepository();
        startListening();
    }

    public LiveData<List<Note>> getUnpinnedNotesLive() {
        return unpinnedNotesLive;
    }

    public LiveData<List<Note>> getPinnedNotesLive() {
        return pinnedNotesLive;
    }

    public LiveData<List<Note>> getSharedNotesLive() {
        return sharedNotesLive;
    }

    public String getCurrentUserUid() {
        return noteRepo.resolveCurrentUserId();
    }

    private synchronized void startListening() {
        if (notesListenerReg != null || sharedNotesListenerReg != null) return;

        notesListenerReg = noteRepo.listenToNotes(new OnFirestoreNotesListener() {
            @Override
            public void onSuccess(List<Note> notes) {
                synchronized (cachedAllNotes) {
                    cachedAllNotes.clear();
                    if (notes != null) cachedAllNotes.addAll(notes);
                }
                applySortAndFilterAndPost();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "notesListener (own) error", e);
            }
        });

        sharedNotesListenerReg = noteRepo.listenToSharedNotes(new OnFirestoreNotesListener() {
            @Override
            public void onSuccess(List<Note> notes) {
                synchronized (cachedSharedNotes) {
                    cachedSharedNotes.clear();
                    if (notes != null) cachedSharedNotes.addAll(notes);
                }
                applySortAndFilterAndPost();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "notesListener (shared) error", e);
            }
        });
    }

    public synchronized void stopListening() {
        if (notesListenerReg != null) {
            notesListenerReg.remove();
            notesListenerReg = null;
        }
        if (sharedNotesListenerReg != null) {
            sharedNotesListenerReg.remove();
            sharedNotesListenerReg = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }

    public void setSort(String sortBy, boolean ascending) {
        this.currentSortBy = (sortBy == null) ? "date" : sortBy;
        this.currentAscending = ascending;
        applySortAndFilterAndPost();
    }

    public void setFilterQuery(String query) {
        this.currentFilterQuery = (query == null) ? "" : query.trim().toLowerCase();
        applySortAndFilterAndPost();
    }

    // Inside NotesViewModel.java

    private void applySortAndFilterAndPost() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Note> ownNotesCopy;
            List<Note> sharedNotesCopy;

            synchronized (cachedAllNotes) {
                ownNotesCopy = new ArrayList<>(cachedAllNotes);
            }
            synchronized (cachedSharedNotes) {
                sharedNotesCopy = new ArrayList<>(cachedSharedNotes);
            }

            List<Note> finalUnpinned = new ArrayList<>();
            List<Note> finalPinned = new ArrayList<>();
            List<Note> finalShared = new ArrayList<>();

            java.util.Set<String> sharedNoteIds = new java.util.HashSet<>();

            for (Note n : sharedNotesCopy) {
                if (n == null) continue;
                boolean matches = currentFilterQuery.isEmpty() ||
                        safeString(n.getTitle()).toLowerCase().contains(currentFilterQuery) ||
                        safeString(n.getContents()).toLowerCase().contains(currentFilterQuery);
                if (!matches) continue;

                finalShared.add(n);
                sharedNoteIds.add(n.getId());
            }

            for (Note n : ownNotesCopy) {
                if (n == null) continue;

                if (sharedNoteIds.contains(n.getId())) continue;

                boolean matches = currentFilterQuery.isEmpty() ||
                        safeString(n.getTitle()).toLowerCase().contains(currentFilterQuery) ||
                        safeString(n.getContents()).toLowerCase().contains(currentFilterQuery);
                if (!matches) continue;

                if (n.getSharedWith() != null && !n.getSharedWith().isEmpty()) {
                    finalShared.add(n);
                }
                else if (n.isPinned()) {
                    finalPinned.add(n);
                } else {
                    finalUnpinned.add(n);
                }
            }

            Comparator<Note> comparator = "title".equalsIgnoreCase(currentSortBy)
                    ? (a, b) -> safeString(a.getTitle()).compareToIgnoreCase(safeString(b.getTitle()))
                    : (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp());

            if (!currentAscending) {
                comparator = comparator.reversed();
            }

            Collections.sort(finalUnpinned, comparator);
            Collections.sort(finalPinned, comparator);
            Collections.sort(finalShared, comparator);

            unpinnedNotesLive.postValue(finalUnpinned);
            pinnedNotesLive.postValue(finalPinned);
            sharedNotesLive.postValue(finalShared);
        });
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    public void updateNoteSharedWith(String noteId, List<String> userIdsToShareWith, OnFirestoreResultListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            noteRepo.updateSharedWith(noteId, userIdsToShareWith, listener);
        });
    }

    public void insertNote(Note note) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final String tempId = (note.getId() == null || note.getId().isEmpty())
                    ? "temp_" + System.currentTimeMillis()
                    : note.getId();

            note.setId(tempId);
            note.setTimestamp(System.currentTimeMillis());

            upsertNoteInCache(note);

            noteRepo.addNote(note, new OnFirestoreResultListener() {
                @Override
                public void onSuccess(String realId) {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        synchronized (cachedAllNotes) {
                            cachedAllNotes.removeIf(n -> tempId.equals(n.getId()));
                        }
                        note.setId(realId);
                        note.setTimestamp(System.currentTimeMillis());
                        upsertNoteInCache(note);
                        Log.d(TAG, "Note inserted: " + realId);
                    });
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "insertNote error", e);
                    removeNoteFromCache(tempId);
                }
            });
        });
    }

    public void updateNote(Note note) {
        note.setTimestamp(System.currentTimeMillis());
        upsertNoteInCache(note);

        Executors.newSingleThreadExecutor().execute(() ->
                noteRepo.updateNote(note, new OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Note updated: " + id);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "updateNote error", e);
                        noteRepo.getNoteById(note.getId(), new com.example.wingman.data.OnFirestoreNoteListener() {
                            @Override
                            public void onSuccess(Note fresh) {
                                if (fresh != null) {
                                    upsertNoteInCache(fresh);
                                }
                            }
                            @Override
                            public void onError(Exception ex) {
                                Log.e(TAG, "Failed to refresh after update failure", ex);
                            }
                        });
                    }
                })
        );
    }


    public void deleteNoteById(String noteId) {
        Note[] removedBackup = new Note[1];
        synchronized (cachedAllNotes) {
            for (Note n : cachedAllNotes) {
                if (n != null && n.getId() != null && n.getId().equals(noteId)) {
                    removedBackup[0] = n;
                    break;
                }
            }
        }

        removeNoteFromCache(noteId);

        Executors.newSingleThreadExecutor().execute(() ->
                noteRepo.deleteNote(noteId, new OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Note deleted: " + id);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "deleteNote error", e);
                        if (removedBackup[0] != null) {
                            upsertNoteInCache(removedBackup[0]);
                        } else {
                            noteRepo.getNoteById(noteId, new com.example.wingman.data.OnFirestoreNoteListener() {
                                @Override
                                public void onSuccess(Note note) {
                                    if (note != null) upsertNoteInCache(note);
                                }
                                @Override
                                public void onError(Exception ex) { /* ignore */ }
                            });
                        }
                    }
                })
        );
    }

    public void setNotePinned(String noteId, boolean pinned) {
        Note target = null;
        synchronized (cachedAllNotes) {
            for (Note n : cachedAllNotes) {
                if (n != null && n.getId() != null && n.getId().equals(noteId)) {

                    if (n.getSharedWith() != null && !n.getSharedWith().isEmpty()) {
                        Log.w(TAG, "Attempted to pin/unpin a shared note (" + noteId + "). Action blocked in ViewModel.");
                        return;
                    }

                    target = new Note(n.getId(), n.getTitle(), n.getContents(),
                            System.currentTimeMillis(), n.getUserId(), n.getMainColor(),
                            n.getAccentColor(), pinned, n.getSharedWith());
                    break;
                }
            }
        }

        if (target != null) {
            upsertNoteInCache(target);
        }

        Executors.newSingleThreadExecutor().execute(() ->
                noteRepo.updatePinnedState(noteId, pinned, new OnFirestoreResultListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Pinned state updated: " + id + " -> " + pinned);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "setNotePinned error", e);
                        noteRepo.getNoteById(noteId, new com.example.wingman.data.OnFirestoreNoteListener() {
                            @Override
                            public void onSuccess(Note note) {
                                if (note != null) upsertNoteInCache(note);
                            }
                            @Override
                            public void onError(Exception ex) { /* ignore */ }
                        });
                    }
                })
        );
    }

    public void getNoteByIdAsync(String noteId, NoteCallback cb) {
        if (noteId == null || noteId.isEmpty()) {
            if (cb != null) cb.onNoteLoaded(null);
            return;
        }

        synchronized (cachedAllNotes) {
            for (Note n : cachedAllNotes) {
                if (n != null && noteId.equals(n.getId())) {
                    if (cb != null) cb.onNoteLoaded(n);
                    return;
                }
            }
        }
        synchronized (cachedSharedNotes) {
            for (Note n : cachedSharedNotes) {
                if (n != null && noteId.equals(n.getId())) {
                    if (cb != null) cb.onNoteLoaded(n);
                    return;
                }
            }
        }

        noteRepo.getNoteById(noteId, new com.example.wingman.data.OnFirestoreNoteListener() {
            @Override
            public void onSuccess(Note note) {
                if (cb != null) cb.onNoteLoaded(note);
                if (note != null) {
                    upsertNoteInCache(note);
                }
            }

            @Override
            public void onError(Exception e) {
                if (cb != null) cb.onNoteLoadError(e);
            }
        });
    }

    public interface NoteCallback {
        void onNoteLoaded(Note note);
        void onNoteLoadError(Exception e);
    }

    private void upsertNoteInCache(Note note) {
        synchronized (cachedAllNotes) {
            for (int i = 0; i < cachedAllNotes.size(); i++) {
                if (cachedAllNotes.get(i).getId().equals(note.getId())) {
                    cachedAllNotes.set(i, note);
                    applySortAndFilterAndPost();
                    return;
                }
            }
            cachedAllNotes.add(note);
        }
        applySortAndFilterAndPost();
    }

    private void removeNoteFromCache(String noteId) {
        synchronized (cachedAllNotes) {
            for (int i = 0; i < cachedAllNotes.size(); i++) {
                if (cachedAllNotes.get(i).getId().equals(noteId)) {
                    cachedAllNotes.remove(i);
                    break;
                }
            }
        }
        applySortAndFilterAndPost();
    }
}