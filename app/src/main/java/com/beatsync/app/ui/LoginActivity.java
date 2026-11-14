package com.beatsync.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.beatsync.app.R;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityLoginBinding;
import com.beatsync.app.models.AuthResponse;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        updateServerInfoText();

        binding.btnLogin.setOnClickListener(v -> performLogin());

        binding.tvGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        binding.btnServerSettings.setOnClickListener(v -> showServerSettingsDialog());
    }

    private void updateServerInfoText() {
        String url = AuthManager.getInstance(this).getBaseUrl();
        binding.tvServerInfo.setText("Connected to: " + url);
    }

    private void performLogin() {
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            binding.etEmail.setError("Email required");
            binding.etEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.etPassword.setError("Password required");
            binding.etPassword.requestFocus();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnLogin.setEnabled(false);
        binding.tvError.setVisibility(View.GONE);

        ApiClient.getInstance(this).login(email, password, new ApiClient.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);

                if (result != null && result.getToken() != null && result.getUser() != null) {
                    AuthManager.getInstance(LoginActivity.this).saveAuth(result.getToken(), result.getUser());
                    Toast.makeText(LoginActivity.this, "Welcome " + result.getUser().getName(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } else {
                    binding.tvError.setText("Invalid response from server");
                    binding.tvError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnLogin.setEnabled(true);
                binding.tvError.setText(error);
                binding.tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showServerSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_server_settings, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etUrl = dialogView.findViewById(R.id.etServerUrl);
        TextView btnEmulator = dialogView.findViewById(R.id.btnPresetEmulator);
        TextView btnLocalhost = dialogView.findViewById(R.id.btnPresetLocalhost);
        View btnCancel = dialogView.findViewById(R.id.btnCancelServer);
        View btnSave = dialogView.findViewById(R.id.btnSaveServer);

        etUrl.setText(AuthManager.getInstance(this).getBaseUrl());

        btnEmulator.setOnClickListener(v -> etUrl.setText("http://10.0.2.2:8000"));
        btnLocalhost.setOnClickListener(v -> etUrl.setText("http://localhost:8000"));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String newUrl = etUrl.getText().toString().trim();
            if (!newUrl.isEmpty()) {
                AuthManager.getInstance(this).setBaseUrl(newUrl);
                updateServerInfoText();
                Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }
}
