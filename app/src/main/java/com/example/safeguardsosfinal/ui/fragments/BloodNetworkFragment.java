package com.example.safeguardsosfinal.ui.fragments;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.adapters.BloodRequestAdapter;
import com.example.safeguardsosfinal.databinding.FragmentBloodNetworkBinding;
import com.example.safeguardsosfinal.models.BloodRequest;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.UUID;

public class BloodNetworkFragment extends Fragment {

    private FragmentBloodNetworkBinding binding;
    private BloodRequestAdapter adapter;
    private ListenerRegistration bloodListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentBloodNetworkBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new BloodRequestAdapter();
        binding.rvBloodRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvBloodRequests.setAdapter(adapter);

        listenToBloodRequests();

        binding.btnPostBloodRequest.setOnClickListener(v -> showAddBloodDialog());
    }

    private void listenToBloodRequests() {
        if (binding.progressBarBlood != null) {
            binding.progressBarBlood.setVisibility(View.VISIBLE);
        }

        bloodListener = FirebaseManager.getInstance().listenToBloodRequests(list -> {
            if (binding != null && binding.progressBarBlood != null) {
                binding.progressBarBlood.setVisibility(View.GONE);
            }
            if (list != null) {
                adapter.updateList(list);
            }
        });
    }

    private void showAddBloodDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_blood_request, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialogView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        TextInputLayout tilName = dialogView.findViewById(R.id.tilPatientName);
        TextInputLayout tilGroup = dialogView.findViewById(R.id.tilBloodGroup);
        TextInputLayout tilLoc = dialogView.findViewById(R.id.tilHospitalLocation);
        TextInputLayout tilPhone = dialogView.findViewById(R.id.tilContactPhone);
        TextInputLayout tilNote = dialogView.findViewById(R.id.tilBloodNote);

        EditText etName = dialogView.findViewById(R.id.etDialogPatientName);
        EditText etGroup = dialogView.findViewById(R.id.etDialogBloodGroup);
        EditText etLoc = dialogView.findViewById(R.id.etDialogHospitalLocation);
        EditText etPhone = dialogView.findViewById(R.id.etDialogContactPhone);
        EditText etNote = dialogView.findViewById(R.id.etDialogBloodNote);
        ProgressBar progressBar = dialogView.findViewById(R.id.dialogBloodProgressBar);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("🚨 Request Blood Donor")
                .setView(dialogView)
                .setPositiveButton("Post Request", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton button = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                if (tilName != null) tilName.setError(null);
                if (tilGroup != null) tilGroup.setError(null);
                if (tilLoc != null) tilLoc.setError(null);
                if (tilPhone != null) tilPhone.setError(null);

                String name = (etName != null && etName.getText() != null) ? etName.getText().toString().trim() : "";
                String group = (etGroup != null && etGroup.getText() != null) ? etGroup.getText().toString().trim().toUpperCase() : "";
                String loc = (etLoc != null && etLoc.getText() != null) ? etLoc.getText().toString().trim() : "";
                String phone = (etPhone != null && etPhone.getText() != null) ? etPhone.getText().toString().trim() : "";
                String note = (etNote != null && etNote.getText() != null) ? etNote.getText().toString().trim() : "Urgent";

                boolean hasError = false;

                String bloodRegex = "^(A|B|AB|O)[+-]$";
                String phoneRegex = "^(\\+?[0-9]{11,14})$";

                if (name.length() < 3) {
                    if (tilName != null) tilName.setError("Enter patient full name (min 3 chars)");
                    hasError = true;
                }
                if (!group.matches(bloodRegex)) {
                    if (tilGroup != null) tilGroup.setError("Valid blood format: A+, B+, AB+, O+, A-, B-, AB-, O-");
                    hasError = true;
                }
                if (loc.length() < 3) {
                    if (tilLoc != null) tilLoc.setError("Hospital name and location are mandatory");
                    hasError = true;
                }
                if (!phone.matches(phoneRegex)) {
                    if (tilPhone != null) tilPhone.setError("Enter valid phone number (11-14 digits only)");
                    hasError = true;
                }

                if (hasError) return;

                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                button.setEnabled(false);

                submitBloodRequest(name, group, loc, phone, note, dialog, progressBar, button);
            });
        });

        dialog.show();
    }

    private void submitBloodRequest(String name, String group, String loc, String phone, String note,
                                    AlertDialog dialog, ProgressBar progressBar, MaterialButton button) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String currentUid = (user != null && user.getUid() != null) ? user.getUid() : "anonymous_user";
            String requestId = "blood_" + UUID.randomUUID().toString().substring(0, 8);

            BloodRequest request = new BloodRequest(
                    requestId,
                    currentUid,
                    name,
                    group,
                    loc,
                    phone,
                    note,
                    23.8103,
                    90.4125,
                    System.currentTimeMillis(),
                    "URGENT"
            );

            FirebaseManager.getInstance().postBloodRequest(request, new FirebaseManager.OnCompleteListener() {
                @Override
                public void onSuccess() {
                    if (isAdded() && getContext() != null) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(getContext(), "Blood request posted successfully!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (isAdded() && getContext() != null) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (button != null) button.setEnabled(true);
                        String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "Failed to post";
                        Toast.makeText(getContext(), "Error: " + msg, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception ex) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            if (button != null) button.setEnabled(true);
            Toast.makeText(getContext(), "Something went wrong: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bloodListener != null) {
            bloodListener.remove();
        }
        binding = null;
    }
}