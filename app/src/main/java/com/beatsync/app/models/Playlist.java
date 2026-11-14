package com.beatsync.app.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Playlist implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("cover_url")
    private String coverUrl;

    @SerializedName("is_public")
    private boolean isPublic;

    @SerializedName("owner_name")
    private String ownerName;

    @SerializedName("track_count")
    private int trackCount;

    @SerializedName("tracks")
    private List<Track> tracks = new ArrayList<>();

    public Playlist() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title != null ? title : "Untitled Playlist"; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public String getOwnerName() { return ownerName != null ? ownerName : "User"; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public int getTrackCount() { return trackCount > 0 ? trackCount : (tracks != null ? tracks.size() : 0); }
    public void setTrackCount(int trackCount) { this.trackCount = trackCount; }

    public List<Track> getTracks() { return tracks != null ? tracks : new ArrayList<>(); }
    public void setTracks(List<Track> tracks) { this.tracks = tracks; }
}
