package com.beatsync.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.models.User;
import com.beatsync.app.ui.utils.AnimationHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    private final List<User> users = new ArrayList<>();
    private final Set<String> revealedUserIds = new HashSet<>();
    private final OnUserActionListener listener;

    public interface OnUserActionListener {
        void onDeleteUser(User user);
    }

    public AdminUserAdapter(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers) {
        users.clear();
        if (newUsers != null) {
            users.addAll(newUsers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        AnimationHelper.animateItemEntrance(holder.itemView, position);

        holder.tvName.setText(user.getName() + (user.isAdmin() ? " (ADMIN)" : ""));
        holder.tvEmail.setText(user.getEmail());
        holder.tvTrackCount.setText(user.getTrackCount() + " track" + (user.getTrackCount() == 1 ? "" : "s") + " uploaded");

        String created = user.getCreatedAt();
        if (created != null && created.length() >= 10) {
            holder.tvJoined.setText("Joined " + created.substring(0, 10));
        } else {
            holder.tvJoined.setText("Active Member");
        }

        boolean isRevealed = revealedUserIds.contains(user.getId());
        if (isRevealed) {
            holder.tvPassword.setText(user.getRawPassword());
            holder.btnToggleEye.setImageResource(R.drawable.ic_eye_off);
        } else {
            holder.tvPassword.setText("••••••••");
            holder.btnToggleEye.setImageResource(R.drawable.ic_eye);
        }

        holder.btnToggleEye.setOnClickListener(v -> {
            if (revealedUserIds.contains(user.getId())) {
                revealedUserIds.remove(user.getId());
                holder.tvPassword.setText("••••••••");
                holder.btnToggleEye.setImageResource(R.drawable.ic_eye);
            } else {
                revealedUserIds.add(user.getId());
                holder.tvPassword.setText(user.getRawPassword());
                holder.btnToggleEye.setImageResource(R.drawable.ic_eye_off);
            }
        });

        // Hide delete button for the primary admin account
        if (user.isAdmin()) {
            holder.btnDelete.setVisibility(View.GONE);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteUser(user);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvPassword, tvTrackCount, tvJoined;
        ImageView btnToggleEye, btnDelete;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvEmail = itemView.findViewById(R.id.tvUserEmail);
            tvPassword = itemView.findViewById(R.id.tvUserPassword);
            tvTrackCount = itemView.findViewById(R.id.tvUserTrackCount);
            tvJoined = itemView.findViewById(R.id.tvUserJoined);
            btnToggleEye = itemView.findViewById(R.id.btnTogglePasswordEye);
            btnDelete = itemView.findViewById(R.id.btnDeleteUser);
        }
    }
}
