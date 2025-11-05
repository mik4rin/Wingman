package com.example.wingman;

import android.graphics.Color;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.Note;
import com.google.android.material.card.MaterialCardView;
import androidx.core.text.HtmlCompat;

import java.util.Objects;

public class NotesAdapter extends ListAdapter<Note, NotesAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public interface OnExportClickListener {
        void onExportClick(Note note);
    }

    public interface ContextMenuCallback {
        void onContextMenuRequested(int position, boolean fromPinned);
    }

    private final OnNoteClickListener listener;
    private final OnExportClickListener exportListener;
    private ContextMenuCallback contextMenuCallback;

    private int selectedPosition = RecyclerView.NO_POSITION;

    public NotesAdapter(OnNoteClickListener listener, OnExportClickListener exportListener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.exportListener = exportListener;
    }

    public void setContextMenuCallback(ContextMenuCallback callback) {
        this.contextMenuCallback = callback;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    private static final DiffUtil.ItemCallback<Note> DIFF_CALLBACK = new DiffUtil.ItemCallback<Note>() {
        @Override
        public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            String a = oldItem.getId();
            String b = newItem.getId();
            return a != null && b != null && a.equals(b);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
            return safeString(oldItem.getTitle()).equals(safeString(newItem.getTitle()))
                    && safeString(oldItem.getContents()).equals(safeString(newItem.getContents()))
                    && oldItem.isPinned() == newItem.isPinned()
                    && safeString(oldItem.getMainColor()).equals(safeString(newItem.getMainColor()))
                    && safeString(oldItem.getAccentColor()).equals(safeString(newItem.getAccentColor()));
        }

        private String safeString(String s) { return s == null ? "" : s; }
    };

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.notes_card, parent, false);
        return new NoteViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class NoteViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {

        private final MaterialCardView cardView;
        private final TextView titleView;
        private final TextView contentView;
        private final ImageButton exportButton;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);

            cardView = itemView.findViewById(R.id.material_card_view);
            titleView = itemView.findViewById(R.id.note_title);
            contentView = itemView.findViewById(R.id.note_content);
            exportButton = itemView.findViewById(R.id.exportNote_btn);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onNoteClick(getItem(pos));
                }
            });

            exportButton.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && exportListener != null) {
                    exportListener.onExportClick(getItem(pos));
                }
            });

            itemView.setOnCreateContextMenuListener(this);

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && contextMenuCallback != null) {
                    selectedPosition = pos;
                    contextMenuCallback.onContextMenuRequested(pos, false);
                }
                return false;
            });

        }

        void bind(Note note) {
            titleView.setText(note.getTitle());
            CharSequence rendered = HtmlCompat.fromHtml(
                    note.getContents(),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
            );
            contentView.setText(rendered);

            try {
                cardView.setCardBackgroundColor(Color.parseColor(note.getMainColor()));
            } catch (Exception e) {
                cardView.setCardBackgroundColor(Color.WHITE);
            }

            try {
                cardView.setStrokeColor(Color.parseColor(note.getAccentColor()));
            } catch (Exception e) {
                cardView.setStrokeColor(Color.BLACK);
            }
            cardView.setStrokeWidth(4);

            titleView.setTextColor(Color.BLACK);
            contentView.setTextColor(Color.BLACK);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle("Select Action");
            menu.add(Menu.NONE, R.id.action_export, Menu.NONE, "Export to PDF");
            menu.add(Menu.NONE, R.id.action_delete, Menu.NONE, "Delete");
            menu.add(Menu.NONE, R.id.action_pin, Menu.NONE, "Pin");
            menu.add(Menu.NONE, R.id.action_unpin, Menu.NONE, "Unpin");
        }
    }
}
