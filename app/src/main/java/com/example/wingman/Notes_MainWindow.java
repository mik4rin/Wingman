package com.example.wingman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.Note;
import com.example.wingman.databinding.NotesMainFragmentAllNotesBinding;

import java.util.ArrayList;
import java.util.List;

public class Notes_MainWindow extends Fragment {
    private static final String TAG = "Notes_MainWindow";
    private NotesMainFragmentAllNotesBinding binding;
    private NotesAdapter notesAdapter;
    private PinnedNotesAdapter pinnedNotesAdapter;
    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private int pinnedScrollPosition = 0;
    private boolean contextMenuFromPinned = false;
    private int selectedNotePosition = RecyclerView.NO_POSITION;
    private int pinnedSelectedPosition = RecyclerView.NO_POSITION;

    private NotesViewModel notesViewModel;

    private String currentSortBy = "title";
    private boolean currentAscending = true;
    private final List<Note> latestPinnedNotes = new ArrayList<>();
    private final List<Note> latestUnpinnedNotes = new ArrayList<>();

    public Notes_MainWindow() { }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             android.os.Bundle savedInstanceState) {
        binding = NotesMainFragmentAllNotesBinding.inflate(inflater, container, false);

        binding.addnoteMainwindow.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), Notes_Edit_MainActivity.class))
        );

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        notesViewModel = new ViewModelProvider(requireActivity()).get(NotesViewModel.class);

        setupAdapters();
        setupRecyclerViews();
        setupSearch();
        setupSortingSpinner();
        setupAutoScroll();

        notesViewModel.getUnpinnedNotesLive().observe(getViewLifecycleOwner(), notes -> {
            List<Note> list = notes != null ? new ArrayList<>(notes) : new ArrayList<>();
            latestUnpinnedNotes.clear();
            latestUnpinnedNotes.addAll(list);

            notesAdapter.submitList(list);

            updateNotesEmptyPlaceholder();
        });

        notesViewModel.getPinnedNotesLive().observe(getViewLifecycleOwner(), pinned -> {
            List<Note> list = pinned != null ? new ArrayList<>(pinned) : new ArrayList<>();
            latestPinnedNotes.clear();
            latestPinnedNotes.addAll(list);

            pinnedNotesAdapter.submitList(list);
            boolean hasPinned = !list.isEmpty();
            binding.pinnedSectionLabel.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            binding.mainNotePinnedNotes.setVisibility(hasPinned ? View.VISIBLE : View.GONE);

            updateNotesEmptyPlaceholder();
        });

        notesViewModel.setSort(currentSortBy, currentAscending);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (autoScrollHandler != null && autoScrollRunnable != null) {
            autoScrollHandler.removeCallbacks(autoScrollRunnable);
        }
        binding = null;
    }

    private void updateNotesEmptyPlaceholder() {
        if (binding == null || binding.textEmptyNotes == null) return;

        int unpinnedCount = latestUnpinnedNotes.size();
        int pinnedCount = latestPinnedNotes.size();
        int totalMatching = unpinnedCount + pinnedCount;

        String query = "";
        if (binding.searchEditText != null && binding.searchEditText.getText() != null) {
            query = binding.searchEditText.getText().toString().trim();
        }

        if (totalMatching == 0) {
            if (TextUtils.isEmpty(query)) {
                binding.textEmptyNotes.setText("You have no notes yet");
                binding.textEmptyNotes.setVisibility(View.VISIBLE);
            } else {
                binding.textEmptyNotes.setText("No notes found");
                binding.textEmptyNotes.setVisibility(View.VISIBLE);
            }
        } else {
            binding.textEmptyNotes.setVisibility(View.GONE);
        }
    }

    private void setupAdapters() {
        notesAdapter = new NotesAdapter(note -> openEditorFor(note.getId()));
        notesAdapter.setContextMenuCallback((position, fromPinned) -> {
            selectedNotePosition = position;
            contextMenuFromPinned = false;
            requireActivity().openContextMenu(binding.mainNoteAllNotes);
        });

        pinnedNotesAdapter = new PinnedNotesAdapter(note -> openEditorFor(note.getId()));
        pinnedNotesAdapter.setContextMenuCallback((position, fromPinned) -> {
            pinnedSelectedPosition = position;
            contextMenuFromPinned = true;
            requireActivity().openContextMenu(binding.mainNotePinnedNotes);
        });
    }

    private void setupRecyclerViews() {
        binding.mainNoteAllNotes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.mainNoteAllNotes.setAdapter(notesAdapter);

        binding.mainNotePinnedNotes.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.mainNotePinnedNotes.setAdapter(pinnedNotesAdapter);

        requireActivity().registerForContextMenu(binding.mainNoteAllNotes);
        requireActivity().registerForContextMenu(binding.mainNotePinnedNotes);
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (notesViewModel != null) notesViewModel.setFilterQuery(s.toString().trim());
            }
        });
    }

    private void setupSortingSpinner() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        Spinner spinner = binding.spinnerOptions;
        String[] sortOptions = new String[] {
                "Sort by Title (A-Z)",
                "Sort by Title (Z-A)",
                "Sort by Date (Asc)",
                "Sort by Date (Desc)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                sortOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int sortSetting = prefs.getInt("sorting_settings", 0);
        spinner.setSelection(sortSetting);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: saveAndApplySorting(prefs.edit(), 0, "title", true); break;
                    case 1: saveAndApplySorting(prefs.edit(), 1, "title", false); break;
                    case 2: saveAndApplySorting(prefs.edit(), 2, "date", true); break;
                    case 3: saveAndApplySorting(prefs.edit(), 3, "date", false); break;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveAndApplySorting(SharedPreferences.Editor editor,
                                     int sortSetting,
                                     String sortBy,
                                     boolean ascending) {
        editor.putInt("sorting_settings", sortSetting).apply();
        currentSortBy = sortBy;
        currentAscending = ascending;
        if (notesViewModel != null) notesViewModel.setSort(sortBy, ascending);
    }

    private void openEditorFor(String noteId) {
        Intent intent = new Intent(getActivity(), Notes_Edit_MainActivity.class);
        intent.putExtra("note_id", noteId);
        startActivity(intent);
    }

    public void deleteNoteById(String noteId) {
        if (notesViewModel != null) {
            notesViewModel.deleteNoteById(noteId);
        }
    }

    public void pinNoteById(String noteId) {
        if (notesViewModel != null) {
            notesViewModel.setNotePinned(noteId, true);
        }
    }

    public void unpinNoteById(String noteId) {
        if (notesViewModel != null) {
            notesViewModel.setNotePinned(noteId, false);
        }
    }

    private void setupAutoScroll() {
        autoScrollHandler = new Handler();
        autoScrollRunnable = () -> {
            int itemCount = pinnedNotesAdapter.getItemCount();
            if (itemCount > 0) {
                pinnedScrollPosition = (pinnedScrollPosition + 1) % itemCount;
                binding.mainNotePinnedNotes.smoothScrollToPosition(pinnedScrollPosition);
            }
            autoScrollHandler.postDelayed(autoScrollRunnable, 3000);
        };
        autoScrollHandler.postDelayed(autoScrollRunnable, 3000);
    }

    @Override
    public void onCreateContextMenu(@NonNull android.view.ContextMenu menu,
                                    @NonNull View v,
                                    @Nullable android.view.ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        requireActivity().getMenuInflater().inflate(R.menu.notes_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(@NonNull android.view.MenuItem item) {
        final boolean fromPinned = contextMenuFromPinned;
        final int pos = fromPinned ? pinnedSelectedPosition : selectedNotePosition;

        if (pos == RecyclerView.NO_POSITION) return super.onContextItemSelected(item);

        try {
            Note selectedNote = fromPinned
                    ? pinnedNotesAdapter.getCurrentList().get(pos)
                    : notesAdapter.getCurrentList().get(pos);

            handleContextMenuAction(item.getItemId(), selectedNote);
        } finally {
            selectedNotePosition = RecyclerView.NO_POSITION;
            pinnedSelectedPosition = RecyclerView.NO_POSITION;
        }
        return true;
    }

    private void handleContextMenuAction(int actionId, Note selectedNote) {
        String noteId = selectedNote.getId();
        if (actionId == R.id.action_edit) {
            openEditorFor(noteId);
        } else if (actionId == R.id.action_delete) {
            confirmDelete(noteId);
        } else if (actionId == R.id.action_pin) {
            if (!selectedNote.isPinned()) {
                pinNoteById(noteId);
            }
        } else if (actionId == R.id.action_unpin) {
            if (selectedNote.isPinned()) {
                unpinNoteById(noteId);
            }
        }
    }

    private void confirmDelete(String noteId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this note?")
                .setPositiveButton("Yes", (dialog, which) -> deleteNoteById(noteId))
                .setNegativeButton("No", null)
                .show();
    }
}