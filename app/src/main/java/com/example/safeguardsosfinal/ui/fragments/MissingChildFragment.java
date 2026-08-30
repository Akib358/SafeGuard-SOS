package com.example.safeguardsosfinal.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.adapters.MissingChildAdapter;
import com.example.safeguardsosfinal.databinding.FragmentMissingChildBinding;
import com.example.safeguardsosfinal.models.MissingChild;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MissingChildFragment extends Fragment {

    private static final String CLOUDINARY_CLOUD_NAME = "hxpahnld";
    private static final String CLOUDINARY_UPLOAD_PRESET = "SafeGuard_SOS";

    private FragmentMissingChildBinding binding;
    private MissingChildAdapter adapter;
    private ListenerRegistration childListener;
    private Uri selectedImageUri = null;
    private ImageView ivPreviewRef = null;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (ivPreviewRef != null && selectedImageUri != null) {
                        ivPreviewRef.setImageURI(selectedImageUri);
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMissingChildBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new MissingChildAdapter();
        binding.rvMissingChildren.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvMissingChildren.setAdapter(adapter);

        listenToMissingReports();
        binding.btnPostMissingReport.setOnClickListener(v -> showReportDialog());
    }

    private void listenToMissingReports() {
        if (binding != null && binding.progressBarMissing != null) {
            binding.progressBarMissing.setVisibility(View.VISIBLE);
        }

        childListener = FirebaseManager.getInstance().listenToMissingChildren(list -> {
            if (binding != null && binding.progressBarMissing != null) {
                binding.progressBarMissing.setVisibility(View.GONE);
            }
            if (list != null) {
                adapter.updateList(list);
            }
        });
    }

    private void showReportDialog() {
        if (getContext() == null) return;

        selectedImageUri = null;
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_missing_child, null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialogView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        TextInputLayout tilName = dialogView.findViewById(R.id.tilChildName);
        TextInputLayout tilAge = dialogView.findViewById(R.id.tilChildAge);
        TextInputLayout tilLocation = dialogView.findViewById(R.id.tilChildLocation);
        TextInputLayout tilPhone = dialogView.findViewById(R.id.tilChildPhone);
        TextInputLayout tilDesc = dialogView.findViewById(R.id.tilChildDesc);

        EditText etName = dialogView.findViewById(R.id.etDialogChildName);
        EditText etAge = dialogView.findViewById(R.id.etDialogChildAge);
        EditText etLocation = dialogView.findViewById(R.id.etDialogChildLocation);
        EditText etPhone = dialogView.findViewById(R.id.etDialogChildPhone);
        EditText etDesc = dialogView.findViewById(R.id.etDialogChildDesc);
        ImageView ivPhoto = dialogView.findViewById(R.id.ivDialogChildPhoto);
        MaterialButton btnSelectPhoto = dialogView.findViewById(R.id.btnDialogSelectPhoto);
        ProgressBar dialogProgress = dialogView.findViewById(R.id.dialogProgressBar);

        ivPreviewRef = ivPhoto;

        btnSelectPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("📢 Report Missing Person")
                .setView(dialogView)
                .setPositiveButton("Submit Report", null)
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            MaterialButton button = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            button.setOnClickListener(v -> {
                if (tilName != null) tilName.setError(null);
                if (tilAge != null) tilAge.setError(null);
                if (tilLocation != null) tilLocation.setError(null);
                if (tilPhone != null) tilPhone.setError(null);

                String name = (etName != null && etName.getText() != null) ? etName.getText().toString().trim() : "";
                String age = (etAge != null && etAge.getText() != null) ? etAge.getText().toString().trim() : "";
                String location = (etLocation != null && etLocation.getText() != null) ? etLocation.getText().toString().trim() : "";
                String phone = (etPhone != null && etPhone.getText() != null) ? etPhone.getText().toString().trim() : "";
                String desc = (etDesc != null && etDesc.getText() != null) ? etDesc.getText().toString().trim() : "No extra description";

                boolean hasError = false;

                String phoneRegex = "^(\\+?[0-9]{11,14})$";
                String ageRegex = "^[0-9]{1,2}$";

                if (name.length() < 3) {
                    if (tilName != null) tilName.setError("Enter full person name (min 3 chars)");
                    hasError = true;
                }
                if (!age.matches(ageRegex) || Integer.parseInt(age) <= 0) {
                    if (tilAge != null) tilAge.setError("Age must be digits only (e.g. 8)");
                    hasError = true;
                }
                if (location.length() < 3) {
                    if (tilLocation != null) tilLocation.setError("Last seen location is mandatory");
                    hasError = true;
                }
                if (!phone.matches(phoneRegex)) {
                    if (tilPhone != null) tilPhone.setError("Enter valid phone number (11-14 digits only)");
                    hasError = true;
                }

                if (hasError) return;

                if (dialogProgress != null) dialogProgress.setVisibility(View.VISIBLE);
                button.setEnabled(false);

                if (selectedImageUri != null) {
                    uploadToCloudinaryAndSubmit(name, age, location, phone, desc, dialog, dialogProgress, button);
                } else {
                    submitReportToFirestore(name, age, location, phone, desc, "", dialog, dialogProgress, button);
                }
            });
        });

        dialog.show();
    }

    private void uploadToCloudinaryAndSubmit(String name, String age, String location, String phone, String desc,
                                             AlertDialog dialog, ProgressBar progress, MaterialButton btn) {
        backgroundExecutor.execute(() -> {
            String uploadedUrl = "";
            try {
                if (getContext() != null && selectedImageUri != null) {
                    InputStream inputStream = getContext().getContentResolver().openInputStream(selectedImageUri);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    byte[] imageBytes = output.toByteArray();

                    String boundary = "===" + System.currentTimeMillis() + "===";
                    URL url = new URL("https://api.cloudinary.com/v1_1/" + CLOUDINARY_CLOUD_NAME + "/image/upload");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setDoOutput(true);
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
                    requestBody.write(("--" + boundary + "\r\n").getBytes());
                    requestBody.write(("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n").getBytes());
                    requestBody.write((CLOUDINARY_UPLOAD_PRESET + "\r\n").getBytes());

                    requestBody.write(("--" + boundary + "\r\n").getBytes());
                    requestBody.write(("Content-Disposition: form-data; name=\"file\"; filename=\"child_photo.jpg\"\r\n").getBytes());
                    requestBody.write(("Content-Type: image/jpeg\r\n\r\n").getBytes());
                    requestBody.write(imageBytes);
                    requestBody.write(("\r\n--" + boundary + "--\r\n").getBytes());

                    conn.getOutputStream().write(requestBody.toByteArray());

                    if (conn.getResponseCode() == 200) {
                        InputStream responseStream = conn.getInputStream();
                        ByteArrayOutputStream resOut = new ByteArrayOutputStream();
                        while ((bytesRead = responseStream.read(buffer)) != -1) {
                            resOut.write(buffer, 0, bytesRead);
                        }
                        JSONObject jsonResponse = new JSONObject(resOut.toString());
                        uploadedUrl = jsonResponse.optString("secure_url", "");
                    }
                }
            } catch (Exception ignored) {}

            final String finalPhotoUrl = uploadedUrl;
            new Handler(Looper.getMainLooper()).post(() ->
                    submitReportToFirestore(name, age, location, phone, desc, finalPhotoUrl, dialog, progress, btn)
            );
        });
    }

    private void submitReportToFirestore(String name, String age, String location, String phone, String desc,
                                         String photoUrl, AlertDialog dialog, ProgressBar progress, MaterialButton btn) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String currentUid = (user != null && user.getUid() != null) ? user.getUid() : "anonymous_user";
            String reportId = "report_" + UUID.randomUUID().toString().substring(0, 8);

            MissingChild report = new MissingChild(
                    reportId,
                    currentUid,
                    name,
                    age,
                    location,
                    phone,
                    desc,
                    photoUrl,
                    23.8103,
                    90.4125,
                    System.currentTimeMillis(),
                    "ACTIVE"
            );

            FirebaseManager.getInstance().postMissingChild(report, new FirebaseManager.OnCompleteListener() {
                @Override
                public void onSuccess() {
                    if (isAdded() && getContext() != null) {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (dialog != null && dialog.isShowing()) dialog.dismiss();
                        Toast.makeText(getContext(), "Missing person report published successfully!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (isAdded() && getContext() != null) {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (btn != null) btn.setEnabled(true);
                        String msg = (e != null && e.getMessage() != null) ? e.getMessage() : "Failed to post";
                        Toast.makeText(getContext(), "Error: " + msg, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception ex) {
            if (progress != null) progress.setVisibility(View.GONE);
            if (btn != null) btn.setEnabled(true);
            Toast.makeText(getContext(), "Something went wrong: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (childListener != null) {
            childListener.remove();
        }
        binding = null;
    }
}