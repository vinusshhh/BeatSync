package com.beatsync.app.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Locale;

public class Track implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("title")
    private String title;

    @SerializedName("artist")
    private String artist;

    @SerializedName("album")
    private String album;

    @SerializedName("duration_seconds")
    private int durationSeconds;

    @SerializedName("file_url")
    private String fileUrl;

    @SerializedName("cover_url")
    private String coverUrl;

    @SerializedName("is_public")
    private boolean isPublic;

    @SerializedName("uploader_name")
    private String uploaderName;

    @SerializedName("is_liked")
    private boolean isLiked;

    public Track() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title != null ? title : "Untitled Track"; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist != null ? artist : "Unknown Artist"; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getAlbum() { return album != null ? album : "Single"; }
    public void setAlbum(String album) { this.album = album; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public String getUploaderName() { return uploaderName != null ? uploaderName : "BeatSync Artist"; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }

    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }

    public String getFormattedDuration() {
        if (durationSeconds <= 0) return "0:00";
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }
}
