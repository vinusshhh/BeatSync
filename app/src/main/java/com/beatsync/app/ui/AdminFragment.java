package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.databinding.FragmentAdminBinding;
import com.beatsync.app.models.Track;
import com.beatsync.app.models.User;
import com.beatsync.app.player.MusicPlayerManager;
import com.beatsync.app.ui.adapters.AdminTrackAdapter;
import com.beatsync.app.ui.adapters.AdminUserAdapter;
import com.beatsync.app.ui.utils.AnimationHelper;
import java.util.List;

public class AdminFragment extends Fragment {

    private FragmentAdminBinding binding;
    private AdminUserAdapter userAdapter;
    private AdminTrackAdapter trackAdapter;
    private boolean isUsersTab = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAdminBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupAdapters();
        setupTabs();

        binding.swipeRefresh.setOnRefreshListener(this::loadData);

        loadData();
    }

    private void setupAdapters() {
        userAdapter = new AdminUserAdapter(new AdminUserAdapter.OnUserActionListener() {
            @Override
            public void onDeleteUser(User user) {
                confirmDeleteUser(user);
            }

            @Override
            public void onEditPassword(User user) {
                showAdminEditPasswordDialog(user);
            }
        });
        binding.rvAdminUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAdminUsers.setAdapter(userAdapter);

        trackAdapter = new AdminTrackAdapter(requireContext(), new AdminTrackAdapter.OnAdminTrackActionListener() {
            @Override
            public void onPlayTrack(Track track, List<Track> list, int position) {
                MusicPlayerManager.getInstance(requireContext()).setQueue(list, position);
                Toast.makeText(requireContext(), "Playing " + track.getTitle(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteTrack(Track track) {
                confirmDeleteTrack(track);
            }
        });
        binding.rvAdminTracks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvAdminTracks.setAdapter(trackAdapter);
    }

    private void setupTabs() {
        AnimationHelper.addPressScaleEffect(binding.btnTabUsers);
        AnimationHelper.addPressScaleEffect(binding.btnTabTracks);

        binding.btnTabUsers.setOnClickListener(v -> {
            if (!isUsersTab) {
                isUsersTab = true;
                updateTabVisuals();
            }
        });

        binding.btnTabTracks.setOnClickListener(v -> {
            if (isUsersTab) {
                isUsersTab = false;
                updateTabVisuals();
            }
        });
    }

    private void updateTabVisuals() {
        if (isUsersTab) {
            binding.btnTabUsers.setBackgroundResource(R.drawable.bg_card_highlight);
            binding.btnTabUsers.setTextColor(Color.WHITE);
            binding.btnTabTracks.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTabTracks.setTextColor(Color.parseColor("#888888"));

            binding.rvAdminTracks.setVisibility(View.GONE);
            binding.rvAdminUsers.setVisibility(View.VISIBLE);
        } else {
            binding.btnTabTracks.setBackgroundResource(R.drawable.bg_card_highlight);
            binding.btnTabTracks.setTextColor(Color.WHITE);
            binding.btnTabUsers.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTabUsers.setTextColor(Color.parseColor("#888888"));

            binding.rvAdminUsers.setVisibility(View.GONE);
            binding.rvAdminTracks.setVisibility(View.VISIBLE);
        }
    }

    private void loadData() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);

        // Fetch Users
        ApiClient.getInstance(requireContext()).adminGetUsers(new ApiClient.ApiCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                if (!isAdded()) return;
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                userAdapter.setUsers(users);
                binding.tvUserCountBadge.setText("Users: " + (users != null ? users.size() : 0));
            }

            @Override
            public void onError(String error) {
                if (!isAdded()) return;
                binding.swipeRefresh.setRefreshing(false);
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Failed to load users: " + error, Toast.LENGTH_SHORT).show();
            }
        });

        // Fetch Tracks
        ApiClient.getInstance(requireContext()).adminGetAllTracks(new ApiClient.ApiCallback<List<Track>>() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (!isAdded()) return;
                trackAdapter.setTracks(tracks);
                binding.tvTrackCountBadge.setText("Tracks: " + (tracks != null ? tracks.size() : 0));
            }

            @Override
            public void onError(String error) {
                // Handled gracefully
            }
        });
    }

    private void showAdminEditPasswordDialog(User user) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Enter new password for " + user.getName());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setBackgroundResource(R.drawable.bg_edit_text);
        input.setPadding(36, 24, 36, 24);

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 48;
        params.rightMargin = 48;
        params.topMargin = 24;
        params.bottomMargin = 24;
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle("Admin Set Password")
                .setMessage("Set a new password for user \"" + user.getEmail() + "\":")
                .setView(container)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPwd = input.getText().toString().trim();
                    if (newPwd.length() < 4) {
                        Toast.makeText(requireContext(), "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ApiClient.getInstance(requireContext()).adminSetUserPassword(user.getId(), newPwd, new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            user.setRawPassword(newPwd);
                            userAdapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), "Password updated for " + user.getEmail(), Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteUser(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete User Account")
                .setMessage("Are you sure you want to permanently delete user \"" + user.getEmail() + "\"?\n\nAll music uploaded by this user and their data will be removed.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.getInstance(requireContext()).adminDeleteUser(user.getId(), new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(requireContext(), "User deleted", Toast.LENGTH_SHORT).show();
                            loadData();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteTrack(Track track) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Track")
                .setMessage("Are you sure you want to delete track \"" + track.getTitle() + "\" by " + track.getArtist() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    ApiClient.getInstance(requireContext()).adminDeleteTrack(track.getId(), new ApiClient.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(requireContext(), "Track deleted", Toast.LENGTH_SHORT).show();
                            loadData();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(requireContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
