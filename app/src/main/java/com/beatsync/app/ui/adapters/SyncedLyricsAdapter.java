package com.beatsync.app.ui.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.models.LyricsResponse;
import java.util.ArrayList;
import java.util.List;

public class SyncedLyricsAdapter extends RecyclerView.Adapter<SyncedLyricsAdapter.ViewHolder> {

    public interface OnLineClickListener {
        void onLineClick(LyricsResponse.LyricLine line, int position);
    }

    private final List<LyricsResponse.LyricLine> lines = new ArrayList<>();
    private final OnLineClickListener clickListener;
    private int activePosition = -1;

    public SyncedLyricsAdapter(OnLineClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setLines(List<LyricsResponse.LyricLine> newLines) {
        this.lines.clear();
        this.activePosition = -1;
        if (newLines != null) {
            this.lines.addAll(newLines);
        }
        notifyDataSetChanged();
    }

    public int setActiveTime(long currentMs) {
        if (lines.isEmpty()) return -1;

        int newActive = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getTimeMs() <= currentMs) {
                newActive = i;
            } else {
                break;
            }
        }

        if (newActive != activePosition) {
            int prev = activePosition;
            activePosition = newActive;
            if (prev >= 0 && prev < lines.size()) {
                notifyItemChanged(prev);
            }
            if (activePosition >= 0 && activePosition < lines.size()) {
                notifyItemChanged(activePosition);
            }
            return activePosition;
        }

        return -1; // No change in active position
    }

    public int getActivePosition() {
        return activePosition;
    }

    public List<LyricsResponse.LyricLine> getLines() {
        return lines;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_synced_lyric_line, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LyricsResponse.LyricLine line = lines.get(position);
        holder.tvLyricText.setText(line.getText());

        boolean isActive = (position == activePosition);
        if (isActive) {
            holder.tvLyricText.setTextColor(Color.WHITE);
            holder.tvLyricText.setTextSize(18f);
            holder.tvLyricText.setTypeface(Typeface.DEFAULT_BOLD);
            holder.tvLyricText.setBackgroundResource(R.drawable.bg_synced_lyric_active);
            holder.itemView.setAlpha(1.0f);
            holder.itemView.setScaleX(1.02f);
            holder.itemView.setScaleY(1.02f);
        } else {
            holder.tvLyricText.setTextColor(Color.parseColor("#80FFFFFF"));
            holder.tvLyricText.setTextSize(15f);
            holder.tvLyricText.setTypeface(Typeface.DEFAULT);
            holder.tvLyricText.setBackground(null);
            holder.itemView.setAlpha(0.60f);
            holder.itemView.setScaleX(1.0f);
            holder.itemView.setScaleY(1.0f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onLineClick(line, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLyricText;

        ViewHolder(View itemView) {
            super(itemView);
            tvLyricText = itemView.findViewById(R.id.tvLyricText);
        }
    }
}
