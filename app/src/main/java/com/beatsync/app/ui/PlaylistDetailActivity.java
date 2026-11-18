package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityPlaylistDetailBinding;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.TrackAdapter;
import com.bumptech.glide.Glide;
import java.util.List;

public class PlaylistDetailActivity extends AppCompatActivity implements MusicPlayerManager.PlaybackListener {

    private ActivityPlaylistDetailBinding binding;
    private Playlist playlist;
    private TrackAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlaylistDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playlist = (Playlist) getIntent().getSerializableExtra("playlist");
        if (playlist == null) {
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> finish());

        binding.tvHeaderTitle.setText(playlist.getTitle());
        binding.tvTitle.setText(playlist.getTitle());
        binding.tvDescription.setText(playlist.getDescription().isEmpty() ? "No description provided." : playlist.getDescription());
        binding.tvMeta.setText("By " + playlist.getOwnerName() + " • " + playlist.getTrackCount() + " tracks");

        if (playlist.getCoverUrl() != null && !playlist.getCoverUrl().isEmpty()) {
            String fullCover = AuthManager.getInstance(this).getBaseUrl() + playlist.getCoverUrl();
            binding.ivDefaultIcon.setVisibility(View.GONE);
            binding.ivCover.setVisibility(View.VISIBLE);
            Glide.with(this).load(fullCover).centerCrop().into(binding.ivCover);
        }

        adapter = new TrackAdapter(this, (track, position, tracks) -> {
            MusicPlayerManager.getInstance(this).setQueue(tracks, position);
        }, (track, anchor) -> showTrackOptions(track, anchor));

        binding.rvTracks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTracks.setAdapter(adapter);

