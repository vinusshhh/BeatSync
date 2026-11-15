package com.beatsync.app.ui;

import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.FragmentUploadBinding;
import com.beatsync.app.models.Track;
import com.beatsync.app.models.User;
import com.beatsync.app.ui.utils.AnimationHelper;
import com.bumptech.glide.Glide;

public class UploadFragment extends Fragment {

    private FragmentUploadBinding binding;
    private Uri selectedAudioUri = null;
    private Uri selectedCoverUri = null;

    private final ActivityResultLauncher<String> audioPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedAudioUri = uri;
                    String fileName = getFileName(uri);
                    binding.tvAudioFileName.setText(fileName != null ? fileName : "audio_track.mp3");
                    binding.tvAudioFileName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));

                    // Auto-fill track title and artist intelligently
                    if (fileName != null) {
                        String cleanName = fileName.replaceAll("\\.[^.]+$", "");
                        String stripped = cleanName.replaceAll("(?i)[\\[\\(].*?[\\]\\)]", " ").trim();
                        String[] parts = stripped.split("\\s*[-|•:]\\s*");
                        if (parts.length >= 2) {
                            String songName = parts[0].replaceAll("(?i)\\b(audio song|video song|full song|official video|official audio|lyrics|remix|hd|4k)\\b", "").replaceAll("\\s+", " ").trim();
                            String artistName = parts[1].replaceAll("(?i)\\b(audio song|video song|full song|official video|official audio|lyrics|remix|hd|4k)\\b", "").replaceAll("\\s+", " ").trim();
                            if (!songName.isEmpty()) binding.etTrackTitle.setText(songName);
                            else binding.etTrackTitle.setText(parts[0].trim());
                            if (!artistName.isEmpty()) binding.etArtistName.setText(artistName);
                        } else {
                            String songName = stripped.replaceAll("(?i)\\b(audio song|video song|full song|official video|official audio|lyrics|remix|hd|4k)\\b", "").replaceAll("\\s+", " ").trim();
                            binding.etTrackTitle.setText(!songName.isEmpty() ? songName : cleanName);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> coverPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedCoverUri = uri;
                    String fileName = getFileName(uri);
                    binding.tvCoverFileName.setText(fileName != null ? fileName : "cover.jpg");
                    binding.tvCoverFileName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                    Glide.with(requireContext()).load(uri).centerCrop().into(binding.ivCoverPreview);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUploadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        User currentUser = AuthManager.getInstance(requireContext()).getUser();
        if (currentUser != null && binding.etArtistName.getText().toString().isEmpty()) {
            binding.etArtistName.setText(currentUser.getName());
        }

        binding.switchPrivacy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int tabIndex = isChecked ? 0 : 1;
            binding.uploadPrivacyTabGroup.selectTab(tabIndex, true, false);

            if (isChecked) {
                // Public mode
                binding.btnPrivacyPublic.setTextColor(Color.WHITE);
                binding.btnPrivacyPrivate.setTextColor(Color.parseColor("#888888"));
                binding.tvPrivacyModeTitle.setText("Public Release");
                binding.tvPrivacyModeDesc.setText(R.string.visibility_public_desc);
                binding.ivPrivacyIcon.setImageResource(R.drawable.ic_globe);
            } else {
                // Private mode
                binding.btnPrivacyPrivate.setTextColor(Color.WHITE);
                binding.btnPrivacyPublic.setTextColor(Color.parseColor("#888888"));
                binding.tvPrivacyModeTitle.setText("Private Vault Track");
                binding.tvPrivacyModeDesc.setText(R.string.visibility_private_desc);
                binding.ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
            }
        });

        binding.btnPrivacyPublic.setOnClickListener(v -> binding.switchPrivacy.setChecked(true));
        binding.btnPrivacyPrivate.setOnClickListener(v -> binding.switchPrivacy.setChecked(false));

        binding.uploadPrivacyTabGroup.setOnTabSelectedListener((pos, tab) -> {
            binding.switchPrivacy.setChecked(pos == 0);
        });

        binding.btnPickAudio.setOnClickListener(v -> audioPickerLauncher.launch("audio/*"));
        binding.btnPickCover.setOnClickListener(v -> coverPickerLauncher.launch("image/*"));

        binding.btnSubmitUpload.setOnClickListener(v -> startUpload());
        AnimationHelper.animateRubberEntrance(view);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) {
            AnimationHelper.animateRubberEntrance(getView());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void startUpload() {
        String title = binding.etTrackTitle.getText().toString().trim();
        String artist = binding.etArtistName.getText().toString().trim();
        boolean isPublic = binding.switchPrivacy.isChecked();

        if (title.isEmpty()) {
            binding.etTrackTitle.setError("Title is required");
            binding.etTrackTitle.requestFocus();
            return;
        }

        if (artist.isEmpty()) {
            binding.etArtistName.setError("Artist name is required");
            binding.etArtistName.requestFocus();
            return;
        }

        if (selectedAudioUri == null) {
            Toast.makeText(requireContext(), "Please select an audio file to upload", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.uploadProgress.setVisibility(View.VISIBLE);
        binding.btnSubmitUpload.setEnabled(false);
        binding.tvStatus.setVisibility(View.GONE);

        ApiClient.getInstance(requireContext()).uploadTrack(
                title,
                artist,
                "Single",
                isPublic,
                selectedAudioUri,
                selectedCoverUri,
                new ApiClient.ApiCallback<Track>() {
                    @Override
                    public void onSuccess(Track track) {
                        if (binding == null) return;
                        binding.uploadProgress.setVisibility(View.GONE);
                        binding.btnSubmitUpload.setEnabled(true);

                        String msg = isPublic ?
                                "Track published publicly to BeatSync catalog!" :
                                "Track saved privately to your personal vault!";
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();

                        // Reset form
                        binding.etTrackTitle.setText("");
                        selectedAudioUri = null;
                        selectedCoverUri = null;
                        binding.tvAudioFileName.setText("Select Audio File from Device");
                        binding.tvAudioFileName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                        binding.tvCoverFileName.setText("Select Cover Artwork (JPG, PNG)");
                        binding.tvCoverFileName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                        binding.ivCoverPreview.setImageResource(R.drawable.ic_library);

                        // Switch to Library tab to see the track
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).selectTab(R.id.nav_library);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (binding == null) return;
                        binding.uploadProgress.setVisibility(View.GONE);
                        binding.btnSubmitUpload.setEnabled(true);
                        binding.tvStatus.setText("Upload failed: " + error);
                        binding.tvStatus.setVisibility(View.VISIBLE);
                    }
                }
        );
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
