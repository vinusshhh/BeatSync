package com.beatsync.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.beatsync.app.api.ApiClient;
import com.beatsync.app.auth.AuthManager;
import com.beatsync.app.databinding.ActivityRegisterBinding;
import com.beatsync.app.models.AuthResponse;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnRegister.setOnClickListener(v -> performRegister());

        binding.tvGoToLogin.setOnClickListener(v -> finish());
    }

    private void performRegister() {
        String name = binding.etName.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (name.isEmpty()) {
            binding.etName.setError("Name required");
            binding.etName.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            binding.etEmail.setError("Email required");
            binding.etEmail.requestFocus();
            return;
        }

        if (password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnRegister.setEnabled(false);
        binding.tvError.setVisibility(View.GONE);

        ApiClient.getInstance(this).register(email, password, name, new ApiClient.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnRegister.setEnabled(true);

                if (result != null && result.getToken() != null && result.getUser() != null) {
                    AuthManager.getInstance(RegisterActivity.this).saveAuth(result.getToken(), result.getUser());
                    Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    binding.tvError.setText("Registration error");
                    binding.tvError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String error) {
                binding.progressBar.setVisibility(View.GONE);
                binding.btnRegister.setEnabled(true);
                binding.tvError.setText(error);
                binding.tvError.setVisibility(View.VISIBLE);
            }
        });
    }
}
