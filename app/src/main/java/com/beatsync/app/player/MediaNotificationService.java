package com.beatsync.app.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.models.Track;
import com.beatsync.app.ui.NowPlayingActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.List;

public class MediaNotificationService extends Service implements MusicPlayerManager.PlaybackListener {

    public static final String CHANNEL_ID = "beatsync_playback_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_PLAY_PAUSE = "com.beatsync.app.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.beatsync.app.ACTION_NEXT";
    public static final String ACTION_PREV = "com.beatsync.app.ACTION_PREV";
    public static final String ACTION_STOP = "com.beatsync.app.ACTION_STOP";

    private MediaSessionCompat mediaSession;
    private MusicPlayerManager playerManager;
    private Bitmap currentCoverBitmap = null;
    private String currentCoverUrl = null;

    public static void start(Context context) {
        Intent intent = new Intent(context, MediaNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MediaNotificationService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        playerManager = MusicPlayerManager.getInstance(this);
        initMediaSession();

        Notification initialNotif = buildNotification(playerManager.getCurrentTrack(), playerManager.isPlaying(), null);
        startForeground(NOTIFICATION_ID, initialNotif);

        playerManager.addListener(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BeatSync Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Spotify-style lock screen & notification media player");
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "BeatSync_MediaSession");
        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playerManager.togglePlayPause();
            }

            @Override
            public void onPause() {
                playerManager.togglePlayPause();
            }

            @Override
            public void onSkipToNext() {
                playerManager.playNext();
            }

            @Override
            public void onSkipToPrevious() {
                playerManager.playPrevious();
            }

            @Override
            public void onSeekTo(long pos) {
                playerManager.seekTo((int) pos);
            }

            @Override
            public void onStop() {
                if (playerManager.isPlaying()) {
                    playerManager.togglePlayPause();
                }
                stopForeground(true);
                stopSelf();
            }
        });

        updatePlaybackState(playerManager.isPlaying(), playerManager.getCurrentPosition());
    }

    private void updatePlaybackState(boolean isPlaying, int positionMs) {
        if (mediaSession == null) return;

        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_STOP;

        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, 1.0f)
                .build());
    }

    private void updateMetadata(Track track, Bitmap coverBitmap) {
        if (mediaSession == null || track == null) return;

        MediaMetadataCompat.Builder metaBuilder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getAlbum() != null && !track.getAlbum().isEmpty() ? track.getAlbum() : "BeatSync Stream")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (long) track.getDurationSeconds() * 1000L);

        if (coverBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap);
        }

        mediaSession.setMetadata(metaBuilder.build());
    }

    private Bitmap getDefaultArtBitmap() {
        try {
            Drawable drawable = ContextCompat.getDrawable(this, R.drawable.bg_default_album_art);
            if (drawable != null) {
                Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return bitmap;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Notification buildNotification(Track track, boolean isPlaying, Bitmap coverBitmap) {
        int flag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        // Content intent: Opens NowPlayingActivity
        Intent openAppIntent = new Intent(this, NowPlayingActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, flag);

        // Previous Action
        Intent prevIntent = new Intent(this, MediaNotificationService.class);
        prevIntent.setAction(ACTION_PREV);
        PendingIntent pPrev = PendingIntent.getService(this, 1, prevIntent, flag);

        // Play/Pause Action
        Intent playPauseIntent = new Intent(this, MediaNotificationService.class);
        playPauseIntent.setAction(ACTION_PLAY_PAUSE);
        PendingIntent pPlayPause = PendingIntent.getService(this, 2, playPauseIntent, flag);

        // Next Action
        Intent nextIntent = new Intent(this, MediaNotificationService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent pNext = PendingIntent.getService(this, 3, nextIntent, flag);

        // Stop/Dismiss Action
        Intent stopIntent = new Intent(this, MediaNotificationService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent pStop = PendingIntent.getService(this, 4, stopIntent, flag);

        Bitmap art = coverBitmap != null ? coverBitmap : getDefaultArtBitmap();

        String title = track != null ? track.getTitle() : "BeatSync";
        String artist = track != null ? track.getArtist() : "Audio Stream";
        String album = track != null && track.getAlbum() != null && !track.getAlbum().isEmpty() ? track.getAlbum() : "BeatSync";

        androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(pStop);

        if (mediaSession != null) {
            mediaStyle.setMediaSession(mediaSession.getSessionToken());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(artist)
                .setSubText(album)
                .setLargeIcon(art)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setShowWhen(false)
                .setStyle(mediaStyle)
                .addAction(R.drawable.ic_skip_prev, "Previous", pPrev)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, isPlaying ? "Pause" : "Play", pPlayPause)
                .addAction(R.drawable.ic_skip_next, "Next", pNext);

        return builder.build();
    }

    private void updateNotification() {
        Track current = playerManager.getCurrentTrack();
        if (current == null) return;

        boolean isPlaying = playerManager.isPlaying();
        updatePlaybackState(isPlaying, playerManager.getCurrentPosition());

        String coverUrl = current.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            if (!coverUrl.equals(currentCoverUrl) || currentCoverBitmap == null) {
                currentCoverUrl = coverUrl;
                String fullCover = coverUrl.startsWith("http")
                        ? coverUrl
                        : AuthManager.getInstance(this).getBaseUrl() + coverUrl;

                Glide.with(this)
                        .asBitmap()
                        .load(fullCover)
                        .into(new CustomTarget<Bitmap>(300, 300) {
                            @Override
                            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                                currentCoverBitmap = resource;
                                updateMetadata(current, currentCoverBitmap);
                                Notification notification = buildNotification(current, isPlaying, currentCoverBitmap);
                                startForeground(NOTIFICATION_ID, notification);
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {}

                            @Override
                            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                currentCoverBitmap = null;
                                updateMetadata(current, getDefaultArtBitmap());
                                Notification notification = buildNotification(current, isPlaying, getDefaultArtBitmap());
                                startForeground(NOTIFICATION_ID, notification);
                            }
                        });
                return;
            }
        } else {
            currentCoverUrl = null;
            currentCoverBitmap = null;
        }

        updateMetadata(current, currentCoverBitmap);
        Notification notification = buildNotification(current, isPlaying, currentCoverBitmap);
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_PAUSE.equals(action)) {
                playerManager.togglePlayPause();
            } else if (ACTION_NEXT.equals(action)) {
                playerManager.playNext();
            } else if (ACTION_PREV.equals(action)) {
                playerManager.playPrevious();
            } else if (ACTION_STOP.equals(action)) {
                if (playerManager.isPlaying()) {
                    playerManager.togglePlayPause();
                }
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onTrackChanged(Track track) {
        currentCoverUrl = null;
        currentCoverBitmap = null;
        updateNotification();
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updatePlaybackState(isPlaying, playerManager.getCurrentPosition());
        updateNotification();
    }

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {
        // Light updates for scrubber precision
        if (mediaSession != null && playerManager.isPlaying()) {
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY
                            | PlaybackStateCompat.ACTION_PAUSE
                            | PlaybackStateCompat.ACTION_PLAY_PAUSE
                            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                            | PlaybackStateCompat.ACTION_SEEK_TO
                            | PlaybackStateCompat.ACTION_STOP)
                    .setState(PlaybackStateCompat.STATE_PLAYING, currentMs, 1.0f)
                    .build());
        }
    }

    @Override
    public void onError(String message) {}

    @Override
    public void onQueueUpdated(List<Track> queue) {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (playerManager != null) {
            playerManager.removeListener(this);
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        stopForeground(true);
    }
}
