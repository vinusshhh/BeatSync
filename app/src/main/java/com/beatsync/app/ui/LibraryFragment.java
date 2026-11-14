package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.FragmentLibraryBinding;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.models.User;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import com.beatsync.app.ui.adapters.TrackAdapter;
import java.util.List;

public class LibraryFragment extends Fragment implements MusicPlayerManager.PlaybackListener {

    private FragmentLibraryBinding binding;
    private PlaylistAdapter playlistAdapter;
    private TrackAdapter trackAdapter;
    private String currentTab = "playlists"; // "playlists", "private", "uploads"

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        User user = AuthManager.getInstance(requireContext()).getUser();
        if (user != null) {
            binding.tvUserName.setText(user.getName());
            binding.tvUserEmail.setText(user.getEmail());
            String initial = user.getName().isEmpty() ? "U" : user.getName().substring(0, 1).toUpperCase();
            binding.tvUserInitial.setText(initial);
        }

        // Setup Playlists Adapter
        playlistAdapter = new PlaylistAdapter(requireContext(), playlist -> {
            Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
            intent.putExtra("playlist", playlist);
            startActivity(intent);
        });
        binding.rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlaylists.setAdapter(playlistAdapter);

        // Setup Tracks Adapter
        trackAdapter = new TrackAdapter(requireContext(), (track, position, tracks) -> {
            MusicPlayerManager.getInstance(requireContext()).setQueue(tracks, position);
        }, (track, anchor) -> showTrackOptions(track, anchor));
        binding.rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTracks.setAdapter(trackAdapter);

        // Tabs
        binding.tabPlaylists.setOnClickListener(v -> switchTab("playlists"));
        binding.tabPrivateVault.setOnClickListener(v -> switchTab("private"));
        binding.tabMyUploads.setOnClickListener(v -> switchTab("uploads"));

