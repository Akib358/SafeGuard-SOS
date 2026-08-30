package com.example.safeguardsosfinal.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeguardsosfinal.databinding.ActivityRegisterBinding;
import com.example.safeguardsosfinal.models.User;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnRegister.setOnClickListener(v -> performRegistration());
        binding.tvLoginPrompt.setOnClickListener(v -> finish());
    }

    private void performRegistration() {
        String name = binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
        String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
        String phone = binding.etPhone.getText() != null ? binding.etPhone.getText().toString().trim() : "";
        String profession = binding.etProfession.getText() != null ? binding.etProfession.getText().toString().trim() : "";
        String address = binding.etAddress.getText() != null ? binding.etAddress.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(phone) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all mandatory fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBarRegister.setVisibility(View.VISIBLE);
        binding.btnRegister.setEnabled(false);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = auth.getCurrentUser();
                        String uid = (firebaseUser != null) ? firebaseUser.getUid() : "";

                        User user = new User(uid, name, email, phone, profession, address);

                        FirebaseManager.getInstance().saveUser(user, new FirebaseManager.OnCompleteListener() {
                            @Override
                            public void onSuccess() {
                                binding.progressBarRegister.setVisibility(View.GONE);
                                Toast.makeText(RegisterActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                                navigateDirectlyToDashboard();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                binding.progressBarRegister.setVisibility(View.GONE);
                                navigateDirectlyToDashboard();
                            }
                        });
                    } else {
                        binding.progressBarRegister.setVisibility(View.GONE);
                        binding.btnRegister.setEnabled(true);
                        String msg = (task.getException() != null) ? task.getException().getMessage() : "Registration failed";
                        Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateDirectlyToDashboard() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}