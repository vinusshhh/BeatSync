package com.beatsync.app.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.models.Track;
import com.beatsync.app.ui.utils.AnimationHelper;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class AdminTrackAdapter extends RecyclerView.Adapter<AdminTrackAdapter.TrackViewHolder> {

    private final Context context;
    private final List<Track> tracks = new ArrayList<>();
    private final OnAdminTrackActionListener listener;

    public interface OnAdminTrackActionListener {
        void onPlayTrack(Track track, List<Track> list, int position);
        void onDeleteTrack(Track track);
    }

    public AdminTrackAdapter(Context context, OnAdminTrackActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setTracks(List<Track> newTracks) {
        tracks.clear();
        if (newTracks != null) {
            tracks.addAll(newTracks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        AnimationHelper.animateRubberEntrance(holder.itemView, position);
        AnimationHelper.addRubberPressEffect(holder.btnPlay);
        AnimationHelper.addRubberPressEffect(holder.btnDelete);

        holder.tvTitle.setText(track.getTitle());
        holder.tvArtist.setText(track.getArtist() + (track.getAlbum() != null && !track.getAlbum().isEmpty() ? " • " + track.getAlbum() : ""));
        holder.tvUploader.setText("by " + track.getUploaderName());

        if (track.isPublic()) {
            holder.tvBadge.setText("PUBLIC");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_public);
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_public_text));
        } else {
            holder.tvBadge.setText("PRIVATE");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_private);
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.badge_private_text));
        }

        if (track.getCoverUrl() != null && !track.getCoverUrl().isEmpty()) {
            String fullCover = track.getCoverUrl().startsWith("http")
                    ? track.getCoverUrl()
                    : AuthManager.getInstance(context).getBaseUrl() + track.getCoverUrl();
            holder.ivNote.setVisibility(View.GONE);
            holder.ivCover.setVisibility(View.VISIBLE);
            Glide.with(context)
                    .load(fullCover)
                    .placeholder(R.drawable.bg_default_album_art_small)
                    .error(R.drawable.bg_default_album_art_small)
                    .centerCrop()
                    .into(holder.ivCover);
        } else {
            holder.ivCover.setVisibility(View.GONE);
            holder.ivNote.setVisibility(View.VISIBLE);
        }

        holder.btnPlay.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlayTrack(track, tracks, position);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteTrack(track);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvArtist, tvBadge, tvUploader;
        ImageView ivCover, ivNote, btnPlay, btnDelete;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTrackTitle);
            tvArtist = itemView.findViewById(R.id.tvTrackArtist);
            tvBadge = itemView.findViewById(R.id.tvPrivacyBadge);
            tvUploader = itemView.findViewById(R.id.tvUploaderInfo);
            ivCover = itemView.findViewById(R.id.ivCover);
            ivNote = itemView.findViewById(R.id.ivDefaultNote);
            btnPlay = itemView.findViewById(R.id.btnPlayTrack);
            btnDelete = itemView.findViewById(R.id.btnDeleteTrack);
        }
    }
}
