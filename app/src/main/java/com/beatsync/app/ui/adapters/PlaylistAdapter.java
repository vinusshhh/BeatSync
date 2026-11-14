package com.beatsync.app.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ItemPlaylistBinding;
import com.beatsync.app.models.Playlist;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }

    private final Context context;
    private final List<Playlist> playlistList = new ArrayList<>();
    private final OnPlaylistClickListener listener;

    public PlaylistAdapter(Context context, OnPlaylistClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPlaylists(List<Playlist> playlists) {
        playlistList.clear();
        if (playlists != null) {
            playlistList.addAll(playlists);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPlaylistBinding binding = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PlaylistViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist playlist = playlistList.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlistList.size();
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final ItemPlaylistBinding binding;

        public PlaylistViewHolder(ItemPlaylistBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Playlist playlist) {
            binding.tvPlaylistTitle.setText(playlist.getTitle());
            String subtitle = "Playlist • " + playlist.getTrackCount() + " " + (playlist.getTrackCount() == 1 ? "track" : "tracks");
            if (playlist.getOwnerName() != null && !playlist.getOwnerName().isEmpty()) {
                subtitle += " • by " + playlist.getOwnerName();
            }
            binding.tvPlaylistSubtitle.setText(subtitle);

            if (playlist.getCoverUrl() != null && !playlist.getCoverUrl().isEmpty()) {
                String fullUrl = AuthManager.getInstance(context).getBaseUrl() + playlist.getCoverUrl();
                binding.ivPlaylistCover.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(fullUrl)
                        .centerCrop()
                        .into(binding.ivPlaylistCover);
            } else {
                binding.ivPlaylistCover.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlaylistClick(playlist);
                }
            });
        }
    }
}
