package com.example.wingman;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

public class Notes_Edit_MainActivity extends AppCompatActivity {

    private static final int EDIT_PAGE_INDEX = 0;
    private ViewPager2 viewPager;
    private SharedNotesViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notes_edit_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        hideSystemUI();

        viewModel = new ViewModelProvider(this).get(SharedNotesViewModel.class);
        new ViewModelProvider(this).get(NotesViewModel.class);

        String noteId = getIntent().getStringExtra("note_id");
        if (noteId != null) {
            viewModel.setCurrentNoteId(noteId);
        }

        viewPager = findViewById(R.id.notes_viewPager);
        viewPager.post(() -> {
            viewPager.setAdapter(new Notes_Edit_Fragment(this));
            viewPager.setCurrentItem(EDIT_PAGE_INDEX, false);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

}
