package com.beatsync.app.api;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.models.AuthResponse;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.models.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static ApiClient instance;
    private final OkHttpClient client;
    private final Gson gson;
    private final Context context;
    private final Handler mainHandler;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();

        Dns customDns = hostname -> {
            try {
                return Dns.SYSTEM.lookup(hostname);
            } catch (Exception e) {
                if ("beatsync-91vv.onrender.com".equalsIgnoreCase(hostname)) {
                    List<InetAddress> fallback = new ArrayList<>();
                    try {
                        fallback.add(InetAddress.getByAddress(hostname, new byte[]{(byte) 216, 24, 57, 18}));
                        fallback.add(InetAddress.getByAddress(hostname, new byte[]{(byte) 216, 24, 57, 16}));
                        return fallback;
                    } catch (Exception ignored) {}
                }
                throw e;
            }
        };

        this.client = new OkHttpClient.Builder()
                .dns(customDns)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }

    private String getBaseUrl() {
        return AuthManager.getInstance(context).getBaseUrl();
    }

    private Request.Builder newAuthenticatedRequest(String path) {
        String finalUrl;
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            finalUrl = path;
        } else {
            String cleanPath = (path != null && !path.startsWith("/")) ? "/" + path : (path != null ? path : "");
            finalUrl = getBaseUrl() + cleanPath;
        }
        Request.Builder builder = new Request.Builder()
                .url(finalUrl);
        String token = AuthManager.getInstance(context).getToken();
        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        return builder;
    }

    // ---------------- AUTH ----------------

    public void login(String email, String password, ApiCallback<AuthResponse> callback) {
        String json = gson.toJson(new LoginBody(email, password));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(getBaseUrl() + "/api/auth/login")
                .post(body)
                .build();

        executeCall(request, AuthResponse.class, callback);
    }

    public void register(String email, String password, String name, ApiCallback<AuthResponse> callback) {
        String json = gson.toJson(new RegisterBody(email, password, name));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(getBaseUrl() + "/api/auth/register")
                .post(body)
                .build();

        executeCall(request, AuthResponse.class, callback);
    }

    // ---------------- TRACKS ----------------

    public void getTracks(String search, String filter, ApiCallback<List<Track>> callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(getBaseUrl() + "/api/tracks").newBuilder();
        if (search != null && !search.trim().isEmpty()) {
            urlBuilder.addQueryParameter("search", search.trim());
        }
        if (filter != null) {
            urlBuilder.addQueryParameter("filter", filter);
        }

        Request.Builder requestBuilder = new Request.Builder().url(urlBuilder.build());
        String token = AuthManager.getInstance(context).getToken();
        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + token);
        }

        Type type = new TypeToken<List<Track>>() {}.getType();
        executeGenericCall(requestBuilder.build(), type, callback);
    }

    public void getTrackDetail(String trackId, ApiCallback<Track> callback) {
        Request request = newAuthenticatedRequest("/api/tracks/" + trackId).get().build();
        executeCall(request, Track.class, callback);
    }

    public void toggleLike(String trackId, ApiCallback<Boolean> callback) {
        RequestBody emptyBody = RequestBody.create("", null);
        Request request = newAuthenticatedRequest("/api/tracks/" + trackId + "/like")
                .post(emptyBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    dispatchError(callback, "Request failed: " + response.code());
                    return;
                }
                try {
                    String resStr = response.body().string();
                    LikeResponse lr = gson.fromJson(resStr, LikeResponse.class);
                    dispatchSuccess(callback, lr != null && lr.isLiked);
                } catch (Exception e) {
                    dispatchError(callback, "Parse error: " + e.getMessage());
                }
            }
        });
    }

    public void uploadTrack(String title, String artist, String album, boolean isPublic,
                            Uri audioUri, Uri coverUri, ApiCallback<Track> callback) {
        uploadTrack(title, artist, album, isPublic, 0, audioUri, coverUri, callback);
    }

    public void uploadTrack(String title, String artist, String album, boolean isPublic, int durationSeconds,
                            Uri audioUri, Uri coverUri, ApiCallback<Track> callback) {
        new Thread(() -> {
            try {
                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("title", title)
                        .addFormDataPart("artist", artist)
                        .addFormDataPart("album", album != null ? album : "Single")
                        .addFormDataPart("is_public", String.valueOf(isPublic))
                        .addFormDataPart("duration_seconds", String.valueOf(Math.max(0, durationSeconds)));

                // Read Audio file bytes
                byte[] audioBytes = readUriBytes(audioUri);
                if (audioBytes == null || audioBytes.length == 0) {
                    dispatchError(callback, "Unable to read audio file");
                    return;
                }
                String audioName = "upload_track.mp3";
                RequestBody audioPart = RequestBody.create(audioBytes, MediaType.parse("audio/mpeg"));
                builder.addFormDataPart("file", audioName, audioPart);

                // Optional Cover image
                if (coverUri != null) {
                    byte[] coverBytes = readUriBytes(coverUri);
                    if (coverBytes != null && coverBytes.length > 0) {
                        RequestBody coverPart = RequestBody.create(coverBytes, MediaType.parse("image/jpeg"));
                        builder.addFormDataPart("cover_file", "cover.jpg", coverPart);
                    }
                }

                Request request = newAuthenticatedRequest("/api/tracks/upload")
                        .post(builder.build())
                        .build();

                executeCall(request, Track.class, callback);
            } catch (Exception e) {
                dispatchError(callback, "Upload failed: " + e.getMessage());
            }
        }).start();
    }

    public void updateTrackDuration(String trackId, int durationSeconds, ApiCallback<Track> callback) {
        new Thread(() -> {
            try {
                String json = "{\"duration_seconds\":" + durationSeconds + "}";
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                Request request = newAuthenticatedRequest("/api/tracks/" + trackId)
                        .put(body)
                        .build();
                executeCall(request, Track.class, callback);
            } catch (Exception e) {
                if (callback != null) {
                    dispatchError(callback, e.getMessage());
                }
            }
        }).start();
    }

    // ---------------- PLAYLISTS ----------------

    public void getPlaylists(ApiCallback<List<Playlist>> callback) {
        Request request = newAuthenticatedRequest("/api/playlists").get().build();
        Type type = new TypeToken<List<Playlist>>() {}.getType();
        executeGenericCall(request, type, callback);
    }

    public void createPlaylist(String title, String description, boolean isPublic, ApiCallback<Playlist> callback) {
        String json = gson.toJson(new CreatePlaylistBody(title, description, isPublic));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/playlists")
                .post(body)
                .build();
        executeCall(request, Playlist.class, callback);
    }

    public void getPlaylistDetails(String playlistId, ApiCallback<Playlist> callback) {
        Request request = newAuthenticatedRequest("/api/playlists/" + playlistId).get().build();
        executeCall(request, Playlist.class, callback);
    }

    public void addTrackToPlaylist(String playlistId, String trackId, ApiCallback<String> callback) {
        String json = gson.toJson(new AddTrackBody(trackId));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/playlists/" + playlistId + "/tracks")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Track added to playlist");
                } else {
                    dispatchError(callback, "Failed to add track (" + response.code() + ")");
                }
            }
        });
    }

    public void removeTrackFromPlaylist(String playlistId, String trackId, ApiCallback<String> callback) {
        Request request = newAuthenticatedRequest("/api/playlists/" + playlistId + "/tracks/" + trackId)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Track removed from playlist");
                } else {
                    dispatchError(callback, "Failed to remove track (" + response.code() + ")");
                }
            }
        });
    }

    public void updatePlaylist(String playlistId, String title, String description, ApiCallback<Playlist> callback) {
        String json = gson.toJson(new UpdatePlaylistBody(title, description));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/playlists/" + playlistId)
                .put(body)
                .build();
        executeCall(request, Playlist.class, callback);
    }

    public void deletePlaylist(String playlistId, ApiCallback<String> callback) {
        Request request = newAuthenticatedRequest("/api/playlists/" + playlistId)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Playlist deleted successfully");
                } else {
                    dispatchError(callback, "Failed to delete playlist (" + response.code() + ")");
                }
            }
        });
    }

    // ---------------- AUTH & SETTINGS ----------------

    public void resetPassword(String oldPassword, String newPassword, ApiCallback<String> callback) {
        String json = gson.toJson(new ResetPasswordBody(oldPassword, newPassword));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/auth/reset-password")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Password updated successfully");
                } else {
                    dispatchError(callback, extractError(resStr, response.code()));
                }
            }
        });
    }

    // ---------------- LYRICS ----------------

    public void getLyrics(String title, String artist, int duration, ApiCallback<com.beatsync.app.models.LyricsResponse> callback) {
        getLyrics(null, title, artist, duration, callback);
    }

    public void getLyrics(String trackId, String title, String artist, int duration, ApiCallback<com.beatsync.app.models.LyricsResponse> callback) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(getBaseUrl() + "/api/lyrics").newBuilder();
        urlBuilder.addQueryParameter("title", title != null ? title : "");
        if (trackId != null && !trackId.isEmpty()) {
            urlBuilder.addQueryParameter("track_id", trackId);
        }
        if (artist != null && !artist.isEmpty()) {
            urlBuilder.addQueryParameter("artist", artist);
        }
        if (duration > 0) {
            urlBuilder.addQueryParameter("duration", String.valueOf(duration));
        }

        Request request = newAuthenticatedRequest(urlBuilder.build().toString()).get().build();
        executeCall(request, com.beatsync.app.models.LyricsResponse.class, callback);
    }

    public void reportLyrics(String trackId, boolean isWrong, boolean isNotSynced, ApiCallback<com.beatsync.app.models.LyricsResponse> callback) {
        new Thread(() -> {
            try {
                String json = "{\"is_wrong\":" + isWrong + ",\"is_not_synced\":" + isNotSynced + "}";
                RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
                Request request = newAuthenticatedRequest("/api/admin/tracks/" + trackId + "/lyrics/report")
                        .post(body)
                        .build();
                executeCall(request, com.beatsync.app.models.LyricsResponse.class, callback);
            } catch (Exception e) {
                if (callback != null) {
                    dispatchError(callback, e.getMessage());
                }
            }
        }).start();
    }

    // ---------------- TRACK DELETION ----------------

    public void deleteTrack(String trackId, ApiCallback<String> callback) {
        Request request = newAuthenticatedRequest("/api/tracks/" + trackId)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Track deleted successfully");
                } else {
                    dispatchError(callback, "Failed to delete track (" + response.code() + ")");
                }
            }
        });
    }

    // ---------------- ADMIN ENDPOINTS ----------------

    public void adminGetUsers(ApiCallback<List<User>> callback) {
        Request request = newAuthenticatedRequest("/api/admin/users").get().build();
        Type type = new TypeToken<List<User>>() {}.getType();
        executeGenericCall(request, type, callback);
    }

    public void adminDeleteUser(String userId, ApiCallback<String> callback) {
        Request request = newAuthenticatedRequest("/api/admin/users/" + userId).delete().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "User deleted successfully");
                } else {
                    dispatchError(callback, "Failed to delete user (" + response.code() + ")");
                }
            }
        });
    }

    public void adminSetUserPassword(String userId, String newPassword, ApiCallback<String> callback) {
        String json = gson.toJson(new SetPasswordBody(newPassword));
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/admin/users/" + userId + "/password")
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Password updated successfully");
                } else {
                    dispatchError(callback, "Failed to update password (" + response.code() + ")");
                }
            }
        });
    }

    public void adminGetAllTracks(ApiCallback<List<Track>> callback) {
        Request request = newAuthenticatedRequest("/api/admin/tracks").get().build();
        Type type = new TypeToken<List<Track>>() {}.getType();
        executeGenericCall(request, type, callback);
    }

    public void updateTrack(String trackId, String title, String artist, String album, boolean isPublic, ApiCallback<Track> callback) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        if (title != null) map.put("title", title);
        if (artist != null) map.put("artist", artist);
        if (album != null) map.put("album", album);
        map.put("is_public", isPublic);

        String json = gson.toJson(map);
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request request = newAuthenticatedRequest("/api/tracks/" + trackId).put(body).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    dispatchError(callback, extractError(responseBody, response.code()));
                    return;
                }
                try {
                    com.google.gson.JsonObject obj = gson.fromJson(responseBody, com.google.gson.JsonObject.class);
                    if (obj != null && obj.has("track")) {
                        Track track = gson.fromJson(obj.get("track"), Track.class);
                        dispatchSuccess(callback, track);
                    } else {
                        Track track = gson.fromJson(responseBody, Track.class);
                        dispatchSuccess(callback, track);
                    }
                } catch (Exception e) {
                    dispatchError(callback, "Parse error: " + e.getMessage());
                }
            }
        });
    }

    public void adminDeleteTrack(String trackId, ApiCallback<String> callback) {
        Request request = newAuthenticatedRequest("/api/admin/tracks/" + trackId).delete().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    dispatchSuccess(callback, "Track deleted successfully");
                } else {
                    dispatchError(callback, "Failed to delete track (" + response.code() + ")");
                }
            }
        });
    }

    // ---------------- HELPERS ----------------

    private byte[] readUriBytes(Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream()) {
            if (inputStream == null) return null;
            byte[] buffer = new byte[16384];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private <T> void executeCall(Request request, Class<T> clazz, ApiCallback<T> callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    dispatchError(callback, extractError(body, response.code()));
                    return;
                }
                try {
                    T parsed = gson.fromJson(body, clazz);
                    dispatchSuccess(callback, parsed);
                } catch (Exception e) {
                    dispatchError(callback, "Failed to parse response: " + e.getMessage());
                }
            }
        });
    }

    private <T> void executeGenericCall(Request request, Type type, ApiCallback<T> callback) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                dispatchError(callback, "Connection failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    dispatchError(callback, extractError(body, response.code()));
                    return;
                }
                try {
                    T parsed = gson.fromJson(body, type);
                    dispatchSuccess(callback, parsed);
                } catch (Exception e) {
                    dispatchError(callback, "Failed to parse response: " + e.getMessage());
                }
            }
        });
    }

    private String extractError(String body, int code) {
        try {
            ErrorResponse err = gson.fromJson(body, ErrorResponse.class);
            if (err != null && err.detail != null) return err.detail;
        } catch (Exception ignored) {}
        return "Request failed (HTTP " + code + ")";
    }

    private <T> void dispatchSuccess(ApiCallback<T> callback, T result) {
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void dispatchError(ApiCallback<T> callback, String error) {
        mainHandler.post(() -> callback.onError(error));
    }

    // Internal request bodies
    private static class LoginBody {
        String email, password;
        LoginBody(String e, String p) { email = e; password = p; }
    }
    private static class RegisterBody {
        String email, password, name;
        RegisterBody(String e, String p, String n) { email = e; password = p; name = n; }
    }
    private static class ResetPasswordBody {
        String old_password, new_password;
        ResetPasswordBody(String o, String n) { old_password = o; new_password = n; }
    }
    private static class SetPasswordBody {
        String new_password;
        SetPasswordBody(String n) { new_password = n; }
    }
    private static class CreatePlaylistBody {
        String title, description;
        boolean is_public;
        CreatePlaylistBody(String t, String d, boolean p) { title = t; description = d; is_public = p; }
    }
    private static class UpdatePlaylistBody {
        String title, description;
        UpdatePlaylistBody(String t, String d) { title = t; description = d; }
    }
    private static class AddTrackBody {
        String track_id;
        AddTrackBody(String tid) { track_id = tid; }
    }
    private static class LikeResponse {
        boolean isLiked;
    }
    private static class ErrorResponse {
        String detail;
    }
}
