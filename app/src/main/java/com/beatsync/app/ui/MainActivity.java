package com.beatsync.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.beatsync.app.R;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityMainBinding;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.utils.AnimationHelper;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private ActivityMainBinding binding;
    private final HomeFragment homeFragment = new HomeFragment();
    private final SearchFragment searchFragment = new SearchFragment();
    private final PlaylistsFragment playlistsFragment = new PlaylistsFragment();
    private final UploadFragment uploadFragment = new UploadFragment();
    private final LibraryFragment libraryFragment = new LibraryFragment();
    private final AdminFragment adminFragment = new AdminFragment();
    private Fragment activeFragment = homeFragment;
    private int currentTabIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean isAdmin = AuthManager.getInstance(this).isAdmin();
        binding.tabNavAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        // Setup fragments in container
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, libraryFragment, "vault").hide(libraryFragment)
                .add(R.id.fragmentContainer, uploadFragment, "upload").hide(uploadFragment)
                .add(R.id.fragmentContainer, playlistsFragment, "playlists").hide(playlistsFragment)
                .add(R.id.fragmentContainer, searchFragment, "search").hide(searchFragment)
                .add(R.id.fragmentContainer, homeFragment, "home");

        if (isAdmin) {
            transaction.add(R.id.fragmentContainer, adminFragment, "admin").hide(adminFragment);
        }
        transaction.commit();

        setupTabClickListeners();

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

        AnimationHelper.addPressScaleEffect(binding.miniBtnPlayPause);
        AnimationHelper.addPressScaleEffect(binding.miniBtnNext);

        MusicPlayerManager.getInstance(this).addListener(this);
    }

    private void setupTabClickListeners() {
        AnimationHelper.addPressScaleEffect(binding.tabNavHome);
        AnimationHelper.addPressScaleEffect(binding.tabNavSearch);
        AnimationHelper.addPressScaleEffect(binding.tabNavPlaylists);
        AnimationHelper.addPressScaleEffect(binding.tabNavUpload);
        AnimationHelper.addPressScaleEffect(binding.tabNavVault);
        AnimationHelper.addPressScaleEffect(binding.tabNavAdmin);

        binding.tabNavHome.setOnClickListener(v -> selectTab(0));
        binding.tabNavSearch.setOnClickListener(v -> selectTab(1));
        binding.tabNavPlaylists.setOnClickListener(v -> selectTab(2));
        binding.tabNavUpload.setOnClickListener(v -> selectTab(3));
        binding.tabNavVault.setOnClickListener(v -> selectTab(4));
        binding.tabNavAdmin.setOnClickListener(v -> selectTab(5));

        selectTab(0);
    }

    public void selectTab(int tabIndex) {
        if (tabIndex == R.id.nav_home) { selectTab(0); return; }
        if (tabIndex == R.id.nav_search) { selectTab(1); return; }
        if (tabIndex == R.id.nav_playlists) { selectTab(2); return; }
        if (tabIndex == R.id.nav_upload) { selectTab(3); return; }
        if (tabIndex == R.id.nav_library) { selectTab(4); return; }

        currentTabIndex = tabIndex;
        Fragment targetFragment = homeFragment;

        resetTabStyles();

        int activeColor = ContextCompat.getColor(this, R.color.text_primary);

        switch (tabIndex) {
            case 0:
                targetFragment = homeFragment;
                setTabActive(binding.iconNavHome, binding.labelNavHome, activeColor);
                break;
            case 1:
                targetFragment = searchFragment;
                setTabActive(binding.iconNavSearch, binding.labelNavSearch, activeColor);
                break;
            case 2:
                targetFragment = playlistsFragment;
                setTabActive(binding.iconNavPlaylists, binding.labelNavPlaylists, activeColor);
                break;
            case 3:
                targetFragment = uploadFragment;
                setTabActive(binding.iconNavUpload, binding.labelNavUpload, activeColor);
                break;
            case 4:
                targetFragment = libraryFragment;
                setTabActive(binding.iconNavVault, binding.labelNavVault, activeColor);
                break;
            case 5:
                targetFragment = adminFragment;
                setTabActive(binding.iconNavAdmin, binding.labelNavAdmin, activeColor);
                break;
        }

        switchFragment(targetFragment);
    }

    // Overload for compatibility with calls passing R.id
    public void selectTabById(int navId) {
        if (navId == R.id.nav_home) selectTab(0);
        else if (navId == R.id.nav_search) selectTab(1);
        else if (navId == R.id.nav_playlists) selectTab(2);
        else if (navId == R.id.nav_upload) selectTab(3);
        else if (navId == R.id.nav_library) selectTab(4);
    }

    private void resetTabStyles() {
        int inactiveColor = ContextCompat.getColor(this, R.color.text_tertiary);

        setTabInactive(binding.iconNavHome, binding.labelNavHome, inactiveColor);
        setTabInactive(binding.iconNavSearch, binding.labelNavSearch, inactiveColor);
        setTabInactive(binding.iconNavPlaylists, binding.labelNavPlaylists, inactiveColor);
        setTabInactive(binding.iconNavUpload, binding.labelNavUpload, inactiveColor);
        setTabInactive(binding.iconNavVault, binding.labelNavVault, inactiveColor);
        setTabInactive(binding.iconNavAdmin, binding.labelNavAdmin, inactiveColor);
    }

    private void setTabActive(ImageView icon, TextView label, int color) {
        icon.setColorFilter(color);
        icon.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start();
        label.setTextColor(color);
        label.setTypeface(null, Typeface.BOLD);
    }

    private void setTabInactive(ImageView icon, TextView label, int color) {
        icon.setColorFilter(color);
        icon.setScaleX(1.0f);
        icon.setScaleY(1.0f);
        label.setTextColor(color);
        label.setTypeface(null, Typeface.NORMAL);
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
            String fullCover = track.getCoverUrl().startsWith("http")
                    ? track.getCoverUrl()
                    : AuthManager.getInstance(this).getBaseUrl() + track.getCoverUrl();
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
        }
    }

    @Override
    public void onError(String message) {
    }
}
