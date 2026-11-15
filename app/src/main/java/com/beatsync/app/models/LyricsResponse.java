package com.beatsync.app.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsResponse implements Serializable {

    @SerializedName("found")
    private boolean found;

    @SerializedName("synced_lyrics")
    private String syncedLyrics;

    @SerializedName("plain_lyrics")
    private String plainLyrics;

    @SerializedName("track_name")
    private String trackName;

    @SerializedName("artist_name")
    private String artistName;

    public static class LyricLine implements Serializable {
        private final long timeMs;
        private final String text;

        public LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        public long getTimeMs() { return timeMs; }
        public String getText() { return text; }
    }

    public boolean isFound() { return found; }
    public String getSyncedLyrics() { return syncedLyrics; }
    public String getPlainLyrics() { return plainLyrics; }
    public String getTrackName() { return trackName; }
    public String getArtistName() { return artistName; }

    public String getCleanLyricalText() {
        if (plainLyrics != null && !plainLyrics.trim().isEmpty()) {
            return plainLyrics.trim();
        }
        if (syncedLyrics != null && !syncedLyrics.trim().isEmpty()) {
            // Strip timestamp prefixes like [00:12.34] or [01:23]
            return syncedLyrics.replaceAll("(?m)^\\[\\d+:\\d+(?:\\.\\d+)?\\]\\s*", "").trim();
        }
        return "";
    }

    public List<LyricLine> getParsedLines() {
        if (syncedLyrics == null || syncedLyrics.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)");

        for (String rawLine : syncedLyrics.split("\n")) {
            rawLine = rawLine.trim();
            if (rawLine.isEmpty()) continue;

            Matcher matcher = pattern.matcher(rawLine);
            if (matcher.matches()) {
                try {
                    int minutes = Integer.parseInt(matcher.group(1));
                    double seconds = Double.parseDouble(matcher.group(2));
                    long timeMs = (long) ((minutes * 60 + seconds) * 1000);
                    String text = matcher.group(3).trim();
                    if (!text.isEmpty()) {
                        lines.add(new LyricLine(timeMs, text));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        Collections.sort(lines, Comparator.comparingLong(LyricLine::getTimeMs));
        return lines;
    }
}
