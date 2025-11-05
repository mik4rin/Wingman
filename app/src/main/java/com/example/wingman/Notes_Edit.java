package com.example.wingman;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.wingman.data.Note;
import com.example.wingman.data.NoteRepository;
import com.example.wingman.data.OnFirestoreNoteListener;
import com.example.wingman.data.OnFirestoreResultListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class Notes_Edit extends Fragment {
    private static final String TAG = "QuillEditor";
    private WebView webView;
    private EditText addNote_title;
    private FloatingActionButton addNote_addBtn;
    private String noteId;
    private String mainColor = "#FFFFFF";
    private String accentColor = "#000000";
    private SharedNotesViewModel viewModel;
    private NotesViewModel notesViewModel;
    private MaterialButton boldBtn, italicBtn, underlineBtn, strikeBtn, h1Btn, superSBtn, subSBtn, colorBtn, bulletBtn, textSize;
    private ImageButton return_btn;
    private String pendingHtmlToLoad = null;
    private String originalTitle = "";
    private String originalContent = "";
    private String originalMainColor = "#FFFFFF";
    private String originalAccentColor = "#000000";
    private Note currentNote = null;
    private boolean isSaving = false;
    private boolean hasUnsavedChanges = false;
    private boolean saveAndExitInProgress = false;

    public Notes_Edit() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.notes_edit_main_fragment_editor, container, false);

        notesViewModel = new ViewModelProvider(requireActivity()).get(NotesViewModel.class);
        viewModel = new ViewModelProvider(requireActivity()).get(SharedNotesViewModel.class);

        addNote_title = view.findViewById(R.id.addNote_title);
        addNote_addBtn = view.findViewById(R.id.addNote_addBtn);
        webView = view.findViewById(R.id.quillWebView);

        addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));

        setupWebView();

        viewModel.getCurrentNoteId().observe(getViewLifecycleOwner(), id -> {
            if (id != null && !id.equals(noteId)) {
                noteId = id;
                loadNoteData(id);
            }
        });

        addNote_title.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setNoteTitle(s.toString());
                hasUnsavedChanges = true;
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        addNote_addBtn.setOnClickListener(v -> {
            saveAndExitInProgress = true;
            saveNote();
        });

        return_btn = view.findViewById(R.id.return_btn);
        return_btn.setOnClickListener(v -> goHome());


        boldBtn      = view.findViewById(R.id.text_bold);
        italicBtn    = view.findViewById(R.id.text_italic);
        underlineBtn = view.findViewById(R.id.text_underline);
        strikeBtn    = view.findViewById(R.id.text_strike);
        h1Btn        = view.findViewById(R.id.text_h1);
        superSBtn    = view.findViewById(R.id.text_super);
        subSBtn      = view.findViewById(R.id.text_sub);
        bulletBtn    = view.findViewById(R.id.text_bullet);
        textSize     = view.findViewById(R.id.text_size);
        colorBtn     = view.findViewById(R.id.colorBtn);

        View.OnClickListener formatClickListener = v -> {
            int id = v.getId();
            if (id == R.id.text_bold) {
                webView.evaluateJavascript("var current = quill.getFormat().bold || false; quill.format('bold', !current);", null);
            } else if (id == R.id.text_italic) {
                webView.evaluateJavascript("var current = quill.getFormat().italic || false; quill.format('italic', !current);", null);
            } else if (id == R.id.text_underline) {
                webView.evaluateJavascript("var current = quill.getFormat().underline || false; quill.format('underline', !current);", null);
            } else if (id == R.id.text_strike) {
                webView.evaluateJavascript("var current = quill.getFormat().strike || false; quill.format('strike', !current);", null);
            } else if (id == R.id.text_h1) {
                webView.evaluateJavascript("var current = quill.getFormat().header || 0; quill.format('header', current == 1 ? false : 1);", null);
            } else if (id == R.id.text_super) {
                webView.evaluateJavascript("var current = quill.getFormat().script || false; quill.format('script', current == 'super' ? false : 'super');", null);
            } else if (id == R.id.text_sub) {
                webView.evaluateJavascript("var current = quill.getFormat().script || false; quill.format('script', current == 'sub' ? false : 'sub');", null);
            } else if (id == R.id.text_bullet) {
                webView.evaluateJavascript("var current = quill.getFormat().list || false; quill.format('list', current === 'bullet' ? false : 'bullet');", null);
            }
        };

        boldBtn.setOnClickListener(formatClickListener);
        italicBtn.setOnClickListener(formatClickListener);
        underlineBtn.setOnClickListener(formatClickListener);
        strikeBtn.setOnClickListener(formatClickListener);
        h1Btn.setOnClickListener(formatClickListener);
        superSBtn.setOnClickListener(formatClickListener);
        subSBtn.setOnClickListener(formatClickListener);
        bulletBtn.setOnClickListener(formatClickListener);

        final int[] FONT_SIZES = { 8, 12, 16, 18, 22, 24, 28, 32, 48, 64, 72, 80 };
        textSize.setOnClickListener(v -> {
            ContextThemeWrapper wrapper = new ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_App_PopupMenu_White);
            PopupMenu popup = new PopupMenu(wrapper, v);
            for (int i = 0; i < FONT_SIZES.length; i++) {
                int size = FONT_SIZES[i];
                popup.getMenu().add(0, i, i, String.valueOf(size));
            }
            popup.setOnMenuItemClickListener(item -> {
                String sizeStr = item.getTitle().toString();
                String js = String.format("quill.format('size','%spx');", sizeStr);
                webView.evaluateJavascript(js, null);
                return true;
            });
            popup.show();
        });

        colorBtn.setOnClickListener(v -> showColorPickerDialog());

        styleTextBtns();

        Map<Integer, int[]> colorMap = new HashMap<>();
        colorMap.put(R.id.card_col1, new int[]{R.color.white, R.color.black});
        colorMap.put(R.id.card_col2, new int[]{R.color.pink_accent, R.color.pink_main});
        colorMap.put(R.id.card_col3, new int[]{R.color.purple_accent, R.color.purple_main});
        colorMap.put(R.id.card_col4, new int[]{R.color.blue_accent, R.color.blue_main});
        colorMap.put(R.id.card_col5, new int[]{R.color.yellow_accent, R.color.yellow_main});
        colorMap.put(R.id.card_col6, new int[]{R.color.orange_accent, R.color.orange_main});
        colorMap.put(R.id.card_col7, new int[]{R.color.red_accent, R.color.red_main});
        colorMap.put(R.id.card_col8, new int[]{R.color.green_accent, R.color.green_main});
        colorMap.put(R.id.card_col9, new int[]{R.color.towa_accent, R.color.towa_main});
        colorMap.put(R.id.card_col10, new int[]{R.color.aqua_accent, R.color.aqua_main});
        colorMap.put(R.id.card_col11, new int[]{R.color.kronii_accent, R.color.kronii_main});
        colorMap.put(R.id.card_col12, new int[]{R.color.luna_accent, R.color.luna_main});
        colorMap.put(R.id.card_col13, new int[]{R.color.vivi_accent, R.color.vivi_main});

        int[] colorButtonIds = {
                R.id.card_col1, R.id.card_col2, R.id.card_col3,
                R.id.card_col4, R.id.card_col5, R.id.card_col6, R.id.card_col7, R.id.card_col8,
                R.id.card_col9, R.id.card_col10, R.id.card_col11, R.id.card_col12, R.id.card_col13
        };

        View.OnClickListener changeNoteColor = v -> {
            int id = v.getId();
            if (colorMap.containsKey(id)) {
                int[] colors = colorMap.get(id);
                int btnCol = colors[0];
                int returnCol = colors[1];

                int mainColorInt = ContextCompat.getColor(requireContext(), btnCol);
                int accentColorInt = ContextCompat.getColor(requireContext(), returnCol);

                mainColor = String.format("#%08X", ContextCompat.getColor(requireContext(), colors[0]));
                accentColor = String.format("#%08X", ContextCompat.getColor(requireContext(), colors[1]));

                addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(mainColorInt));
                return_btn.setBackgroundTintList(ColorStateList.valueOf(accentColorInt));
                addNote_title.setTextColor(ColorStateList.valueOf(accentColorInt));
            }

            Toast.makeText(requireContext(), "Note Card's color has been updated.", Toast.LENGTH_SHORT).show();
            hasUnsavedChanges = true;
        };

        for (int id : colorButtonIds) {
            view.findViewById(id).setOnClickListener(changeNoteColor);
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goHome();
            }
        });

        return view;
    }

    private void showColorPickerDialog() {
        final String[] colorOptions = {
                "#000000", "#333333", "#666666", "#FFFFFF", "#FF0000", "#E91E63",
                "#9C27B0", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", "#009688",
                "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107", "#FF9800",
                "#FF5722", "#795548", "#A1887F", "#607D8B"
        };

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.notes_edit_colorpicker, null);
        GridLayout grid = dialogView.findViewById(R.id.color_grid);

        TextView title = new TextView(requireContext());
        title.setText("Change text color");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setPadding(32, 32, 32, 0);
        title.setTypeface(null, Typeface.BOLD);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setCustomTitle(title)
                .setView(dialogView)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dialog.getWindow().setElevation(0f);
            }
            View decor = dialog.getWindow().getDecorView();
            if (decor instanceof ViewGroup) ((ViewGroup) decor).setBackgroundColor(Color.WHITE);
        });

        int columnCount = 5;
        grid.setColumnCount(columnCount);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int marginDp = 8;
        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, marginDp, metrics);

        int totalMargin = marginPx * (columnCount + 1);
        int swatchSize = (screenWidth - totalMargin) / columnCount;

        for (String color : colorOptions) {
            View colorView = new View(requireContext());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = swatchSize;
            params.height = swatchSize;
            params.setMargins(marginPx, marginPx, marginPx, marginPx);
            colorView.setLayoutParams(params);
            colorView.setClickable(true);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(Color.parseColor(color));
            drawable.setStroke(2, Color.DKGRAY);
            colorView.setBackground(drawable);

            colorView.setOnClickListener(v -> {
                String js = String.format("applyColorToSelection('%s');", color);
                webView.evaluateJavascript(js, null);
                dialog.dismiss();
                hasUnsavedChanges = true;
            });

            grid.addView(colorView);
        }

        dialog.show();
    }

    private void styleTextBtns() {
        underlineBtn.setText(Html.fromHtml("<u>U</u>"));
        strikeBtn.setText(Html.fromHtml("<strike>S</strike>"));
        subSBtn.setText(Html.fromHtml("S<sub>s</sub>"));
        superSBtn.setText(Html.fromHtml("S<sup>s</sup>"));
    }

    private CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(html);
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        WebView.setWebContentsDebuggingEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendingHtmlToLoad != null) {
                    injectHtmlIntoQuill(pendingHtmlToLoad);
                    pendingHtmlToLoad = null;
                }
            }
        });

        webView.addJavascriptInterface(new JSBridge(), "Android");
        webView.loadUrl("file:///android_asset/editor.html");
    }

    private void saveNote() {
        if (isSaving) return;
        isSaving = true;

        final String title = addNote_title.getText().toString().trim();

        webView.evaluateJavascript("getHtmlContent();", html -> {
            if (html == null || html.equals("null")) {
                isSaving = false;
                return;
            }
            String cleaned = html;
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            cleaned = cleaned.replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            final String finalCleaned = cleaned;

            if (title.isEmpty() || finalCleaned.trim().isEmpty()) {
                Toast.makeText(getContext(), "Title and content cannot be empty.", Toast.LENGTH_SHORT).show();
                isSaving = false;
                return;
            }

            Note note = new Note();
            FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
            if (fu == null) {
                Toast.makeText(getContext(), "You must be signed in to save notes.", Toast.LENGTH_SHORT).show();
                isSaving = false;
                return;
            }
            note.setUserId(fu.getUid());
            note.setTitle(title);
            note.setContents(finalCleaned);
            note.setMainColor(mainColor);
            note.setAccentColor(accentColor);
            note.setPinned(false);

            if (noteId != null && !noteId.isEmpty()) {
                if (currentNote == null) {
                    Toast.makeText(getContext(), "Error: Note data missing. Cannot update.", Toast.LENGTH_SHORT).show();
                    isSaving = false;
                    return;
                }

                currentNote.setTitle(title);
                currentNote.setContents(finalCleaned);
                currentNote.setMainColor(mainColor);
                currentNote.setAccentColor(accentColor);
                notesViewModel.updateNote(currentNote);

                originalTitle = title;
                originalContent = finalCleaned;
                viewModel.setCurrentNoteId(noteId);

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), title + " updated successfully!", Toast.LENGTH_SHORT).show();
                    finishSave();
                });

            } else {
                Note newNote = new Note();

                newNote.setUserId(fu.getUid());
                newNote.setTitle(title);
                newNote.setContents(finalCleaned);
                newNote.setMainColor(mainColor);
                newNote.setAccentColor(accentColor);
                newNote.setPinned(false);

                notesViewModel.insertNote(newNote);

                currentNote = newNote;
                noteId = newNote.getId() != null ? newNote.getId() : ("temp_" + System.currentTimeMillis());
                newNote.setId(noteId);
                viewModel.setCurrentNoteId(noteId);

                originalTitle = title;
                originalContent = finalCleaned;

                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), title + " added successfully!", Toast.LENGTH_SHORT).show();
                    finishSave();
                });
            }
        });
    }

    private void finishSave() {
        isSaving = false;
        hasUnsavedChanges = false;
        if (saveAndExitInProgress) {
            saveAndExitInProgress = false;
            actuallyGoHome();
        }
    }

    private void loadNoteData(String noteId) {
        if (noteId == null || noteId.isEmpty()) return;

        if (notesViewModel != null) {
            notesViewModel.getNoteByIdAsync(noteId, new NotesViewModel.NoteCallback() {
                @Override
                public void onNoteLoaded(Note note) {
                    if (note == null) return;
                    currentNote = note;
                    originalTitle = note.getTitle() != null ? note.getTitle() : "";
                    originalContent = note.getContents() != null ? note.getContents() : "";

                    requireActivity().runOnUiThread(() -> {
                        addNote_title.setText(originalTitle);
                        setHtmlToEditor(originalContent);

                        mainColor = note.getMainColor();
                        accentColor = note.getAccentColor();
                        originalMainColor = mainColor;
                        originalAccentColor = accentColor;

                        try {
                            int mainCol = Color.parseColor(mainColor);
                            int accentCol = Color.parseColor(accentColor);

                            addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(mainCol));
                            addNote_title.setTextColor(ColorStateList.valueOf(accentCol));

                            int whiteColor = Color.parseColor("#FFFFFFFF");
                            if (mainCol == whiteColor) {
                                return_btn.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK));
                            } else {
                                return_btn.setBackgroundTintList(ColorStateList.valueOf(accentCol));
                            }
                        } catch (Exception e) {
                            addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                        }
                    });
                }

                @Override
                public void onNoteLoadError(Exception e) {
                    Log.e(TAG, "Error loading note via ViewModel", e);
                }
            });
        }
    }

    private void fallbackLoadViaRepo(String id) {
        NoteRepository repo = new NoteRepository();
        repo.getNoteById(id, new OnFirestoreNoteListener() {
            @Override
            public void onSuccess(Note note) {
                if (note == null) return;
                originalTitle = note.getTitle() != null ? note.getTitle() : "";
                originalContent = note.getContents() != null ? note.getContents() : "";

                requireActivity().runOnUiThread(() -> {
                    addNote_title.setText(originalTitle);
                    setHtmlToEditor(originalContent);

                    mainColor = note.getMainColor();
                    accentColor = note.getAccentColor();

                    try {
                        int mainCol = Color.parseColor(mainColor);
                        int accentCol = Color.parseColor(accentColor);

                        addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(mainCol));
                        addNote_title.setTextColor(ColorStateList.valueOf(accentCol));

                        int whiteColor = Color.parseColor("#FFFFFFFF");
                        if (mainCol == whiteColor) {
                            return_btn.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK));
                        } else {
                            return_btn.setBackgroundTintList(ColorStateList.valueOf(accentCol));
                        }
                    } catch (Exception e) {
                        addNote_addBtn.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading note", e);
            }
        });
    }

    private void setHtmlToEditor(String html) {
        if (html == null) html = "";
        if (webView.getProgress() < 100) {
            pendingHtmlToLoad = html;
        } else {
            injectHtmlIntoQuill(html);
        }
    }

    private void injectHtmlIntoQuill(String html) {
        if (html == null) html = "";
        String escaped = "\"" + html.replace("\"", "\\\"") + "\"";
        webView.evaluateJavascript("setHtmlContent(" + escaped + ");", null);
    }

    public boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }

    private void goHome() {
        String currentTitle = addNote_title.getText().toString().trim();

        webView.evaluateJavascript("getHtmlContent();", html -> {
            String cleaned = html == null ? "" : html;
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            cleaned = cleaned.replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            boolean hasChanges =
                    !originalTitle.equals(currentTitle)
                            || !originalContent.equals(cleaned)
                            || !isColorSame(mainColor, originalMainColor)
                            || !isColorSame(accentColor, originalAccentColor);

            if (hasChanges) {
                View dialogView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.dialog_unsaved_changes, null);

                androidx.appcompat.app.AlertDialog unsavedDialog =
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setView(dialogView)
                                .setCancelable(false)
                                .create();

                TextView title = dialogView.findViewById(R.id.dialogTitleText);
                TextView message = dialogView.findViewById(R.id.dialogMessageText);
                androidx.appcompat.widget.AppCompatButton discardBtn = dialogView.findViewById(R.id.buttonNo);
                androidx.appcompat.widget.AppCompatButton saveBtn = dialogView.findViewById(R.id.btnYes);

                title.setText("Save Changes?");
                message.setText("You have unsaved changes. Do you want to save them before exiting?");

                discardBtn.setText("Discard");
                saveBtn.setText("Save");

                discardBtn.setOnClickListener(v -> {
                    hasUnsavedChanges = false;
                    unsavedDialog.dismiss();
                    actuallyGoHome();
                });

                saveBtn.setOnClickListener(v -> {
                    saveAndExitInProgress = true;
                    hasUnsavedChanges = false;
                    unsavedDialog.dismiss();
                    saveNote();
                });

                unsavedDialog.show();

            } else {
                actuallyGoHome();
            }
        });
    }

    private boolean isColorSame(String c1, String c2) {
        if (c1 == null || c2 == null) return false;
        try {
            return Color.parseColor(c1) == Color.parseColor(c2);
        } catch (Exception e) {
            return c1.equals(c2);
        }
    }

    private void actuallyGoHome() {
        if (requireActivity() instanceof Notes_Edit_MainActivity) {
            requireActivity().finish();
            return;
        }

        if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_notes);
        }
    }

    public class JSBridge {
        @JavascriptInterface
        public void receiveHtml(String html) {
            Log.d(TAG, "Live HTML: " + html);
            viewModel.setNoteContentHtml(html);
            hasUnsavedChanges = true;
        }
    }

    @Override
    public void onDestroyView() {
        try {
            if (webView != null) {
                webView.loadUrl("about:blank");
                webView.stopLoading();
                webView.clearHistory();
                webView.removeAllViews();

                webView.setWebViewClient(null);
                webView.setWebChromeClient(null);

                webView.destroy();
                webView = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error while destroying WebView", e);
        }

        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (webView != null) {
            webView.clearFocus();
        }
        super.onPause();
    }
}