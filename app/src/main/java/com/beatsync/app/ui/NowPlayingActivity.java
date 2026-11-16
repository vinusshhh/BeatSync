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
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import com.beatsync.app.ui.utils.AnimationHelper;
import com.bumptech.glide.Glide;
import java.util.List;
import java.util.Locale;

public class NowPlayingActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private ActivityNowPlayingBinding binding;
    private MusicPlayerManager playerManager;
    private boolean isUserSeeking = false;
    private boolean isLyricsMode = false;
    private String currentLyricsTrackId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNowPlayingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Edge-to-edge transparent system bars
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            float density = getResources().getDisplayMetrics().density;
            int baseTop = (int) (16 * density);
            int baseBottom = (int) (14 * density);
            binding.getRoot().setPadding(
                    binding.getRoot().getPaddingStart(),
                    systemBars.top + baseTop,
                    binding.getRoot().getPaddingEnd(),
                    systemBars.bottom + baseBottom
            );
            return insets;
        });

        playerManager = MusicPlayerManager.getInstance(this);

        setupAestheticsAndAnimations();
        setupLyricsView();

        binding.btnCollapse.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnCollapse);
            finish();
        });

        binding.btnPlayPauseContainer.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnPlayPauseContainer);
            playerManager.togglePlayPause();
        });

        binding.btnNext.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnNext);
            playerManager.playNext();
        });

        binding.btnPrevious.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnPrevious);
            playerManager.playPrevious();
        });

        binding.btnShuffle.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnShuffle);
            playerManager.toggleShuffle();
            updateShuffleButton();
        });

        binding.btnRepeat.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnRepeat);
            playerManager.cycleRepeatMode();
            updateRepeatButton();
        });

        binding.btnAddToPlaylist.setOnClickListener(v -> {
            AnimationHelper.rubberPop(binding.btnAddToPlaylist);
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
        AnimationHelper.addPressScaleEffect(binding.btnAddToPlaylist);
        AnimationHelper.addPressScaleEffect(binding.btnToggleLyrics);

        if (binding.musicPulseVisualizer != null) {
            binding.musicPulseVisualizer.setPlaying(playerManager.isPlaying());
        }
    }

    private void setupLyricsView() {
        binding.tvLyricsContent.setText("");
        binding.tvLyricsEmpty.setVisibility(View.GONE);
    }

    private void toggleLyricsView() {
        isLyricsMode = !isLyricsMode;
        AnimationHelper.rubberPop(binding.btnToggleLyrics);
        if (isLyricsMode) {
            binding.cardArtContainer.animate()
                    .scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(160)
                    .withEndAction(() -> {
                        binding.cardArtContainer.setVisibility(View.GONE);
                        binding.layoutLyricsContainer.setVisibility(View.VISIBLE);
                        binding.layoutLyricsContainer.setScaleX(0.86f);
                        binding.layoutLyricsContainer.setScaleY(0.86f);
                        binding.layoutLyricsContainer.setAlpha(0f);
                        binding.layoutLyricsContainer.animate()
                                .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                                .setDuration(280)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.8f))
                                .start();
                    }).start();
            binding.btnToggleLyrics.setColorFilter(ContextCompat.getColor(this, R.color.text_primary));
        } else {
            binding.layoutLyricsContainer.animate()
                    .scaleX(0.86f).scaleY(0.86f).alpha(0f).setDuration(160)
                    .withEndAction(() -> {
                        binding.layoutLyricsContainer.setVisibility(View.GONE);
                        binding.cardArtContainer.setVisibility(View.VISIBLE);
                        binding.cardArtContainer.setScaleX(0.86f);
                        binding.cardArtContainer.setScaleY(0.86f);
                        binding.cardArtContainer.setAlpha(0f);
                        binding.cardArtContainer.animate()
                                .scaleX(1.0f).scaleY(1.0f).alpha(1.0f)
                                .setDuration(280)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2.8f))
                                .start();
                    }).start();
            binding.btnToggleLyrics.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void fetchLyrics(Track track) {
        if (track == null) return;
        if (track.getId().equals(currentLyricsTrackId)) return;

        currentLyricsTrackId = track.getId();
        binding.pbLyricsLoading.setVisibility(View.VISIBLE);
        binding.tvLyricsEmpty.setVisibility(View.GONE);
        binding.scrollLyrics.setVisibility(View.GONE);

        ApiClient.getInstance(this).getLyrics(track.getTitle(), track.getArtist(), track.getDurationSeconds(), new ApiClient.ApiCallback<LyricsResponse>() {
            @Override
            public void onSuccess(LyricsResponse response) {
                binding.pbLyricsLoading.setVisibility(View.GONE);
                if (response != null && response.isFound()) {
                    String cleanLyrics = response.getCleanLyricalText();
                    if (!cleanLyrics.isEmpty()) {
                        binding.tvLyricsContent.setText(cleanLyrics);
                        binding.tvLyricsEmpty.setVisibility(View.GONE);
                        binding.scrollLyrics.setVisibility(View.VISIBLE);
                        // Reset scroll position to top for new song
                        binding.scrollLyrics.scrollTo(0, 0);
                        return;
                    }
                }
                binding.tvLyricsContent.setText("");
                binding.tvLyricsEmpty.setText("No lyrics available for \"" + track.getTitle() + "\".");
                binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                binding.scrollLyrics.setVisibility(View.GONE);
            }

            @Override
            public void onError(String error) {
                binding.pbLyricsLoading.setVisibility(View.GONE);
                binding.tvLyricsContent.setText("");
                binding.tvLyricsEmpty.setText("Lyrics search unavailable");
                binding.tvLyricsEmpty.setVisibility(View.VISIBLE);
                binding.scrollLyrics.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        playerManager.removeListener(this);
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

        updatePlayPauseButton(playerManager.isPlaying());
        updateShuffleButton();
        updateRepeatButton();

        // Load Synced Lyrics
        fetchLyrics(track);
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        binding.btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        if (binding.musicPulseVisualizer != null) {
            binding.musicPulseVisualizer.setPlaying(isPlaying);
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
        } else if (mode == MusicPlayerManager.RepeatMode.ONE) {
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan));
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one);
        } else {
            binding.btnRepeat.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan));
            binding.btnRepeat.setImageResource(R.drawable.ic_repeat);
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
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
