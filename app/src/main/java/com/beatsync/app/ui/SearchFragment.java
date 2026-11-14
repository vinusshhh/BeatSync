package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.beatsync.app.databinding.FragmentSearchBinding;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.models.Track;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import com.beatsync.app.ui.adapters.TrackAdapter;
import java.util.List;

public class SearchFragment extends Fragment implements MusicPlayerManager.PlaybackListener {

    private FragmentSearchBinding binding;
    private TrackAdapter adapter;
    private String currentFilter = "all"; // "all", "public", "private"

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new TrackAdapter(requireContext(), (track, position, tracks) -> {
            MusicPlayerManager.getInstance(requireContext()).setQueue(tracks, position);
        }, (track, anchor) -> showTrackOptions(track, anchor));

        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSearchResults.setAdapter(adapter);

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnClearSearch.setOnClickListener(v -> binding.etSearch.setText(""));

        // Chips
        binding.chipAll.setOnClickListener(v -> selectFilter("all"));
        binding.chipPublic.setOnClickListener(v -> selectFilter("public"));
        binding.chipPrivate.setOnClickListener(v -> selectFilter("private"));

        MusicPlayerManager.getInstance(requireContext()).addListener(this);

        // Initial search to display list
        performSearch("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MusicPlayerManager.getInstance(requireContext()).removeListener(this);
        binding = null;
    }

    private void selectFilter(String filter) {
        currentFilter = filter;
        updateChipStyles();
        performSearch(binding.etSearch.getText().toString());
    }

    private void updateChipStyles() {
        int activeBg = R.drawable.bg_button_primary;
        int activeText = ContextCompat.getColor(requireContext(), R.color.text_inverse);
        int inactiveBg = R.drawable.bg_card_minimal;
        int inactiveText = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        binding.chipAll.setBackgroundResource(currentFilter.equals("all") ? activeBg : inactiveBg);
        binding.chipAll.setTextColor(currentFilter.equals("all") ? activeText : inactiveText);

        binding.chipPublic.setBackgroundResource(currentFilter.equals("public") ? activeBg : inactiveBg);
        binding.chipPublic.setTextColor(currentFilter.equals("public") ? activeText : inactiveText);

        binding.chipPrivate.setBackgroundResource(currentFilter.equals("private") ? activeBg : inactiveBg);
        binding.chipPrivate.setTextColor(currentFilter.equals("private") ? activeText : inactiveText);
    }

    private void performSearch(String query) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);

        ApiClient.getInstance(requireContext()).getTracks(query, currentFilter, new ApiClient.ApiCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> result) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);

                if (result != null && !result.isEmpty()) {
                    adapter.setTracks(result);
                    binding.tvEmptyState.setVisibility(View.GONE);
                } else {
                    adapter.setTracks(null);
                    binding.tvEmptyState.setText("No matching tracks found.");
                    binding.tvEmptyState.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                if (binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                binding.tvEmptyState.setText("Search error: " + error);
                binding.tvEmptyState.setVisibility(View.VISIBLE);
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
                        adapter.notifyDataSetChanged();
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
        if (adapter != null && track != null) {
            adapter.setPlayingTrackId(track.getId());
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {}

    @Override
    public void onProgressUpdate(int currentMs, int totalMs) {}

    @Override
    public void onError(String message) {}
}