        binding.btnPlayAll.setOnClickListener(v -> {
            List<Track> currentTracks = adapter.getTracks();
            if (!currentTracks.isEmpty()) {
                MusicPlayerManager.getInstance(this).setQueue(currentTracks, 0);
            } else {
                Toast.makeText(this, "No tracks in playlist to play", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnAddSongs.setOnClickListener(v -> showAddSongsCatalogDialog());

        binding.btnPlaylistOptions.setOnClickListener(this::showPlaylistOptionsMenu);

        binding.swipeRefresh.setOnRefreshListener(this::loadPlaylistDetails);

        MusicPlayerManager.getInstance(this).addListener(this);

        loadPlaylistDetails();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicPlayerManager.getInstance(this).removeListener(this);
    }

    private void loadPlaylistDetails() {
        binding.swipeRefresh.setRefreshing(true);
        ApiClient.getInstance(this).getPlaylistDetails(playlist.getId(), new ApiClient.ApiCallback<Playlist>() {
            @Override
            public void onSuccess(Playlist result) {
                binding.swipeRefresh.setRefreshing(false);
                if (result != null) {
                    playlist = result;
                    binding.tvMeta.setText("By " + playlist.getOwnerName() + " • " + playlist.getTrackCount() + " tracks");
                    List<Track> tracks = result.getTracks();
                    if (tracks != null && !tracks.isEmpty()) {
                        adapter.setTracks(tracks);
                        binding.tvEmptyState.setVisibility(View.GONE);
                    } else {
                        adapter.setTracks(null);
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onError(String error) {
                binding.swipeRefresh.setRefreshing(false);
                Toast.makeText(PlaylistDetailActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPlaylistOptionsMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Edit Name & Details");
        popup.getMenu().add(0, 2, 1, "Delete Playlist");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showEditPlaylistDialog();
                return true;
            } else if (item.getItemId() == 2) {
                confirmDeletePlaylist();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEditPlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_playlist, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etTitle = dialogView.findViewById(R.id.etPlaylistTitle);
        EditText etDesc = dialogView.findViewById(R.id.etPlaylistDesc);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        androidx.appcompat.widget.AppCompatButton btnSave = dialogView.findViewById(R.id.btnCreate);
        btnSave.setText("Save Changes");

        etTitle.setText(playlist.getTitle());
        etDesc.setText(playlist.getDescription());

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newTitle = etTitle.getText().toString().trim();
            String newDesc = etDesc.getText().toString().trim();
            if (newTitle.isEmpty()) {
                etTitle.setError("Playlist title required");
                return;
            }

            ApiClient.getInstance(this).updatePlaylist(playlist.getId(), newTitle, newDesc, new ApiClient.ApiCallback<Playlist>() {
                @Override
                public void onSuccess(Playlist updated) {
                    Toast.makeText(PlaylistDetailActivity.this, "Playlist updated!", Toast.LENGTH_SHORT).show();
                    playlist.setTitle(newTitle);
                    playlist.setDescription(newDesc);
                    binding.tvHeaderTitle.setText(newTitle);
                    binding.tvTitle.setText(newTitle);
                    binding.tvDescription.setText(newDesc.isEmpty() ? "No description provided." : newDesc);
                    dialog.dismiss();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(PlaylistDetailActivity.this, "Update failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        dialog.show();
    }

    private void confirmDeletePlaylist() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to permanently delete \"" + playlist.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.getInstance(this).deletePlaylist(playlist.getId(), new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(PlaylistDetailActivity.this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(PlaylistDetailActivity.this, "Error deleting: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTrackOptions(Track track, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, track.isLiked() ? "Unlike Track" : "Like Track");
        popup.getMenu().add(0, 2, 1, "Remove from Playlist");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                ApiClient.getInstance(this).toggleLike(track.getId(), new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isLiked) {
                        track.setLiked(isLiked);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(PlaylistDetailActivity.this, isLiked ? "Saved to Liked Songs" : "Removed from Liked Songs", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(PlaylistDetailActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            } else if (item.getItemId() == 2) {
                ApiClient.getInstance(this).removeTrackFromPlaylist(playlist.getId(), track.getId(), new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        Toast.makeText(PlaylistDetailActivity.this, "Track removed from playlist", Toast.LENGTH_SHORT).show();
                        loadPlaylistDetails();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(PlaylistDetailActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showAddSongsCatalogDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_to_playlist, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTarget = dialogView.findViewById(R.id.tvTrackTarget);
        tvTarget.setText("Select a song to add to " + playlist.getTitle());

        ProgressBar pb = dialogView.findViewById(R.id.progressBar);
        TextView tvNoTracks = dialogView.findViewById(R.id.tvNoPlaylists);
        RecyclerView rvPicker = dialogView.findViewById(R.id.rvPlaylistsPicker);
        View btnClose = dialogView.findViewById(R.id.btnClose);

        rvPicker.setLayoutManager(new LinearLayoutManager(this));
        pb.setVisibility(View.VISIBLE);

        ApiClient.getInstance(this).getTracks(null, "all", new ApiClient.ApiCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                pb.setVisibility(View.GONE);
                if (tracks != null && !tracks.isEmpty()) {
                    TrackAdapter pickerAdapter = new TrackAdapter(PlaylistDetailActivity.this, (track, position, list) -> {
                        ApiClient.getInstance(PlaylistDetailActivity.this).addTrackToPlaylist(playlist.getId(), track.getId(), new ApiClient.ApiCallback<String>() {
                            @Override
                            public void onSuccess(String result) {
                                Toast.makeText(PlaylistDetailActivity.this, "Added \"" + track.getTitle() + "\" to playlist", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                                loadPlaylistDetails();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(PlaylistDetailActivity.this, error, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }, null);
                    pickerAdapter.setTracks(tracks);
                    rvPicker.setAdapter(pickerAdapter);
                } else {
                    tvNoTracks.setText("No songs available to add.");
                    tvNoTracks.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                pb.setVisibility(View.GONE);
                tvNoTracks.setText("Failed to load catalog: " + error);
                tvNoTracks.setVisibility(View.VISIBLE);
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public void onTrackChanged(Track track) {
        if (adapter != null && track != null) {
            adapter.setPlayingTrack(track);
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {}

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {}

    @Override
    public void onError(String message) {}
}
