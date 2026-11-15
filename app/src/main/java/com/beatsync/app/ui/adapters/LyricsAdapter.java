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
import com.beatsync.app.models.LyricsResponse.LyricLine;
import java.util.ArrayList;
import java.util.List;

public class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.LyricViewHolder> {

    private final List<LyricLine> lines = new ArrayList<>();
    private int activePosition = -1;
    private OnLineClickListener clickListener;

    public interface OnLineClickListener {
        void onLineClicked(LyricLine line);
    }

    public void setOnLineClickListener(OnLineClickListener listener) {
        this.clickListener = listener;
    }

    public void setLines(List<LyricLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
        activePosition = -1;
        notifyDataSetChanged();
    }

    public int getLineCount() {
        return lines.size();
    }

    public LyricLine getLine(int position) {
        if (position >= 0 && position < lines.size()) {
            return lines.get(position);
        }
        return null;
    }

    public void setActivePosition(int newPos) {
        if (activePosition != newPos) {
            int prevPos = activePosition;
            activePosition = newPos;
            if (prevPos >= 0 && prevPos < lines.size()) {
                notifyItemChanged(prevPos);
            }
            if (activePosition >= 0 && activePosition < lines.size()) {
                notifyItemChanged(activePosition);
            }
        }
    }

    public int getActivePosition() {
        return activePosition;
    }

    @NonNull
    @Override
    public LyricViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric_line, parent, false);
        return new LyricViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LyricViewHolder holder, int position) {
        LyricLine line = lines.get(position);
        boolean isActive = (position == activePosition);

        holder.tvText.setText(line.getText());

        if (isActive) {
            holder.tvText.setTextColor(Color.WHITE);
            holder.tvText.setTextSize(18f);
            holder.tvText.setTypeface(null, Typeface.BOLD);
            holder.tvText.setAlpha(1.0f);
        } else {
            holder.tvText.setTextColor(Color.parseColor("#777777"));
            holder.tvText.setTextSize(14f);
            holder.tvText.setTypeface(null, Typeface.NORMAL);
            holder.tvText.setAlpha(0.5f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onLineClicked(line);
            }
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class LyricViewHolder extends RecyclerView.ViewHolder {
        TextView tvText;
        LyricViewHolder(@NonNull View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvLyricText);
        }
    }
}
