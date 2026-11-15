package com.beatsync.app.ui;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityNowPlayingBinding;
import com.beatsync.app.models.LyricsResponse;
import com.beatsync.app.models.LyricsResponse.LyricLine;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.LyricsAdapter;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import com.beatsync.app.ui.utils.AnimationHelper;
import com.bumptech.glide.Glide;
import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private ActivityNowPlayingBinding binding;
    private MusicPlayerManager playerManager;
    private LyricsAdapter lyricsAdapter;
    private ObjectAnimator vinylAnimator;
    private ObjectAnimator liquidBreathingAnimator;
    private boolean isUserSeeking = false;
    private boolean isLyricsMode = false;
    private String currentLyricsTrackId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNowPlayingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playerManager = MusicPlayerManager.getInstance(this);

        setupAestheticsAndAnimations();
        setupLyricsView();

        binding.btnCollapse.setOnClickListener(v -> finish());
        binding.btnPlayPauseContainer.setOnClickListener(v -> playerManager.togglePlayPause());
        binding.btnNext.setOnClickListener(v -> playerManager.playNext());
        binding.btnPrevious.setOnClickListener(v -> playerManager.playPrevious());

        binding.btnShuffle.setOnClickListener(v -> {
            playerManager.toggleShuffle();
            updateShuffleButton();
        });

        binding.btnRepeat.setOnClickListener(v -> {
            playerManager.cycleRepeatMode();
            updateRepeatButton();
        });

        binding.btnLike.setOnClickListener(v -> {
            Track current = playerManager.getCurrentTrack();
            if (current != null) {
                ApiClient.getInstance(this).toggleLike(current.getId(), new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isLiked) {
                        current.setLiked(isLiked);
                        updateLikeButton(isLiked);
                        Toast.makeText(NowPlayingActivity.this, isLiked ? "Saved to Liked Songs" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(NowPlayingActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        binding.btnAddToPlaylist.setOnClickListener(v -> {
            Track current = playerManager.getCurrentTrack();
            if (current != null) {
                showAddToPlaylistDialog(current);
            }
        });

        binding.btnToggleLyrics.setOnClickListener(v -> toggleLyricsView());

        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int total = playerManager.getDuration();
                    if (total > 0) {
                        int currentMs = (int) (((long) progress * total) / 1000);
                        binding.tvCurrentTime.setText(formatTime(currentMs));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                int total = playerManager.getDuration();
                if (total > 0) {
                    int seekMs = (int) (((long) seekBar.getProgress() * total) / 1000);
                    playerManager.seekTo(seekMs);
                }
            }
        });

        playerManager.addListener(this);

        Track current = playerManager.getCurrentTrack();
        if (current != null) {
            updateUi(current);
        } else {
            finish();
        }
    }

    private void setupAestheticsAndAnimations() {
        AnimationHelper.addPressScaleEffect(binding.btnPlayPauseContainer);
        AnimationHelper.addPressScaleEffect(binding.btnNext);
        AnimationHelper.addPressScaleEffect(binding.btnPrevious);
        AnimationHelper.addPressScaleEffect(binding.btnShuffle);
        AnimationHelper.addPressScaleEffect(binding.btnRepeat);
        AnimationHelper.addPressScaleEffect(binding.btnLike);
        AnimationHelper.addPressScaleEffect(binding.btnAddToPlaylist);
        AnimationHelper.addPressScaleEffect(binding.btnToggleLyrics);

        vinylAnimator = AnimationHelper.createContinuousRotation(binding.ivVinylCenter, 24000);
        if (vinylAnimator != null && playerManager.isPlaying()) {
            vinylAnimator.start();
        }

        liquidBreathingAnimator = AnimationHelper.createLiquidBreathing(binding.cardArtContainer);
        if (liquidBreathingAnimator != null && playerManager.isPlaying()) {
            liquidBreathingAnimator.start();
        }
    }

    private void setupLyricsView() {
        lyricsAdapter = new LyricsAdapter();
        binding.rvLyrics.setLayoutManager(new LinearLayoutManager(this));
        binding.rvLyrics.setAdapter(lyricsAdapter);

        lyricsAdapter.setOnLineClickListener(line -> {
            playerManager.seekTo((int) line.getTimeMs());
            Toast.makeText(this, "Jumped to " + formatTime((int) line.getTimeMs()), Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleLyricsView() {
        isLyricsMode = !isLyricsMode;
        if (isLyricsMode) {
            binding.cardArtContainer.setVisibility(View.GONE);
            binding.layoutLyricsContainer.setVisibility(View.VISIBLE);
            binding.btnToggleLyrics.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan));
        } else {
            binding.layoutLyricsContainer.setVisibility(View.GONE);
            binding.cardArtContainer.setVisibility(View.VISIBLE);
            binding.btnToggleLyrics.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void fetchLyrics(Track track) {
        if (track == null) return;
        if (track.getId().equals(currentLyricsTrackId)) return;

        currentLyricsTrackId = track.getId();
        binding.pbLyricsLoading.setVisibility(View.VISIBLE);
        binding.tvLyricsEmpty.setVisibility(View.GONE);

        ApiClient.getInstance(this).getLyrics(track.getTitle(), track.getArtist(), track.getDurationSeconds(), new ApiClient.ApiCallback<LyricsResponse>() {
            @Override
            public void onSuccess(LyricsResponse response) {
                binding.pbLyricsLoading.setVisibility(View.GONE);
                if (response != null && response.isFound()) {
                    List<LyricLine> lines = response.getParsedLines();
                    if (!lines.isEmpty()) {
                        lyricsAdapter.setLines(lines);
                        binding.tvLyricsEmpty.setVisibility(View.GONE);
                        binding.rvLyrics.setVisibility(View.VISIBLE);
                    } else if (response.getPlainLyrics() != null && !response.getPlainLyrics().isEmpty()) {
                        binding.tvLyricsEmpty.setText(response.getPlainLyrics());
                        binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                        binding.rvLyrics.setVisibility(View.GONE);
                    } else {
                        binding.tvLyricsEmpty.setText("No synchronized lyrics available for this track.");
                        binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                        binding.rvLyrics.setVisibility(View.GONE);
                    }
                } else {
                    lyricsAdapter.setLines(null);
                    binding.tvLyricsEmpty.setText("No lyrics found for \"" + track.getTitle() + "\".");
                    binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                    binding.rvLyrics.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String error) {
                binding.pbLyricsLoading.setVisibility(View.GONE);
                lyricsAdapter.setLines(null);
                binding.tvLyricsEmpty.setText("Lyrics search unavailable");
                binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                binding.rvLyrics.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playerManager.removeListener(this);
        if (vinylAnimator != null) {
            vinylAnimator.cancel();
        }
        if (liquidBreathingAnimator != null) {
            liquidBreathingAnimator.cancel();
        }
    }

    private void updateUi(Track track) {
        binding.tvTrackTitle.setText(track.getTitle());
        binding.tvTrackTitle.setSelected(true); // marquee
        binding.tvTrackArtist.setText(track.getArtist() + (track.getAlbum() != null && !track.getAlbum().isEmpty() ? " • " + track.getAlbum() : ""));
        binding.tvHeaderSubtitle.setText("From " + track.getUploaderName());

        // Privacy Badge
        if (track.isPublic()) {
            binding.tvPrivacyBadge.setText("PUBLIC RELEASE");
            binding.tvPrivacyBadge.setBackgroundResource(R.drawable.bg_badge_public);
            binding.tvPrivacyBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_public_text));
        } else {
            binding.tvPrivacyBadge.setText("PRIVATE VAULT");
            binding.tvPrivacyBadge.setBackgroundResource(R.drawable.bg_badge_private);
            binding.tvPrivacyBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_private_text));
        }

        // Cover Art
        if (track.getCoverUrl() != null && !track.getCoverUrl().isEmpty()) {
            String fullCover = track.getCoverUrl().startsWith("http")
                    ? track.getCoverUrl()
                    : AuthManager.getInstance(this).getBaseUrl() + track.getCoverUrl();
            binding.layoutDefaultArt.setVisibility(View.GONE);
            binding.ivBigCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(fullCover).centerCrop().into(binding.ivBigCover);
        } else {
            binding.ivBigCover.setVisibility(View.GONE);
            binding.layoutDefaultArt.setVisibility(View.VISIBLE);
        }

        updatePlayPauseButton(playerManager.isPlaying());
        updateShuffleButton();
        updateRepeatButton();
        updateLikeButton(track.isLiked());

        // Load Synced Lyrics
        fetchLyrics(track);
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        binding.btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        if (vinylAnimator != null) {
            if (isPlaying) {
                if (vinylAnimator.isPaused()) vinylAnimator.resume();
                else if (!vinylAnimator.isStarted()) vinylAnimator.start();
            } else {
                if (vinylAnimator.isRunning()) vinylAnimator.pause();
            }
        }
        if (liquidBreathingAnimator != null) {
            if (isPlaying) {
                if (liquidBreathingAnimator.isPaused()) liquidBreathingAnimator.resume();
                else if (!liquidBreathingAnimator.isStarted()) liquidBreathingAnimator.start();
            } else {
                if (liquidBreathingAnimator.isRunning()) liquidBreathingAnimator.pause();
            }
        }
    }

    private void updateShuffleButton() {
        boolean shuffle = playerManager.isShuffle();
        binding.btnShuffle.setColorFilter(ContextCompat.getColor(this, shuffle ? R.color.accent_cyan : R.color.text_tertiary));
    }

    private void updateRepeatButton() {
        MusicPlayerManager.RepeatMode mode = playerManager.getRepeatMode();
        if (mode == MusicPlayerManager.RepeatMode.OFF) {
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary));
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
        } else {
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan));
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
        }
    }

    private void updateLikeButton(boolean isLiked) {
        if (isLiked) {
            binding.btnLike.setImageResource(R.drawable.ic_heart_filled);
            binding.btnLike.setColorFilter(ContextCompat.getColor(this, R.color.accent_rose));
        } else {
            binding.btnLike.setImageResource(R.drawable.ic_heart);
            binding.btnLike.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void showAddToPlaylistDialog(Track track) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_to_playlist, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTarget = dialogView.findViewById(R.id.tvTrackTarget);
        tvTarget.setText("Add \"" + track.getTitle() + "\" to:");

        ProgressBar pb = dialogView.findViewById(R.id.progressBar);
        TextView tvNoPlaylists = dialogView.findViewById(R.id.tvNoPlaylists);
        RecyclerView rvPicker = dialogView.findViewById(R.id.rvPlaylistsPicker);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        rvPicker.setLayoutManager(new LinearLayoutManager(this));
        pb.setVisibility(View.VISIBLE);

        ApiClient.getInstance(this).getPlaylists(new ApiClient.ApiCallback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                pb.setVisibility(View.GONE);
                if (playlists != null && !playlists.isEmpty()) {
                    PlaylistAdapter adapter = new PlaylistAdapter(NowPlayingActivity.this, p -> {
                        ApiClient.getInstance(NowPlayingActivity.this).addTrackToPlaylist(p.getId(), track.getId(), new ApiClient.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                Toast.makeText(NowPlayingActivity.this, "Added to " + p.getTitle(), Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(NowPlayingActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    adapter.setPlaylists(playlists);
                    rvPicker.setAdapter(adapter);
                } else {
                    tvNoPlaylists.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                pb.setVisibility(View.GONE);
                tvNoPlaylists.setText("Failed to load playlists: " + error);
                tvNoPlaylists.setVisibility(View.VISIBLE);
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    public void onTrackChanged(Track track) {
        if (track != null) {
            updateUi(track);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updatePlayPauseButton(isPlaying);
    }

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {
        if (!isUserSeeking) {
            binding.tvCurrentTime.setText(formatTime(currentMs));
            binding.tvTotalTime.setText(formatTime(totalMs));
            if (totalMs > 0) {
                int progress = (int) (((long) currentMs * 1000) / totalMs);
                binding.seekBar.setProgress(progress);
            }
        }

        // Synchronize Active Lyric Line
        if (lyricsAdapter != null && lyricsAdapter.getLineCount() > 0) {
            int lineCount = lyricsAdapter.getLineCount();
            int matchedIndex = -1;

            for (int i = 0; i < lineCount; i++) {
                LyricLine line = lyricsAdapter.getLine(i);
                long nextTime = (i + 1 < lineCount) ? lyricsAdapter.getLine(i + 1).getTimeMs() : Long.MAX_VALUE;
                if (currentMs >= line.getTimeMs() && currentMs < nextTime) {
                    matchedIndex = i;
                    break;
                }
            }

            if (matchedIndex != -1 && lyricsAdapter.getActivePosition() != matchedIndex) {
                lyricsAdapter.setActivePosition(matchedIndex);
                binding.rvLyrics.smoothScrollToPosition(matchedIndex);
            }
        }
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
