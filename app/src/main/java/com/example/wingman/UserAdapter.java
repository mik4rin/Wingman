package com.example.wingman;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wingman.data.User;

import java.util.Objects;

public class UserAdapter extends ListAdapter<User, UserAdapter.UserViewHolder> {
    public interface OnUserSelectListener {
        void onUserSelected(User user, boolean isSelected);
    }
    private final OnUserSelectListener listener;
    private final Set<String> selectedUserIds = new HashSet<>();
    private User selectedUser = null;

    public UserAdapter(OnUserSelectListener listener, List<String> initialSharedUids) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        if (initialSharedUids != null) {
            this.selectedUserIds.addAll(initialSharedUids);
        }
    }

    public List<String> getSelectedUserIds() {
        return new ArrayList<>(selectedUserIds);
    }

    private static final DiffUtil.ItemCallback<User> DIFF_CALLBACK = new DiffUtil.ItemCallback<User>() {
        @Override
        public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return Objects.equals(oldItem.getId(), newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
            return Objects.equals(oldItem.getUsername(), newItem.getUsername()) &&
                    Objects.equals(oldItem.getEmail(), newItem.getEmail());
        }
    };

    public void clearSelection() {
        selectedUser = null;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_search, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final TextView usernameView;
        private final TextView emailView;
        private final ImageView selectionIndicator;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameView = itemView.findViewById(R.id.user_username);
            emailView = itemView.findViewById(R.id.user_email);
            selectionIndicator = itemView.findViewById(R.id.selection_indicator);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    User clickedUser = getItem(position);
                    String clickedUid = clickedUser.getId();

                    boolean isCurrentlySelected = selectedUserIds.contains(clickedUid);

                    if (isCurrentlySelected) {
                        selectedUserIds.remove(clickedUid);
                    } else {
                        selectedUserIds.add(clickedUid);
                    }

                    listener.onUserSelected(clickedUser, !isCurrentlySelected);
                    notifyItemChanged(position);
                }
            });
        }

        public void bind(User user) {
            usernameView.setText(user.getUsername());
            emailView.setText(user.getEmail());

            boolean isSelected = selectedUserIds.contains(user.getId());

            itemView.setBackgroundColor(isSelected ?
                    ContextCompat.getColor(itemView.getContext(), R.color.appbackround) :
                    Color.TRANSPARENT);

            selectionIndicator.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            usernameView.setTextColor(ContextCompat.getColor(itemView.getContext(), isSelected ? R.color.blue : R.color.black));
        }
    }
}