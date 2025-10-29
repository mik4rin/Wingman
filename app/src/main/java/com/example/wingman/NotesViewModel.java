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

    // LiveData exposed to UI
    private final MutableLiveData<List<Note>> unpinnedNotesLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Note>> pinnedNotesLive = new MutableLiveData<>(new ArrayList<>());

    // repository listener registration (single listener)
    private ListenerRegistration notesListenerReg;

    // cache + sort/filter state
    private final List<Note> cachedAllNotes = new ArrayList<>();
    private String currentSortBy = "date"; // "date" or "title"
    private boolean currentAscending = false;
    private String currentFilterQuery = "";

    public NotesViewModel(@NonNull Application application) {
        super(application);
        noteRepo = new NoteRepository();
        startListening();
    }

    // ---------- LiveData getters ----------
    public LiveData<List<Note>> getUnpinnedNotesLive() {
        return unpinnedNotesLive;
    }

    public LiveData<List<Note>> getPinnedNotesLive() {
        return pinnedNotesLive;
    }

    // ---------- Real-time listener ----------
    private synchronized void startListening() {
        if (notesListenerReg != null) return;

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
                Log.e(TAG, "notesListener error", e);
            }
        });
    }

    public synchronized void stopListening() {
        if (notesListenerReg != null) {
            notesListenerReg.remove();
            notesListenerReg = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }

    // ---------- Sorting & filtering ----------
    public void setSort(String sortBy, boolean ascending) {
        this.currentSortBy = (sortBy == null) ? "date" : sortBy;
        this.currentAscending = ascending;
        applySortAndFilterAndPost();
    }

    public void setFilterQuery(String query) {
        this.currentFilterQuery = (query == null) ? "" : query.trim().toLowerCase();
        applySortAndFilterAndPost();
    }

    private void applySortAndFilterAndPost() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Note> copy;
            synchronized (cachedAllNotes) {
                copy = new ArrayList<>(cachedAllNotes);
            }

            List<Note> unpinned = new ArrayList<>();
            List<Note> pinned = new ArrayList<>();

            for (Note n : copy) {
                if (n == null) continue;
                boolean matches = currentFilterQuery.isEmpty() ||
                        safeString(n.getTitle()).toLowerCase().contains(currentFilterQuery) ||
                        safeString(n.getContents()).toLowerCase().contains(currentFilterQuery);
                if (!matches) continue;

                if (n.isPinned()) pinned.add(n);
                else unpinned.add(n);
            }

            Comparator<Note> comparator = "title".equalsIgnoreCase(currentSortBy)
                    ? (a, b) -> safeString(a.getTitle()).compareToIgnoreCase(safeString(b.getTitle()))
                    : (a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp());

            if (!currentAscending) comparator = comparator.reversed();

            Collections.sort(unpinned, comparator);
            Collections.sort(pinned, comparator);

            unpinnedNotesLive.postValue(new ArrayList<>(unpinned));
            pinnedNotesLive.postValue(new ArrayList<>(pinned));
        });
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }

    // ---------- CRUD operations (auto-update cache) ----------
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
        // find note and update optimistically
        Note target = null;
        synchronized (cachedAllNotes) {
            for (Note n : cachedAllNotes) {
                if (n != null && n.getId() != null && n.getId().equals(noteId)) {
                    target = new Note(n.getId(), n.getTitle(), n.getContents(),
                            System.currentTimeMillis(), n.getUserId(), n.getMainColor(), n.getAccentColor(), pinned);
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

    // ---------- Cache helpers ----------
    private void updateCachedNote(Note note) {
        synchronized (cachedAllNotes) {
            for (int i = 0; i < cachedAllNotes.size(); i++) {
                if (cachedAllNotes.get(i).getId().equals(note.getId())) {
                    cachedAllNotes.set(i, note);
                    applySortAndFilterAndPost();
                    return;
                }
            }
            cachedAllNotes.add(0, note); // not found -> add
        }
        applySortAndFilterAndPost();
    }

    private void removeCachedNote(String noteId) {
        synchronized (cachedAllNotes) {
            cachedAllNotes.removeIf(n -> n.getId().equals(noteId));
        }
        applySortAndFilterAndPost();
    }

    // ---------- Async fetch single note ----------
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

    // Helper: replace or add a note in cachedAllNotes
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

    // Helper: remove note from cache by ID
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