package com.example.wingman;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.Note;
import com.example.wingman.data.OnFirestoreResultListener;
import com.example.wingman.databinding.NotesMainFragmentAllNotesBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.Spanned;

public class Notes_MainWindow extends Fragment implements ShareNoteDialog.ShareNoteListener {
    private static final String TAG = "Notes_MainWindow";
    private NotesMainFragmentAllNotesBinding binding;
    private NotesAdapter notesAdapter;
    private PinnedNotesAdapter pinnedNotesAdapter;
    private NotesAdapter sharedNotesAdapter;

    private Handler autoScrollHandler;
    private Runnable autoScrollRunnable;
    private int pinnedScrollPosition = 0;

    private boolean contextMenuFromPinned = false;
    private boolean contextMenuFromShared = false;
    private int selectedNotePosition = RecyclerView.NO_POSITION;
    private int pinnedSelectedPosition = RecyclerView.NO_POSITION;
    private int sharedSelectedPosition = RecyclerView.NO_POSITION;

    private NotesViewModel notesViewModel;

    private String currentSortBy = "title";
    private boolean currentAscending = true;

    private final List<Note> latestPinnedNotes = new ArrayList<>();
    private final List<Note> latestUnpinnedNotes = new ArrayList<>();
    private final List<Note> latestSharedNotes = new ArrayList<>();

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
            if (binding != null) {
                binding.pinnedSectionLabel.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
                binding.mainNotePinnedNotes.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
            }
            updateNotesEmptyPlaceholder();
        });

        notesViewModel.getSharedNotesLive().observe(getViewLifecycleOwner(), shared -> {
            List<Note> list = shared != null ? new ArrayList<>(shared) : new ArrayList<>();
            latestSharedNotes.clear();
            latestSharedNotes.addAll(list);

            if (sharedNotesAdapter != null) {
                sharedNotesAdapter.submitList(list);
            }

            boolean hasShared = !list.isEmpty();
            if (binding != null && binding.sharedSectionLabel != null && binding.mainNoteSharedNotes != null) {
                binding.sharedSectionLabel.setVisibility(hasShared ? View.VISIBLE : View.GONE);
                binding.mainNoteSharedNotes.setVisibility(hasShared ? View.VISIBLE : View.GONE);
            }
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
        int sharedCount = latestSharedNotes.size();
        int totalMatching = unpinnedCount + pinnedCount + sharedCount;

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
        NotesAdapter.OnExportClickListener exportClickListener = this::exportNoteToPdf;
        PinnedNotesAdapter.OnExportClickListener pinnedExportClickListener = this::exportNoteToPdf;

        NotesAdapter.OnShareClickListener shareClickListener = this::openShareDialog;
        PinnedNotesAdapter.OnShareClickListener pinnedShareClickListener = this::openShareDialog;

        notesAdapter = new NotesAdapter(note -> openEditorFor(note.getId()), exportClickListener, shareClickListener);
        notesAdapter.setContextMenuCallback((position, fromPinned) -> {
            selectedNotePosition = position;
            contextMenuFromPinned = false;
            contextMenuFromShared = false;
            requireActivity().openContextMenu(binding.mainNoteAllNotes);
        });

        pinnedNotesAdapter = new PinnedNotesAdapter(note -> openEditorFor(note.getId()), pinnedExportClickListener, pinnedShareClickListener);
        pinnedNotesAdapter.setContextMenuCallback((position, fromPinned) -> {
            pinnedSelectedPosition = position;
            contextMenuFromPinned = true;
            contextMenuFromShared = false;
            requireActivity().openContextMenu(binding.mainNotePinnedNotes);
        });

        sharedNotesAdapter = new NotesAdapter(note -> openEditorFor(note.getId()), exportClickListener, shareClickListener);
        sharedNotesAdapter.setContextMenuCallback((position, fromPinned) -> {
            sharedSelectedPosition = position;
            contextMenuFromPinned = false;
            contextMenuFromShared = true;
            if (binding != null && binding.mainNoteSharedNotes != null) {
                requireActivity().openContextMenu(binding.mainNoteSharedNotes);
            }
        });
    }

    private void setupRecyclerViews() {
        binding.mainNoteAllNotes.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.mainNoteAllNotes.setAdapter(notesAdapter);

        binding.mainNotePinnedNotes.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.mainNotePinnedNotes.setAdapter(pinnedNotesAdapter);

        if (binding != null && binding.mainNoteSharedNotes != null) {
            binding.mainNoteSharedNotes.setLayoutManager(new GridLayoutManager(getContext(), 2));
            binding.mainNoteSharedNotes.setAdapter(sharedNotesAdapter);
        }

        requireActivity().registerForContextMenu(binding.mainNoteAllNotes);
        requireActivity().registerForContextMenu(binding.mainNotePinnedNotes);
        if (binding != null && binding.mainNoteSharedNotes != null) {
            requireActivity().registerForContextMenu(binding.mainNoteSharedNotes);
        }
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

        final int pos = contextMenuFromPinned ? pinnedSelectedPosition : (contextMenuFromShared ? sharedSelectedPosition : selectedNotePosition);
        if (pos == RecyclerView.NO_POSITION) return;

        Note selectedNote = null;
        try {
            if (contextMenuFromPinned) {
                selectedNote = pinnedNotesAdapter.getCurrentList().get(pos);
            } else if (contextMenuFromShared) {
                selectedNote = sharedNotesAdapter.getCurrentList().get(pos);
            } else {
                selectedNote = notesAdapter.getCurrentList().get(pos);
            }
        } catch (IndexOutOfBoundsException e) {
            Log.e(TAG, "Error getting selected note for context menu", e);
            return;
        }

        if (selectedNote == null) return;

        requireActivity().getMenuInflater().inflate(R.menu.notes_context_menu, menu);

        String currentUserId = getCurrentUserUid();
        boolean isOwner = currentUserId != null && currentUserId.equals(selectedNote.getUserId());
        boolean isSharedNote = selectedNote.getSharedWith() != null && !selectedNote.getSharedWith().isEmpty();
        boolean isPinned = selectedNote.isPinned();

        if (!isOwner) {
            menu.findItem(R.id.action_pin).setVisible(false);
            menu.findItem(R.id.action_unpin).setVisible(false);
        }
        else {
            if (isSharedNote) {
                menu.findItem(R.id.action_pin).setVisible(false);
                menu.findItem(R.id.action_unpin).setVisible(false);
            } else {
                menu.findItem(R.id.action_pin).setVisible(!isPinned);
                menu.findItem(R.id.action_unpin).setVisible(isPinned);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull android.view.MenuItem item) {
        final boolean fromPinned = contextMenuFromPinned;
        final boolean fromShared = contextMenuFromShared;

        final int pos = fromPinned ? pinnedSelectedPosition : (fromShared ? sharedSelectedPosition : selectedNotePosition);

        if (pos == RecyclerView.NO_POSITION) return super.onContextItemSelected(item);

        try {
            Note selectedNote;
            if (fromPinned) {
                selectedNote = pinnedNotesAdapter.getCurrentList().get(pos);
            } else if (fromShared) {
                selectedNote = sharedNotesAdapter.getCurrentList().get(pos);
            } else {
                selectedNote = notesAdapter.getCurrentList().get(pos);
            }

            handleContextMenuAction(item.getItemId(), selectedNote);
        } finally {
            selectedNotePosition = RecyclerView.NO_POSITION;
            pinnedSelectedPosition = RecyclerView.NO_POSITION;
            sharedSelectedPosition = RecyclerView.NO_POSITION;
            contextMenuFromPinned = false;
            contextMenuFromShared = false;
        }
        return true;
    }

    private void handleContextMenuAction(int actionId, Note selectedNote) {
        String noteId = selectedNote.getId();
        String currentUserId = getCurrentUserUid();

        if (currentUserId == null) {
            Toast.makeText(requireContext(), "User session error.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isOwner = currentUserId.equals(selectedNote.getUserId());
        boolean isSharedNote = selectedNote.getSharedWith() != null && !selectedNote.getSharedWith().isEmpty();

        if (!isOwner) {
            if (actionId == R.id.action_share || actionId == R.id.action_delete ||
                    actionId == R.id.action_pin || actionId == R.id.action_unpin) {
                Toast.makeText(requireContext(), "Permission denied: You cannot perform this action on a note owned by someone else.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (isSharedNote && (actionId == R.id.action_pin || actionId == R.id.action_unpin)) {
            Toast.makeText(requireContext(), "Shared notes cannot be pinned or unpinned.", Toast.LENGTH_LONG).show();
            return;
        }

        if (actionId == R.id.action_export) {
            exportNoteToPdf(selectedNote);
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
        } else if (actionId == R.id.action_share) {
            openShareDialog(selectedNote);
        }
    }

    private void openShareDialog(Note note) {
        Log.d(TAG, "Opening custom ShareNoteDialog for note: " + note.getTitle());

        ShareNoteDialog dialog = ShareNoteDialog.newInstance(note);

        dialog.setTargetFragment(this, 0);

        dialog.show(getParentFragmentManager(), "ShareNoteDialogTag");
    }

    @Override
    public void onSharedUsersUpdated(String noteId, List<String> newSharedUserIds) {
        Log.d(TAG, "Share dialog confirmed. Updating shared users for note ID: " + noteId);
        executeNoteShareUpdate(noteId, newSharedUserIds);
    }

    private void executeNoteShareUpdate(String noteId, List<String> userIdsToShareWith) {
        if (notesViewModel == null || getContext() == null) return;

        notesViewModel.updateNoteSharedWith(noteId, userIdsToShareWith, new OnFirestoreResultListener() {
            @Override
            public void onSuccess(String id) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Note sharing updated successfully!",
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to update note sharing", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Error updating note sharing: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private void confirmDelete(String noteId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this note?")
                .setPositiveButton("Yes", (dialog, which) -> deleteNoteById(noteId))
                .setNegativeButton("No", null)
                .show();
    }

    private void exportNoteToPdf(Note note) {
        if (getContext() == null || getActivity() == null) return;

        final Context applicationContext = requireContext().getApplicationContext();
        final String noteTitle = note.getTitle();
        final String noteContents = note.getContents();
        final String TAG = Notes_MainWindow.TAG;

        new Thread(() -> {
            Spanned formattedText = androidx.core.text.HtmlCompat.fromHtml(
                    noteContents,
                    androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
            );

            PdfDocument document = new PdfDocument();
            int pageWidth = 595;
            int pageHeight = 842;
            int margin = 40;
            int contentWidth = pageWidth - 2 * margin;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);

            Canvas canvas = page.getCanvas();
            int y = margin;

            Paint paint = new Paint();
            paint.setTextSize(20f);
            paint.setFakeBoldText(true);
            canvas.drawText(noteTitle, margin, y, paint);
            y += 40;

            canvas.drawLine(margin, y, pageWidth - margin, y, paint);
            y += 20;

            TextPaint textPaint = new TextPaint();
            textPaint.setTextSize(12f);
            textPaint.setAntiAlias(true);

            StaticLayout layout;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                layout = StaticLayout.Builder.obtain(formattedText, 0, formattedText.length(), textPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(true)
                        .build();
            } else {
                layout = new StaticLayout(formattedText, textPaint, contentWidth,
                        Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, true);
            }

            int textHeight = layout.getHeight();
            int contentHeightOnFirstPage = pageHeight - y - margin;
            int contentHeightOnSubsequentPages = pageHeight - 2 * margin;

            int startOffsetVertical = 0;
            int pageNumber = 1;

            while (startOffsetVertical < textHeight) {
                int currentContentHeight;

                if (pageNumber > 1) {
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    canvas.drawColor(android.graphics.Color.WHITE);
                    y = margin;
                    currentContentHeight = contentHeightOnSubsequentPages;
                } else {
                    currentContentHeight = contentHeightOnFirstPage;
                }

                int endOffsetVertical;

                if (startOffsetVertical + currentContentHeight >= textHeight) {
                    endOffsetVertical = textHeight;
                } else {
                    int endLine = layout.getLineForVertical(startOffsetVertical + currentContentHeight);
                    endOffsetVertical = layout.getLineTop(endLine);

                    if (endOffsetVertical == startOffsetVertical) {
                        endOffsetVertical = startOffsetVertical + currentContentHeight;
                    }
                }

                canvas.save();
                canvas.translate(margin, y - startOffsetVertical);
                canvas.clipRect(0, startOffsetVertical, contentWidth, endOffsetVertical);
                layout.draw(canvas);
                canvas.restore();

                startOffsetVertical = endOffsetVertical;
                pageNumber++;
            }

            document.finishPage(page);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String safeTitle = noteTitle.replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = safeTitle + "_" + timestamp + ".pdf";

            File file = new File(applicationContext.getExternalFilesDir(null), filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                document.writeTo(fos);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(applicationContext, "Note exported successfully!", Toast.LENGTH_SHORT).show();
                        openPdf(file);
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error generating PDF: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(applicationContext, "Error exporting note.", Toast.LENGTH_LONG).show();
                    });
                }
            } finally {
                document.close();
            }

        }).start();
    }

    private void openPdf(File file) {
        if (getContext() == null || getActivity() == null) return;
        try {
            Context context = requireContext();
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
            );

            Intent openIntent = new Intent(Intent.ACTION_VIEW);
            openIntent.setDataAndType(fileUri, "application/pdf");
            openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

            startActivity(openIntent);

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "FileProvider setup error: " + e.getMessage());
            Toast.makeText(getContext(), "Could not open PDF. Check FileProvider setup.", Toast.LENGTH_LONG).show();
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(getContext(), "No app found to view PDF.", Toast.LENGTH_LONG).show();
        }
    }

    private String getCurrentUserUid() {
        if (notesViewModel == null) return null;
        return notesViewModel.getCurrentUserUid();
    }
}