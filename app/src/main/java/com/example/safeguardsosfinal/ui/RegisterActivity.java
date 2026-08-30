package com.example.safeguardsosfinal.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeguardsosfinal.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        binding.btnRegister.setOnClickListener(v -> performRegistration());

        binding.tvGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void performRegistration() {
        String name = binding.etRegName.getText() != null ? binding.etRegName.getText().toString().trim() : "";
        String email = binding.etRegEmail.getText() != null ? binding.etRegEmail.getText().toString().trim() : "";
        String phone = binding.etRegPhone.getText() != null ? binding.etRegPhone.getText().toString().trim() : "";
        String blood = binding.etRegBloodGroup.getText() != null ? binding.etRegBloodGroup.getText().toString().trim().toUpperCase() : "";
        String password = binding.etRegPassword.getText() != null ? binding.etRegPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            binding.etRegName.setError("Name is required");
            binding.etRegName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            binding.etRegEmail.setError("Email is required");
            binding.etRegEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            binding.etRegPhone.setError("Phone number is required");
            binding.etRegPhone.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.etRegPassword.setError("Password must be at least 6 characters");
            binding.etRegPassword.requestFocus();
            return;
        }

        setLoading(true);

        // ১. ফায়ারবেস অথেন্টিকেশন দিয়ে একাউন্ট তৈরি
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && auth.getCurrentUser() != null) {
                        FirebaseUser user = auth.getCurrentUser();
                        String uid = user.getUid();

                        // প্রোফাইল ডিসপ্লে নাম আপডেট
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build();
                        user.updateProfile(profileUpdates);

                        // ২. লোকাল মেমোরিতে ব্যবহারকারীর নম্বর সংরক্ষণ (যাতে গার্ডিয়ান অ্যালার্টে চেনে)
                        SharedPreferences sp = getSharedPreferences("SafeGuardUser", MODE_PRIVATE);
                        sp.edit()
                                .putString("user_name", name)
                                .putString("user_phone", phone)
                                .putString("user_blood", blood)
                                .apply();

                        // ৩. ফায়ারস্টোর ডেটাবেজে সম্পূর্ণ প্রোফাইল ডেটা পুশ করা
                        Map<String, Object> userData = new HashMap<>();
                        userData.put("uid", uid);
                        userData.put("name", name);
                        userData.put("email", email);
                        userData.put("phone", phone);
                        userData.put("bloodGroup", blood.isEmpty() ? "N/A" : blood);
                        userData.put("joinedAt", System.currentTimeMillis());

                        firestore.collection("users").document(uid)
                                .set(userData)
                                .addOnSuccessListener(aVoid -> {
                                    setLoading(false);
                                    Toast.makeText(RegisterActivity.this, "Account Registered Successfully!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    setLoading(false);
                                    Toast.makeText(RegisterActivity.this, "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    // ডাটাবেজ ফেইল হলেও সরাসরি মেইন স্ক্রিনে ঢুকিয়ে দেওয়া
                                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });

                    } else {
                        setLoading(false);
                        String error = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                        Toast.makeText(RegisterActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            binding.progressBarReg.setVisibility(View.VISIBLE);
            binding.btnRegister.setVisibility(View.INVISIBLE);
            binding.btnRegister.setEnabled(false);
        } else {
            binding.progressBarReg.setVisibility(View.GONE);
            binding.btnRegister.setVisibility(View.VISIBLE);
            binding.btnRegister.setEnabled(true);
        }
    }
}