        // Create Playlist
        binding.btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());

        // Logout
        binding.btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Sign Out")
                    .setMessage("Are you sure you want to sign out of BeatSync?")
                    .setPositiveButton("Sign Out", (dialog, which) -> {
                        AuthManager.getInstance(requireContext()).logout();
                        Intent intent = new Intent(requireContext(), LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.swipeRefresh.setOnRefreshListener(this::loadActiveContent);

        MusicPlayerManager.getInstance(requireContext()).addListener(this);

        switchTab("playlists");
    }

    @Override
    public void onResume() {
        super.onResume();
        loadActiveContent();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MusicPlayerManager.getInstance(requireContext()).removeListener(this);
        binding = null;
    }

    private void switchTab(String tab) {
        currentTab = tab;
        updateTabStyles();

        if (tab.equals("playlists")) {
            binding.rvPlaylists.setVisibility(View.VISIBLE);
            binding.rvTracks.setVisibility(View.GONE);
            binding.btnCreatePlaylist.setVisibility(View.VISIBLE);
        } else {
            binding.rvPlaylists.setVisibility(View.GONE);
            binding.rvTracks.setVisibility(View.VISIBLE);
            binding.btnCreatePlaylist.setVisibility(View.GONE);
        }

        loadActiveContent();
    }

    private void updateTabStyles() {
        int activeBg = R.drawable.bg_button_primary;
        int activeText = ContextCompat.getColor(requireContext(), R.color.text_inverse);
        int inactiveBg = R.drawable.bg_card_minimal;
        int inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        binding.tabPlaylists.setBackgroundResource(currentTab.equals("playlists") ? activeBg : inactiveBg);
        binding.tabPlaylists.setTextColor(currentTab.equals("playlists") ? activeText : inactiveText);

        binding.tabPrivateVault.setBackgroundResource(currentTab.equals("private") ? activeBg : inactiveBg);
        binding.tabPrivateVault.setTextColor(currentTab.equals("private") ? activeText : inactiveText);

        binding.tabMyUploads.setBackgroundResource(currentTab.equals("uploads") ? activeBg : inactiveBg);
        binding.tabMyUploads.setTextColor(currentTab.equals("uploads") ? activeText : inactiveText);
    }

    private void loadActiveContent() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);

        if (currentTab.equals("playlists")) {
            loadPlaylists();
        } else if (currentTab.equals("private")) {
            loadTracks("private", "No private tracks found in your vault.\nUpload music with 'Private' enabled to lock it here.");
        } else {
            loadTracks("mine", "No uploads yet.\nGo to Upload tab to release your sound.");
        }
    }

    private void loadPlaylists() {
        ApiClient.getInstance(requireContext()).getPlaylists(new ApiClient.ApiCallback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                if (playlists != null && !playlists.isEmpty()) {
                    playlistAdapter.setPlaylists(playlists);
                    binding.tvEmptyState.setVisibility(View.GONE);
                } else {
                    playlistAdapter.setPlaylists(null);
                    binding.tvEmptyState.setText("No playlists found.\nTap 'Create New Playlist' above to get started.");
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                binding.tvEmptyState.setText("Error loading playlists: " + error);
                binding.tvEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadTracks(String filter, String emptyMessage) {
        ApiClient.getInstance(requireContext()).getTracks(null, filter, new ApiClient.ApiCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                if (tracks != null && !tracks.isEmpty()) {
                    trackAdapter.setTracks(tracks);
                    binding.tvEmptyState.setVisibility(View.GONE);
                } else {
                    trackAdapter.setTracks(null);
                    binding.tvEmptyState.setText(emptyMessage);
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                binding.tvEmptyState.setText("Error loading tracks: " + error);
                binding.tvEmptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_playlist, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etTitle = dialogView.findViewById(R.id.etPlaylistTitle);
        EditText etDesc = dialogView.findViewById(R.id.etPlaylistDesc);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        View btnCreate = dialogView.findViewById(R.id.btnCreate);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            if (title.isEmpty()) {
                etTitle.setError("Playlist title required");
                return;
            }

            ApiClient.getInstance(requireContext()).createPlaylist(title, desc, true, new ApiClient.ApiCallback<Playlist>() {
                @Override
                public void onSuccess(Playlist result) {
                    Toast.makeText(requireContext(), "Playlist created!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadPlaylists();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(requireContext(), "Failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void showTrackOptions(Track track, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, track.isLiked() ? "Unlike Track" : "Like Track");
        popup.getMenu().add(0, 2, 1, "Add to Playlist");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                ApiClient.getInstance(requireContext()).toggleLike(track.getId(), new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isLiked) {
                        track.setLiked(isLiked);
                        trackAdapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), isLiked ? "Saved to Liked Songs" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            } else if (item.getItemId() == 2) {
                showAddToPlaylistDialog(track);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showAddToPlaylistDialog(Track track) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
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

        rvPicker.setLayoutManager(new LinearLayoutManager(requireContext()));
        pb.setVisibility(View.VISIBLE);

        ApiClient.getInstance(requireContext()).getPlaylists(new ApiClient.ApiCallback<List<Playlist>>() {
            @Override
            public void onSuccess(List<Playlist> playlists) {
                pb.setVisibility(View.GONE);
                if (playlists != null && !playlists.isEmpty()) {
                    PlaylistAdapter pAdapter = new PlaylistAdapter(requireContext(), p -> {
                        ApiClient.getInstance(requireContext()).addTrackToPlaylist(p.getId(), track.getId(), new ApiClient.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                Toast.makeText(requireContext(), "Added to " + p.getTitle(), Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    pAdapter.setPlaylists(playlists);
                    rvPicker.setAdapter(pAdapter);
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

    @Override
    public void onTrackChanged(Track track) {
        if (trackAdapter != null && track != null) {
            trackAdapter.setPlayingTrackId(track.getId());
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {}

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {}

    @Override
    public void onError(String message) {}
}
