package com.beatsync.app.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.models.Track;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicPlayerManager {
    public enum RepeatMode {
        OFF, ALL, ONE
    }

    public interface PlaybackListener {
        void onTrackChanged(Track track);
        void onPlaybackStateChanged(boolean isPlaying);
        void onProgressUpdate(int currentMs, int totalMs);
        void onError(String message);
        default void onQueueUpdated(List<Track> queue) {}
    }

    private static MusicPlayerManager instance;
    private final Context context;
    private MediaPlayer mediaPlayer;
    private final List<Track> playlistQueue = new ArrayList<>();
    private final List<Track> originalQueue = new ArrayList<>();
    private int currentIndex = -1;

    private boolean isShuffle = false;
    private RepeatMode repeatMode = RepeatMode.OFF;

    private final List<PlaybackListener> listeners = new ArrayList<>();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private boolean isPreparing = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && !isPreparing) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        int current = mediaPlayer.getCurrentPosition();
                        int total = mediaPlayer.getDuration();
                        notifyProgress(current, total);
                    }
                } catch (IllegalStateException ignored) {}
            }
            progressHandler.postDelayed(this, 300);
        }
    };

    private MusicPlayerManager(Context context) {
        this.context = context.getApplicationContext();
        initMediaPlayer();
        progressHandler.post(progressRunnable);
    }

    public static synchronized MusicPlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new MusicPlayerManager(context);
        }
        return instance;
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
        );

        mediaPlayer.setOnPreparedListener(mp -> {
            isPreparing = false;
            mp.start();
            notifyState(true);
            Track current = getCurrentTrack();
            if (current != null) {
                notifyTrackChanged(current);
            }
            MediaNotificationService.start(context);
        });

        mediaPlayer.setOnCompletionListener(mp -> {
            notifyState(false);
            handleTrackCompletion();
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            isPreparing = false;
            notifyError("Streaming error (Code: " + what + ")");
            return true;
        });
    }

    public void playTrack(Track track) {
        List<Track> singleList = new ArrayList<>();
        singleList.add(track);
        setQueue(singleList, 0);
    }

    public void setQueue(List<Track> tracks, int startIndex) {
        if (tracks == null || tracks.isEmpty()) return;
        originalQueue.clear();
        originalQueue.addAll(tracks);
        playlistQueue.clear();
        playlistQueue.addAll(tracks);

        if (isShuffle) {
            Track selected = tracks.get(Math.min(startIndex, tracks.size() - 1));
            Collections.shuffle(playlistQueue);
            playlistQueue.remove(selected);
            playlistQueue.add(0, selected);
            currentIndex = 0;
        } else {
            currentIndex = Math.max(0, Math.min(startIndex, playlistQueue.size() - 1));
        }

        notifyQueueUpdated();
        playCurrentIndex();
    }

    private void playCurrentIndex() {
        if (currentIndex < 0 || currentIndex >= playlistQueue.size()) return;
        Track track = playlistQueue.get(currentIndex);
        notifyTrackChanged(track);
        MediaNotificationService.start(context);

        // Build streaming url with auth token query param (or direct CDN url)
        String streamUrl;
        if (track.getFileUrl() != null && track.getFileUrl().startsWith("http")) {
            streamUrl = track.getFileUrl();
        } else {
            String baseUrl = AuthManager.getInstance(context).getBaseUrl();
            streamUrl = baseUrl + track.getFileUrl();
            String token = AuthManager.getInstance(context).getToken();
            if (token != null) {
                streamUrl += (streamUrl.contains("?") ? "&" : "?") + "token=" + token;
            }
        }
        final String finalStreamUrl = streamUrl;

        File cacheDir = new File(context.getCacheDir(), "audio_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        File localFile = new File(cacheDir, track.getId() + ".mp3");

        if (localFile.exists() && localFile.length() > 5000) {
            playLocalFile(localFile);
            return;
        }

        isPreparing = true;
        Request request = new Request.Builder().url(finalStreamUrl).build();
        ApiClient.getInstance(context).getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> playDirectStream(finalStreamUrl));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    new Handler(Looper.getMainLooper()).post(() -> playDirectStream(finalStreamUrl));
                    return;
                }

                File tempFile = new File(cacheDir, track.getId() + ".tmp");
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                    fos.flush();
                }

                File target = tempFile.renameTo(localFile) ? localFile : tempFile;
                new Handler(Looper.getMainLooper()).post(() -> playLocalFile(target));
            }
        });
    }

    private void playLocalFile(File file) {
        try {
            isPreparing = true;
            mediaPlayer.reset();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            isPreparing = false;
            notifyError("Unable to play track: " + e.getMessage());
        }
    }

    private void playDirectStream(String streamUrl) {
        try {
            isPreparing = true;
            mediaPlayer.reset();
            mediaPlayer.setDataSource(streamUrl);
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            isPreparing = false;
            notifyError("Unable to load track: " + e.getMessage());
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (isPreparing) return;

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                notifyState(false);
            } else {
                if (getCurrentTrack() != null) {
                    mediaPlayer.start();
                    notifyState(true);
                } else if (!playlistQueue.isEmpty()) {
                    playCurrentIndex();
                }
            }
        } catch (IllegalStateException e) {
            playCurrentIndex();
        }
    }

    public void playNext() {
        if (playlistQueue.isEmpty()) return;
        if (repeatMode == RepeatMode.ONE) {
            seekTo(0);
            if (!mediaPlayer.isPlaying()) mediaPlayer.start();
            notifyState(true);
            return;
        }

        if (currentIndex < playlistQueue.size() - 1) {
            currentIndex++;
            playCurrentIndex();
        } else {
            // Loop back to the first song in the list when reaching the end
            currentIndex = 0;
            playCurrentIndex();
        }
    }

    public void playPrevious() {
        if (playlistQueue.isEmpty()) return;

        if (currentIndex > 0) {
            currentIndex--;
            playCurrentIndex();
        } else {
            currentIndex = playlistQueue.size() - 1;
            playCurrentIndex();
        }
    }

    private void handleTrackCompletion() {
        if (repeatMode == RepeatMode.ONE) {
            playCurrentIndex();
        } else {
            playNext();
        }
    }

    public void seekTo(int progressMs) {
        if (mediaPlayer != null && !isPreparing) {
            try {
                mediaPlayer.seekTo(progressMs);
            } catch (IllegalStateException ignored) {}
        }
    }

    public boolean isPlaying() {
        if (mediaPlayer == null || isPreparing) return false;
        try {
            return mediaPlayer.isPlaying();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public Track getCurrentTrack() {
        if (currentIndex >= 0 && currentIndex < playlistQueue.size()) {
            return playlistQueue.get(currentIndex);
        }
        return null;
    }

    public int getCurrentPosition() {
        if (mediaPlayer == null || isPreparing) return 0;
        try {
            return mediaPlayer.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public int getDuration() {
        if (mediaPlayer == null || isPreparing) return 0;
        try {
            return mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public void toggleShuffle() {
        isShuffle = !isShuffle;
        Track current = getCurrentTrack();
        if (isShuffle) {
            Collections.shuffle(playlistQueue);
            if (current != null) {
                playlistQueue.remove(current);
                playlistQueue.add(0, current);
                currentIndex = 0;
            }
        } else {
            playlistQueue.clear();
            playlistQueue.addAll(originalQueue);
            if (current != null) {
                currentIndex = playlistQueue.indexOf(current);
            }
        }
        notifyQueueUpdated();
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void cycleRepeatMode() {
        if (repeatMode == RepeatMode.OFF) {
            repeatMode = RepeatMode.ALL;
        } else if (repeatMode == RepeatMode.ALL) {
            repeatMode = RepeatMode.ONE;
        } else {
            repeatMode = RepeatMode.OFF;
        }
    }

    public void addListener(PlaybackListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Track current = getCurrentTrack();
            if (current != null) {
                listener.onTrackChanged(current);
                listener.onPlaybackStateChanged(isPlaying());
                listener.onProgressUpdate(getCurrentPosition(), getDuration());
            }
        }
    }

    public void removeListener(PlaybackListener listener) {
        listeners.remove(listener);
    }

    private void notifyTrackChanged(Track track) {
        for (PlaybackListener l : new ArrayList<>(listeners)) {
            l.onTrackChanged(track);
        }
    }

    private void notifyState(boolean isPlaying) {
        for (PlaybackListener l : new ArrayList<>(listeners)) {
            l.onPlaybackStateChanged(isPlaying);
        }
    }

    private void notifyProgress(int current, int total) {
        for (PlaybackListener l : new ArrayList<>(listeners)) {
            l.onProgressUpdate(current, total);
        }
    }

    private void notifyError(String err) {
        for (PlaybackListener l : new ArrayList<>(listeners)) {
            l.onError(err);
        }
    }

    private void notifyQueueUpdated() {
        for (PlaybackListener l : new ArrayList<>(listeners)) {
            l.onQueueUpdated(playlistQueue);
        }
    }
}
