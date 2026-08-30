package com.example.safeguardsosfinal.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.databinding.ActivityMainBinding;
import com.example.safeguardsosfinal.models.MissingChild;
import com.example.safeguardsosfinal.services.PanicSOSService;
import com.example.safeguardsosfinal.ui.fragments.BloodNetworkFragment;
import com.example.safeguardsosfinal.ui.fragments.ContactsSettingsFragment;
import com.example.safeguardsosfinal.ui.fragments.MapRadarFragment;
import com.example.safeguardsosfinal.ui.fragments.MissingChildFragment;
import com.example.safeguardsosfinal.ui.fragments.NotificationsFragment;
import com.example.safeguardsosfinal.ui.fragments.SafetyDashboardFragment;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CHECK_SETTINGS = 2001;
    private ActivityMainBinding binding;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private ListenerRegistration topBadgeListener;

    private final ActivityResultLauncher<String[]> appPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                promptUserToEnableGPS();
                startSOSBackgroundService();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (auth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            loadFragment(new SafetyDashboardFragment());
        }

        setupDrawerAndToolbar();
        setupBottomNavigation();
        setupTopNotificationBell();
        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        promptUserToEnableGPS();
    }

    private void setupTopNotificationBell() {
        binding.btnNotificationBell.setOnClickListener(v -> loadFragment(new NotificationsFragment()));

        long twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000);
        topBadgeListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .whereGreaterThan("timestamp", twoHoursAgo)
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && !snapshots.isEmpty()) {
                        binding.tvTopNotifBadge.setVisibility(View.VISIBLE);
                        binding.tvTopNotifBadge.setText(String.valueOf(snapshots.size()));
                    } else {
                        binding.tvTopNotifBadge.setVisibility(View.GONE);
                    }
                });
    }

    public void promptUserToEnableGPS() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true);

        Task<LocationSettingsResponse> task = LocationServices.getSettingsClient(this)
                .checkLocationSettings(builder.build());

        task.addOnFailureListener(e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    resolvable.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException ignored) {}
            }
        });
    }

    private void setupDrawerAndToolbar() {
        binding.btnOpenDrawer.setOnClickListener(v -> binding.drawerLayout.openDrawer(GravityCompat.START));

        View headerView = binding.navigationDrawer.getHeaderView(0);
        TextView tvUser = headerView.findViewById(R.id.tvHeaderUserName);
        TextView tvMail = headerView.findViewById(R.id.tvHeaderEmail);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String name = (currentUser.getDisplayName() != null && !currentUser.getDisplayName().isEmpty())
                    ? currentUser.getDisplayName()
                    : "SafeGuard Verified User";
            tvUser.setText(name);
            tvMail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "Active SOS User");
        }

        binding.navigationDrawer.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            binding.drawerLayout.closeDrawer(GravityCompat.START);

            if (id == R.id.nav_drawer_profile) {
                showAccountProfileDialog();
                return true;
            } else if (id == R.id.nav_drawer_my_posts) {
                showMyPostsOptionsDialog();
                return true;
            } else if (id == R.id.nav_drawer_settings) {
                showSettingsDialog();
                return true;
            } else if (id == R.id.nav_drawer_logout) {
                performUserLogout();
                return true;
            }
            return false;
        });
    }

    private void showMyPostsOptionsDialog() {
        String[] options = {"Manage My Missing Person Reports", "Manage My Blood Requests"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("📋 My Posts & Reports")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        loadUserMissingReports();
                    } else {
                        loadUserBloodRequests();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void loadUserMissingReports() {
        String uid = auth.getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("missing_children")
                .whereEqualTo("reporterId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "You haven't posted any missing reports.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> titles = new ArrayList<>();
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    for (DocumentSnapshot doc : docs) {
                        String name = doc.getString("childName");
                        String status = doc.getString("status");
                        Boolean isEdited = doc.getBoolean("edited");
                        String editedTag = (isEdited != null && isEdited) ? " (Edited)" : "";
                        titles.add((name != null ? name : "Report") + " [" + status + "]" + editedTag);
                    }

                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Select Report to Manage")
                            .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                                showManageSingleReportDialog(docs.get(which), "missing_children");
                            })
                            .setNegativeButton("Back", null)
                            .show();
                });
    }

    private void loadUserBloodRequests() {
        String uid = auth.getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("blood_requests")
                .whereEqualTo("requesterId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "You haven't posted any blood requests.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<String> titles = new ArrayList<>();
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    for (DocumentSnapshot doc : docs) {
                        String grp = doc.getString("bloodGroup");
                        String hospital = doc.getString("hospital");
                        String status = doc.getString("status");
                        Boolean isEdited = doc.getBoolean("edited");
                        String editedTag = (isEdited != null && isEdited) ? " (Edited)" : "";
                        titles.add((grp != null ? grp : "Blood") + " at " + hospital + " [" + status + "]" + editedTag);
                    }

                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Select Request to Manage")
                            .setItems(titles.toArray(new String[0]), (dialog, which) -> {
                                showManageSingleReportDialog(docs.get(which), "blood_requests");
                            })
                            .setNegativeButton("Back", null)
                            .show();
                });
    }

    private void showManageSingleReportDialog(DocumentSnapshot doc, String collectionName) {
        String docId = doc.getId();
        String[] actions = {"Mark as FOUND / RESOLVED", "Edit Post", "Delete Post"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Actions")
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        FirebaseFirestore.getInstance().collection(collectionName).document(docId)
                                .update("status", "FOUND / RESOLVED")
                                .addOnSuccessListener(v -> Toast.makeText(this, "Status updated to FOUND / RESOLVED", Toast.LENGTH_SHORT).show());
                    } else if (which == 1) {
                        if (collectionName.equals("missing_children")) {
                            showEditMissingReportDialog(doc);
                        } else {
                            showEditBloodRequestDialog(doc);
                        }
                    } else if (which == 2) {
                        FirebaseFirestore.getInstance().collection(collectionName).document(docId)
                                .delete()
                                .addOnSuccessListener(v -> Toast.makeText(this, "Post deleted successfully", Toast.LENGTH_SHORT).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditMissingReportDialog(DocumentSnapshot doc) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_missing_child, null);
        EditText etName = view.findViewById(R.id.etDialogChildName);
        EditText etAge = view.findViewById(R.id.etDialogChildAge);
        EditText etLoc = view.findViewById(R.id.etDialogChildLocation);
        EditText etPhone = view.findViewById(R.id.etDialogChildPhone);
        EditText etDesc = view.findViewById(R.id.etDialogChildDesc);

        etName.setText(doc.getString("childName"));
        etAge.setText(doc.getString("age"));
        etLoc.setText(doc.getString("lastSeenLocation"));
        etPhone.setText(doc.getString("contactPhone"));
        etDesc.setText(doc.getString("description"));

        new MaterialAlertDialogBuilder(this)
                .setTitle("✏️ Edit Missing Report")
                .setView(view)
                .setPositiveButton("Update", (dialog, which) -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("childName", etName.getText().toString().trim());
                    updates.put("age", etAge.getText().toString().trim());
                    updates.put("lastSeenLocation", etLoc.getText().toString().trim());
                    updates.put("contactPhone", etPhone.getText().toString().trim());
                    updates.put("description", etDesc.getText().toString().trim());
                    updates.put("edited", true);
                    updates.put("editedAt", System.currentTimeMillis());

                    FirebaseFirestore.getInstance().collection("missing_children").document(doc.getId())
                            .update(updates)
                            .addOnSuccessListener(v -> Toast.makeText(this, "Report updated with (Edited) tag!", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditBloodRequestDialog(DocumentSnapshot doc) {
        EditText etHospital = new EditText(this);
        etHospital.setHint("Hospital / Location Name");
        etHospital.setText(doc.getString("hospital"));

        new MaterialAlertDialogBuilder(this)
                .setTitle("✏️ Edit Blood Request")
                .setView(etHospital)
                .setPositiveButton("Update", (dialog, which) -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("hospital", etHospital.getText().toString().trim());
                    updates.put("edited", true);
                    updates.put("editedAt", System.currentTimeMillis());

                    FirebaseFirestore.getInstance().collection("blood_requests").document(doc.getId())
                            .update(updates)
                            .addOnSuccessListener(v -> Toast.makeText(this, "Request updated with (Edited) tag!", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selected = null;
            int id = item.getItemId();

            if (id == R.id.nav_dashboard) selected = new SafetyDashboardFragment();
            else if (id == R.id.nav_map_radar) selected = new MapRadarFragment();
            else if (id == R.id.nav_missing_child) selected = new MissingChildFragment();
            else if (id == R.id.nav_blood_network) selected = new BloodNetworkFragment();
            else if (id == R.id.nav_contacts) selected = new ContactsSettingsFragment();

            if (selected != null) {
                loadFragment(selected);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss();
    }

    private void showAccountProfileDialog() {
        FirebaseUser user = auth.getCurrentUser();
        String uid = user != null ? user.getUid() : "N/A";
        String email = (user != null && user.getEmail() != null) ? user.getEmail() : "No email linked";

        new MaterialAlertDialogBuilder(this)
                .setTitle("👤 User Account Details")
                .setMessage("Account UID:\n" + uid + "\n\nEmail Address:\n" + email + "\n\nSOS Protection Status: ACTIVE\nDevice Linked: Yes")
                .setPositiveButton("Close", (d, w) -> d.dismiss())
                .show();
    }

    private void showSettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_sos_settings, null);
        MaterialSwitch sw999 = view.findViewById(R.id.switchAutoCall999);
        MaterialSwitch swPower = view.findViewById(R.id.switchPowerButtonTrigger);
        MaterialSwitch swRadar = view.findViewById(R.id.switchRadiusAlert);
        RadioGroup rgSim = view.findViewById(R.id.rgSimSlot);
        RadioButton rbSim1 = view.findViewById(R.id.rbSim1);
        RadioButton rbSim2 = view.findViewById(R.id.rbSim2);

        SharedPreferences sp = getSharedPreferences("SafeGuardSettings", MODE_PRIVATE);
        sw999.setChecked(sp.getBoolean("auto_call_999", true));
        swPower.setChecked(sp.getBoolean("power_trigger", true));
        swRadar.setChecked(sp.getBoolean("radar_alert", true));

        int savedSimSlot = sp.getInt("selected_sim_slot", 0);
        if (savedSimSlot == 1) rbSim2.setChecked(true);
        else rbSim1.setChecked(true);

        sw999.setOnCheckedChangeListener((btn, isChecked) -> sp.edit().putBoolean("auto_call_999", isChecked).apply());
        swPower.setOnCheckedChangeListener((btn, isChecked) -> sp.edit().putBoolean("power_trigger", isChecked).apply());
        swRadar.setOnCheckedChangeListener((btn, isChecked) -> sp.edit().putBoolean("radar_alert", isChecked).apply());

        rgSim.setOnCheckedChangeListener((group, checkedId) -> {
            int slot = (checkedId == R.id.rbSim2) ? 1 : 0;
            sp.edit().putInt("selected_sim_slot", slot).apply();
        });

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton("Save Settings", (d, w) -> d.dismiss())
                .show();
    }

    private void performUserLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out from SafeGuard SOS?")
                .setPositiveButton("Log Out", (d, w) -> {
                    auth.signOut();
                    Intent logoutIntent = new Intent(this, LoginActivity.class);
                    logoutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(logoutIntent);
                    finish();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .show();
    }

    private void checkAndRequestPermissions() {
        List<String> list = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.CALL_PHONE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!list.isEmpty()) {
            appPermissionLauncher.launch(list.toArray(new String[0]));
        } else {
            promptUserToEnableGPS();
            startSOSBackgroundService();
        }
    }

    private void startSOSBackgroundService() {
        try {
            Intent serviceIntent = new Intent(this, PanicSOSService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (topBadgeListener != null) {
            topBadgeListener.remove();
        }
    }
}