package com.beatsync.app.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ItemTrackBinding;
import com.beatsync.app.models.Track;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(Track track, int position, List<Track> tracks);
    }

    public interface OnTrackMoreClickListener {
        void onTrackMoreClick(Track track, View anchor);
    }

    private final Context context;
    private final List<Track> trackList = new ArrayList<>();
    private final OnTrackClickListener clickListener;
    private final OnTrackMoreClickListener moreClickListener;
    private String playingTrackId = null;

    public TrackAdapter(Context context, OnTrackClickListener clickListener, OnTrackMoreClickListener moreClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.moreClickListener = moreClickListener;
    }

    public void setTracks(List<Track> tracks) {
        trackList.clear();
        if (tracks != null) {
            trackList.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    public void setPlayingTrackId(String trackId) {
        this.playingTrackId = trackId;
        notifyDataSetChanged();
    }

    public List<Track> getTracks() {
        return trackList;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTrackBinding binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new TrackViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = trackList.get(position);
        holder.bind(track, position);
    }

    @Override
    public int getItemCount() {
        return trackList.size();
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {
        private final ItemTrackBinding binding;

        public TrackViewHolder(ItemTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(Track track, int position) {
            binding.tvTitle.setText(track.getTitle());
            binding.tvArtist.setText(track.getArtist());
            binding.tvDuration.setText(track.getFormattedDuration());

            boolean isCurrent = playingTrackId != null && playingTrackId.equals(track.getId());
            if (isCurrent) {
                binding.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_cyan));
            } else {
                binding.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }

            // Privacy badge
            if (track.isPublic()) {
                binding.tvBadge.setText("PUBLIC");
                binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_public);
                binding.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_public_text));
            } else {
                binding.tvBadge.setText("PRIVATE");
                binding.tvBadge.setBackgroundResource(R.drawable.bg_badge_private);
                binding.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_private_text));
            }

            // Cover Art loading
            if (track.getCoverUrl() != null && !track.getCoverUrl().isEmpty()) {
                String fullCoverUrl = AuthManager.getInstance(context).getBaseUrl() + track.getCoverUrl();
                binding.ivDefaultNote.setVisibility(View.GONE);
                binding.ivCover.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(fullCoverUrl)
                        .placeholder(R.drawable.ic_music_note)
                        .centerCrop()
                        .into(binding.ivCover);
            } else {
                binding.ivCover.setVisibility(View.GONE);
                binding.ivDefaultNote.setVisibility(View.VISIBLE);
            }

            // Clicks
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTrackClick(track, position, trackList);
                }
            });

            binding.btnMore.setOnClickListener(v -> {
                if (moreClickListener != null) {
                    moreClickListener.onTrackMoreClick(track, v);
                }
            });
        }
    }
}
