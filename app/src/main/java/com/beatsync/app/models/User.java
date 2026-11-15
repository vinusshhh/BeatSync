package com.beatsync.app.models;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class User implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("email")
    private String email;

    @SerializedName("name")
    private String name;

    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("is_admin")
    private Boolean isAdmin;

    @SerializedName("raw_password")
    private String rawPassword;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("track_count")
    private Integer trackCount;

    public User() {}

    public User(String id, String email, String name, String avatarUrl) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.avatarUrl = avatarUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name != null ? name : "Anonymous"; }
    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isAdmin() {
        if (isAdmin != null && isAdmin) return true;
        return email != null && email.trim().equalsIgnoreCase("im.vinush@gmail.com");
    }
    public void setAdmin(Boolean admin) { isAdmin = admin; }

    public String getRawPassword() {
        if (rawPassword != null && !rawPassword.trim().isEmpty()) {
            return rawPassword.trim();
        }
        return "[Not Recorded Yet]";
    }
    public void setRawPassword(String rawPassword) { this.rawPassword = rawPassword; }

    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getTrackCount() { return trackCount != null ? trackCount : 0; }
    public void setTrackCount(Integer trackCount) { this.trackCount = trackCount; }
}
