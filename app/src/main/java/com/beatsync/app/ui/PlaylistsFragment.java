package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.databinding.FragmentPlaylistsBinding;
import com.beatsync.app.models.Playlist;
import com.beatsync.app.ui.adapters.PlaylistAdapter;
import java.util.List;

public class PlaylistsFragment extends Fragment {

    private FragmentPlaylistsBinding binding;
    private PlaylistAdapter playlistAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playlistAdapter = new PlaylistAdapter(requireContext(), playlist -> {
            Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
            intent.putExtra("playlist", playlist);
            startActivity(intent);
        });

        binding.rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvPlaylists.setAdapter(playlistAdapter);

        binding.btnCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        binding.swipeRefresh.setOnRefreshListener(this::loadPlaylists);

        loadPlaylists();
        com.beatsync.app.ui.utils.AnimationHelper.animateRubberEntrance(view);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            com.beatsync.app.ui.utils.AnimationHelper.animateRubberEntrance(getView());
            if (binding != null && binding.rvPlaylists != null) {
                com.beatsync.app.ui.utils.AnimationHelper.animateListItems(binding.rvPlaylists);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPlaylists();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadPlaylists() {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);

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
                    binding.tvEmptyState.setText("No playlists found.\nTap 'Create New Playlist' to get started.");
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
}
