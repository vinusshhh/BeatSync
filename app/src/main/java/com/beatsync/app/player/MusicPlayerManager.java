package com.beatsync.app.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.models.Track;
import java.io.IOException;
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

        try {
            isPreparing = true;
            mediaPlayer.reset();
            
            // Build streaming url with auth token query param
            String baseUrl = AuthManager.getInstance(context).getBaseUrl();
            String streamUrl = baseUrl + track.getFileUrl();
            String token = AuthManager.getInstance(context).getToken();
            if (token != null) {
                streamUrl += (streamUrl.contains("?") ? "&" : "?") + "token=" + token;
            }

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
        } else if (repeatMode == RepeatMode.ALL) {
            currentIndex = 0;
            playCurrentIndex();
        } else {
            notifyState(false);
        }
    }

    public void playPrevious() {
        if (playlistQueue.isEmpty()) return;
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            seekTo(0);
            return;
        }

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
