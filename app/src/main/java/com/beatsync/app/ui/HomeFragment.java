package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.FragmentHomeBinding;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.models.User;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import com.beatsync.app.ui.adapters.TrackAdapter;
import java.util.List;

public class HomeFragment extends Fragment implements MusicPlayerManager.PlaybackListener {

    private FragmentHomeBinding binding;
    private TrackAdapter trackAdapter;
    private Track featuredTrack = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        User user = AuthManager.getInstance(requireContext()).getUser();
        if (user != null) {
            binding.tvGreeting.setText("Welcome, " + user.getName());
        }

        trackAdapter = new TrackAdapter(requireContext(), (track, position, tracks) -> {
            MusicPlayerManager.getInstance(requireContext()).setQueue(tracks, position);
        }, (track, anchor) -> showTrackOptions(track, anchor));

        binding.rvTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTracks.setAdapter(trackAdapter);

        binding.swipeRefresh.setOnRefreshListener(this::loadTracks);

        binding.cardFeatured.setOnClickListener(v -> {
            if (featuredTrack != null) {
                MusicPlayerManager.getInstance(requireContext()).playTrack(featuredTrack);
            }
        });

        binding.btnQuickProfile.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_library);
            }
        });

        MusicPlayerManager.getInstance(requireContext()).addListener(this);
        loadTracks();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MusicPlayerManager.getInstance(requireContext()).removeListener(this);
        binding = null;
    }

    private void loadTracks() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        ApiClient.getInstance(requireContext()).getTracks(null, "public", new ApiClient.ApiCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> result) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);

                if (result != null && !result.isEmpty()) {
                    trackAdapter.setTracks(result);
                    binding.tvTrackCount.setText(result.size() + " tracks");
                    binding.tvEmpty.setVisibility(View.GONE);

                    // Pick first track as featured
                    featuredTrack = result.get(0);
                    binding.tvFeaturedTitle.setText(featuredTrack.getTitle());
                    binding.tvFeaturedArtist.setText(featuredTrack.getArtist() + " • " + featuredTrack.getAlbum());
                } else {
                    trackAdapter.setTracks(null);
                    binding.tvTrackCount.setText("0 tracks");
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                binding.tvEmpty.setText("Error loading streams:\n" + error);
                binding.tvEmpty.setVisibility(View.VISIBLE);
            }
        });
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
                    PlaylistAdapter adapter = new PlaylistAdapter(requireContext(), p -> {
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
