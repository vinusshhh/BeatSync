package com.beatsync.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityMainBinding;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private ActivityMainBinding binding;
    private final HomeFragment homeFragment = new HomeFragment();
    private final SearchFragment searchFragment = new SearchFragment();
    private final PlaylistsFragment playlistsFragment = new PlaylistsFragment();
    private final UploadFragment uploadFragment = new UploadFragment();
    private final LibraryFragment libraryFragment = new LibraryFragment();
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup fragments in container
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, libraryFragment, "5").hide(libraryFragment)
                .add(R.id.fragmentContainer, uploadFragment, "4").hide(uploadFragment)
                .add(R.id.fragmentContainer, playlistsFragment, "3").hide(playlistsFragment)
                .add(R.id.fragmentContainer, searchFragment, "2").hide(searchFragment)
                .add(R.id.fragmentContainer, homeFragment, "1")
                .commit();

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                switchFragment(searchFragment);
                return true;
            } else if (itemId == R.id.nav_playlists) {
                switchFragment(playlistsFragment);
                return true;
            } else if (itemId == R.id.nav_upload) {
                switchFragment(uploadFragment);
                return true;
            } else if (itemId == R.id.nav_library) {
                switchFragment(libraryFragment);
                return true;
            }
            return false;
        });

        // Mini player clicks
        binding.miniPlayerLayout.setOnClickListener(v -> {
            Track current = MusicPlayerManager.getInstance(this).getCurrentTrack();
            if (current != null) {
                Intent intent = new Intent(MainActivity.this, NowPlayingActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        binding.miniBtnPlayPause.setOnClickListener(v -> {
            MusicPlayerManager.getInstance(this).togglePlayPause();
        });

        binding.miniBtnNext.setOnClickListener(v -> {
            MusicPlayerManager.getInstance(this).playNext();
        });

        MusicPlayerManager.getInstance(this).addListener(this);
    }

    public void selectTab(int navId) {
        binding.bottomNavigation.setSelectedItemId(navId);
    }

    private void switchFragment(Fragment target) {
        if (target != activeFragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit();
            activeFragment = target;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Track current = MusicPlayerManager.getInstance(this).getCurrentTrack();
        if (current != null) {
            updateMiniPlayer(current);
            binding.miniPlayerLayout.setVisibility(View.VISIBLE);
        } else {
            binding.miniPlayerLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicPlayerManager.getInstance(this).removeListener(this);
    }

    private void updateMiniPlayer(Track track) {
        binding.miniTitle.setText(track.getTitle());
        binding.miniTitle.setSelected(true); // Enable marquee
        binding.miniArtist.setText(track.getArtist());

        if (track.getCoverUrl() != null && !track.getCoverUrl().isEmpty()) {
            String fullCover = AuthManager.getInstance(this).getBaseUrl() + track.getCoverUrl();
            binding.miniDefaultNote.setVisibility(View.GONE);
            binding.miniCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(fullCover).centerCrop().into(binding.miniCover);
        } else {
            binding.miniCover.setVisibility(View.GONE);
            binding.miniDefaultNote.setVisibility(View.VISIBLE);
        }

        boolean isPlaying = MusicPlayerManager.getInstance(this).isPlaying();
        binding.miniBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onTrackChanged(Track track) {
        if (track != null) {
            binding.miniPlayerLayout.setVisibility(View.VISIBLE);
            updateMiniPlayer(track);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        binding.miniBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {
        if (totalMs > 0) {
            int progress = (int) (((long) currentMs * 1000) / totalMs);
            binding.miniProgressBar.setProgress(progress);
        } else {
            binding.miniProgressBar.setProgress(0);
        }
    }

    @Override
    public void onError(String message) {}
}
