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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.Note;
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
        NotesAdapter.OnExportClickListener exportClickListener = this::exportNoteToPdf;
        PinnedNotesAdapter.OnExportClickListener pinnedExportClickListener = this::exportNoteToPdf;

        notesAdapter = new NotesAdapter(note -> openEditorFor(note.getId()), exportClickListener);
        notesAdapter.setContextMenuCallback((position, fromPinned) -> {
            selectedNotePosition = position;
            contextMenuFromPinned = false;
            requireActivity().openContextMenu(binding.mainNoteAllNotes);
        });

        pinnedNotesAdapter = new PinnedNotesAdapter(note -> openEditorFor(note.getId()), pinnedExportClickListener);
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
        if (actionId == R.id.action_export) {
            // Export to PDF
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

            // 5. Draw content page by page
            int textHeight = layout.getHeight();
            // Calculate available height for page 1 (which has a header)
            int contentHeightOnFirstPage = pageHeight - y - margin;
            // Calculate available height for subsequent pages (full page)
            int contentHeightOnSubsequentPages = pageHeight - 2 * margin;

            int startOffsetVertical = 0;
            int pageNumber = 1;

            while (startOffsetVertical < textHeight) {
                int currentContentHeight;

                if (pageNumber > 1) {
                    // --- Setup new page ---
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    canvas.drawColor(android.graphics.Color.WHITE); // Clear new page
                    y = margin; // Reset Y position to top margin
                    currentContentHeight = contentHeightOnSubsequentPages;
                } else {
                    // --- Use first page ---
                    currentContentHeight = contentHeightOnFirstPage;
                }

                // --- Calculate drawing bounds ---
                int endOffsetVertical;

                // Check if the rest of the text fits on this page
                if (startOffsetVertical + currentContentHeight >= textHeight) {
                    // We are on the last page. Set the bottom to the total text height.
                    endOffsetVertical = textHeight;
                } else {
                    // We are not on the last page. Find the line at the bottom of the page.
                    int endLine = layout.getLineForVertical(startOffsetVertical + currentContentHeight);
                    endOffsetVertical = layout.getLineTop(endLine);

                    // Check for a single line taller than the page (infinite loop)
                    if (endOffsetVertical == startOffsetVertical) {
                        // The line is too tall. Force clip at the bottom of the page.
                        endOffsetVertical = startOffsetVertical + currentContentHeight;
                    }
                }

                // --- Draw the clipped content ---
                canvas.save();
                canvas.translate(margin, y - startOffsetVertical);
                canvas.clipRect(0, startOffsetVertical, contentWidth, endOffsetVertical);
                layout.draw(canvas);
                canvas.restore();

                // Update offset for the next page
                startOffsetVertical = endOffsetVertical;
                pageNumber++;
            }

            // Finalize the last page
            document.finishPage(page);
            // 6. Save the PDF file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String safeTitle = noteTitle.replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = safeTitle + "_" + timestamp + ".pdf";

            // Use applicationContext.getExternalFilesDir for file location
            File file = new File(applicationContext.getExternalFilesDir(null), filename);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                document.writeTo(fos);

                // --- END OF HEAVY WORK ---

                // Switch back to the Main Thread to update the UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(applicationContext, "Note exported successfully!", Toast.LENGTH_SHORT).show();
                        // Call openPdf on the Main Thread
                        openPdf(file);
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Error generating PDF: " + e.getMessage());
                // Switch back to the Main Thread for error Toast
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(applicationContext, "Error exporting note.", Toast.LENGTH_LONG).show();
                    });
                }
            } finally {
                document.close();
            }

        }).start(); // Start the background thread
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
}