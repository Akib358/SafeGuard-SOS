package com.example.safeguardsosfinal.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.safeguardsosfinal.R;
import com.example.safeguardsosfinal.databinding.FragmentSafetyDashboardBinding;
import com.example.safeguardsosfinal.models.ChatMessage;
import com.example.safeguardsosfinal.models.SOSAlert;
import com.example.safeguardsosfinal.network.FirebaseManager;
import com.example.safeguardsosfinal.services.PanicSOSService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SafetyDashboardFragment extends Fragment implements LocationListener {

    private FragmentSafetyDashboardBinding binding;
    private LocationManager locationManager;
    private ListenerRegistration sosCountListener;
    private ListenerRegistration myActiveSosListener;
    private ListenerRegistration activeChatListener;

    private SOSAlert currentActiveAlert = null;
    private Location currentDeviceLocation = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSafetyDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

        binding.swipeRefreshDashboard.setColorSchemeColors(0xFF448AFF, 0xFFE53935);
        binding.swipeRefreshDashboard.setOnRefreshListener(() -> {
            requestAccurateGPSLocation();
            listenToDashboardCounts();
            binding.swipeRefreshDashboard.postDelayed(() -> {
                if (binding != null) binding.swipeRefreshDashboard.setRefreshing(false);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "GPS & Live Threat Synced", Toast.LENGTH_SHORT).show();
                }
            }, 1000);
        });

        // Instant SOS Button Click - Single SOS trigger
        binding.cardEmergencySOS.setOnClickListener(v -> {
            // Anti-spam debounce: If current user already has an active SOS in progress, prevent duplicate
            if (currentActiveAlert != null && "ACTIVE_DANGER".equalsIgnoreCase(currentActiveAlert.getStatus())) {
                Toast.makeText(getContext(), "⚠️ An SOS broadcast is already active! Manage it via Active SOS Hub.", Toast.LENGTH_LONG).show();
                showActiveSOSHubDialog();
                return;
            }

            Intent sosIntent = new Intent(requireContext(), PanicSOSService.class);
            sosIntent.setAction("TRIGGER_PANIC_SOS");
            if (currentDeviceLocation != null) {
                sosIntent.putExtra("latitude", currentDeviceLocation.getLatitude());
                sosIntent.putExtra("longitude", currentDeviceLocation.getLongitude());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(sosIntent);
            } else {
                requireContext().startService(sosIntent);
            }

            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                Intent directCall = new Intent(Intent.ACTION_CALL, Uri.parse("tel:999"));
                startActivity(directCall);
            } else {
                ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CALL_PHONE}, 101);
            }

            Toast.makeText(getContext(), "🚨 EMERGENCY SOS BROADCASTED!", Toast.LENGTH_LONG).show();
        });

        // "Active SOS" Card Click -> Opens Active SOS & Rescue Incident Hub
        View.OnClickListener hubOpener = v -> showActiveSOSHubDialog();
        binding.cardActiveSosStats.setOnClickListener(hubOpener);
        binding.tvActiveIncidents.setOnClickListener(hubOpener);

        // "Safe Zones" Card Click -> Shows Dynamic Threat Info
        binding.cardSafeZonesStats.setOnClickListener(v -> showSafeZoneInfoDialog());
        binding.tvRegisteredSafeZones.setOnClickListener(v -> showSafeZoneInfoDialog());

        requestAccurateGPSLocation();
        listenToDashboardCounts();
        listenToUserActiveSOS();
    }

    private void listenToUserActiveSOS() {
        myActiveSosListener = FirebaseManager.getInstance().listenToMyActiveSOS(activeAlert -> {
            this.currentActiveAlert = activeAlert;
        });
    }

    private void showSafeZoneInfoDialog() {
        if (getContext() == null) return;
        String currentStatus = binding.tvRegisteredSafeZones.getText().toString();
        String explanation;

        if ("DANGER ZONE".equalsIgnoreCase(currentStatus) || currentStatus.contains("DANGER")) {
            explanation = "🚨 DANGER ZONE ALERT!\n\nThere is an active Emergency SOS broadcast within 1.5 KM of your current GPS position. Please be vigilant, help coordinate rescue via the Active SOS Hub, or stay in a safe secured location.";
        } else if ("MODERATE ZONE".equalsIgnoreCase(currentStatus) || "CAUTION ZONE".equalsIgnoreCase(currentStatus) || currentStatus.contains("MODERATE") || currentStatus.contains("CAUTION")) {
            explanation = "⚠️ MODERATE ZONE\n\nThere are ongoing alerts within 3.5 KM perimeter of your location. Monitor live updates in notifications.";
        } else {
            explanation = "✅ SAFE ZONE\n\nNo active emergency SOS incidents reported in your immediate perimeter based on your live GPS location.";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("🛡️ Live Zone Safety Status: " + currentStatus)
                .setMessage(explanation)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showActiveSOSHubDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_active_sos_hub, null);
        CardView cardMySOS = dialogView.findViewById(R.id.cardMySOSSection);
        TextView tvMySosHeader = dialogView.findViewById(R.id.tvMySosStatusHeader);
        TextView tvMySosDesc = dialogView.findViewById(R.id.tvMySosDescription);
        TextView tvMySosBadge = dialogView.findViewById(R.id.tvMySosBadge);
        MaterialButton btnManageMySOS = dialogView.findViewById(R.id.btnManageMySOS);
        MaterialButton btnClose = dialogView.findViewById(R.id.btnCloseHub);
        RecyclerView rv = dialogView.findViewById(R.id.rvActiveSOSList);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyActiveSos);

        AlertDialog hubDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        // Top Section: Check Your SOS
        cardMySOS.setVisibility(View.VISIBLE);
        if (currentActiveAlert != null && "ACTIVE_DANGER".equalsIgnoreCase(currentActiveAlert.getStatus())) {
            cardMySOS.setCardBackgroundColor(0xFF2A1B1B);
            tvMySosHeader.setText("🚨 You Have an Active SOS Broadcast!");
            tvMySosBadge.setText("ACTIVE");
            tvMySosBadge.setBackgroundColor(0xFFD50000);
            tvMySosDesc.setText("Your SOS is active. Describe what happened and mark as safe when you are out of danger.");
            btnManageMySOS.setVisibility(View.VISIBLE);
            btnManageMySOS.setOnClickListener(v -> {
                hubDialog.dismiss();
                showMarkSafeDialog(currentActiveAlert.getAlertId());
            });
        } else {
            cardMySOS.setCardBackgroundColor(0xFF1B2A1E);
            tvMySosHeader.setText("🛡️ Check Your SOS: SAFE");
            tvMySosHeader.setTextColor(0xFF00E676);
            tvMySosBadge.setText("NORMAL / SAFE");
            tvMySosBadge.setBackgroundColor(0xFF00E676);
            tvMySosDesc.setText("You have no active emergency broadcasted. Your live GPS protection is active.");
            btnManageMySOS.setVisibility(View.GONE);
        }

        // Fetch ongoing SOS alerts
        FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .get()
                .addOnSuccessListener(queryDocs -> {
                    List<SOSAlert> activeList = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocs) {
                        SOSAlert a = new SOSAlert();
                        a.setAlertId(doc.getString("alertId") != null ? doc.getString("alertId") : doc.getId());
                        a.setUserId(doc.getString("userId"));
                        a.setUserName(doc.getString("userName") != null ? doc.getString("userName") : "Emergency Victim");
                        a.setUserPhone(doc.getString("phone") != null ? doc.getString("phone") : "Unknown");
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        a.setLatitude(lat != null ? lat : 0.0);
                        a.setLongitude(lng != null ? lng : 0.0);
                        Long time = doc.getLong("timestamp");
                        a.setTimestamp(time != null ? time : System.currentTimeMillis());
                        Long msgCount = doc.getLong("messageCount");
                        a.setMessageCount(msgCount != null ? msgCount.intValue() : 0);
                        activeList.add(a);
                    }

                    if (activeList.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        rv.setVisibility(View.GONE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        rv.setVisibility(View.VISIBLE);
                    }

                    rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                    rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_sos_hub, parent, false);
                            return new RecyclerView.ViewHolder(itemView) {};
                        }

                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                            SOSAlert alert = activeList.get(position);
                            TextView tvName = holder.itemView.findViewById(R.id.tvHubVictimName);
                            TextView tvDist = holder.itemView.findViewById(R.id.tvHubDistance);
                            TextView tvPhone = holder.itemView.findViewById(R.id.tvHubPhone);
                            TextView tvTime = holder.itemView.findViewById(R.id.tvHubTime);
                            TextView tvChat = holder.itemView.findViewById(R.id.tvHubChatCount);
                            MaterialButton btnChat = holder.itemView.findViewById(R.id.btnOpenHubChat);

                            tvName.setText("🚨 " + alert.getUserName());
                            tvPhone.setText("Phone: " + alert.getUserPhone());

                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
                            tvTime.setText(sdf.format(new Date(alert.getTimestamp())));

                            // Calculate live distance
                            float distanceMeters = 0;
                            if (currentDeviceLocation != null) {
                                Location vicLoc = new Location("Vic");
                                vicLoc.setLatitude(alert.getLatitude());
                                vicLoc.setLongitude(alert.getLongitude());
                                distanceMeters = currentDeviceLocation.distanceTo(vicLoc);
                            }

                            String distStr = distanceMeters > 1000
                                    ? String.format(Locale.US, "%.1f KM away", distanceMeters / 1000)
                                    : ((int) distanceMeters) + "m away";
                            tvDist.setText(distStr);

                            tvChat.setText("💬 " + alert.getMessageCount() + " Rescue Messages");

                            View.OnClickListener openChatListener = v -> {
                                hubDialog.dismiss();
                                openRescueChatDialog(alert.getAlertId(), alert.getUserName());
                            };

                            holder.itemView.setOnClickListener(openChatListener);
                            btnChat.setOnClickListener(openChatListener);
                        }

                        @Override
                        public int getItemCount() { return activeList.size(); }
                    });
                });

        btnClose.setOnClickListener(v -> hubDialog.dismiss());
        hubDialog.show();
    }

    private void openRescueChatDialog(String alertId, String victimName) {
        if (getContext() == null) return;

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sos_chat, null);
        TextView tvTitle = view.findViewById(R.id.tvChatTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvChatSubtitle);
        EditText etMsg = view.findViewById(R.id.etChatMessage);
        ImageButton btnSend = view.findViewById(R.id.btnSendMessage);
        RecyclerView rvChat = view.findViewById(R.id.rvChatMessages);
        MaterialButton btnClose = view.findViewById(R.id.btnCloseChat);

        TextView chipOnMyWay = view.findViewById(R.id.chipOnMyWay);
        TextView chipCalled999 = view.findViewById(R.id.chipCalled999);
        TextView chipNearby = view.findViewById(R.id.chipNearby);
        TextView chipStaySafe = view.findViewById(R.id.chipStaySafe);

        tvTitle.setText("Rescue Chat: " + (victimName != null ? victimName : "Victim"));
        tvSubtitle.setText("Coordinate how to rescue and share distance/status");

        List<ChatMessage> chatList = new ArrayList<>();
        rvChat.setLayoutManager(new LinearLayoutManager(requireContext()));

        RecyclerView.Adapter<RecyclerView.ViewHolder> chatAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View itemMsgView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sos_chat_message, parent, false);
                return new RecyclerView.ViewHolder(itemMsgView) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ChatMessage m = chatList.get(position);
                TextView tvSender = holder.itemView.findViewById(R.id.tvChatSender);
                TextView tvText = holder.itemView.findViewById(R.id.tvChatText);
                TextView tvTime = holder.itemView.findViewById(R.id.tvChatTime);

                tvSender.setText(m.getSenderName() != null ? m.getSenderName() : "Helper");
                tvText.setText(m.getMessage() != null ? m.getMessage() : "");
                SimpleDateFormat timeFormat = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
                tvTime.setText(timeFormat.format(new Date(m.getTimestamp())));
            }

            @Override
            public int getItemCount() { return chatList.size(); }
        };
        rvChat.setAdapter(chatAdapter);

        if (activeChatListener != null) activeChatListener.remove();
        activeChatListener = FirebaseManager.getInstance().listenToRescueChat(alertId, (snapshots, error) -> {
            if (snapshots != null) {
                chatList.clear();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    ChatMessage msg = doc.toObject(ChatMessage.class);
                    if (msg != null) chatList.add(msg);
                }
                chatAdapter.notifyDataSetChanged();
                if (!chatList.isEmpty()) rvChat.scrollToPosition(chatList.size() - 1);
            }
        });

        AlertDialog chatDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> chatDialog.dismiss());
        }

        // Quick action chips
        if (chipOnMyWay != null) chipOnMyWay.setOnClickListener(v -> etMsg.setText("🏃 I'm on the way to help right now!"));
        if (chipCalled999 != null) chipCalled999.setOnClickListener(v -> etMsg.setText("📞 I have dialed Police 999 and shared your location!"));
        if (chipNearby != null) chipNearby.setOnClickListener(v -> etMsg.setText("📍 I am approaching and very close to your location!"));
        if (chipStaySafe != null) chipStaySafe.setOnClickListener(v -> etMsg.setText("🛡️ Please stay calm and try to reach a safe crowded place/shop!"));

        btnSend.setOnClickListener(v -> {
            String text = etMsg.getText().toString().trim();
            if (!text.isEmpty()) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                String senderName = (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                        ? user.getDisplayName() : "Rescue Volunteer";
                FirebaseManager.getInstance().sendRescueChatMessage(alertId, text, senderName);
                etMsg.setText("");
            }
        });

        chatDialog.setOnDismissListener(d -> {
            if (activeChatListener != null) activeChatListener.remove();
        });

        chatDialog.show();
    }

    private void showMarkSafeDialog(String alertId) {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_mark_safe, null);
        EditText etSafeNote = dialogView.findViewById(R.id.etSafeNote);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelSafe);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmSafe);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String note = etSafeNote.getText().toString().trim();
            if (note.isEmpty()) {
                etSafeNote.setError("Please describe what happened & how you became safe");
                return;
            }

            FirebaseManager.getInstance().markSOSAsSafe(alertId, note, new FirebaseManager.OnCompleteListener() {
                @Override
                public void onSuccess() {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "✅ You are marked SAFE! Full story updated.", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        if (currentDeviceLocation != null) {
                            updateDynamicThreatRadar(currentDeviceLocation.getLatitude(), currentDeviceLocation.getLongitude());
                        }
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Failed to update. Check connection.", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });

        dialog.show();
    }

    // Dynamic Zone Update based on live GPS location & active incidents
    private void updateDynamicThreatRadar(double lat, double lng) {
        FirebaseManager.getInstance().calculateZoneThreat(lat, lng, (zoneType, activeIncidents, uniqueUsersCount) -> {
            if (binding == null || getActivity() == null) return;

            requireActivity().runOnUiThread(() -> {
                if ("DANGER".equalsIgnoreCase(zoneType)) {
                    binding.tvRegisteredSafeZones.setText("DANGER ZONE");
                    binding.tvRegisteredSafeZones.setTextColor(0xFFFF1744); // Red
                } else if ("MODERATE".equalsIgnoreCase(zoneType) || "CAUTION".equalsIgnoreCase(zoneType)) {
                    binding.tvRegisteredSafeZones.setText("MODERATE ZONE");
                    binding.tvRegisteredSafeZones.setTextColor(0xFFFFD600); // Yellow
                } else {
                    binding.tvRegisteredSafeZones.setText("SAFE ZONE");
                    binding.tvRegisteredSafeZones.setTextColor(0xFF00E676); // Green
                }
            });
        });
    }

    private void requestAccurateGPSLocation() {
        if (getContext() == null || locationManager == null) return;

        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 1, this);
            }

            Location lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastGps != null) {
                updateLocationUI(lastGps);
            } else {
                Location lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (lastNet != null) updateLocationUI(lastNet);
            }
        }
    }

    private void updateLocationUI(Location loc) {
        if (loc == null || binding == null || getContext() == null) return;

        this.currentDeviceLocation = loc;
        double lat = loc.getLatitude();
        double lng = loc.getLongitude();

        binding.tvDashCoordinates.setText("Lat: " + String.format(Locale.US, "%.5f", lat) + ", Lng: " + String.format(Locale.US, "%.5f", lng));

        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(lat, lng, 1);
            if (list != null && !list.isEmpty()) {
                Address addr = list.get(0);
                String subLocality = addr.getSubLocality() != null ? addr.getSubLocality() : "";
                String locality = addr.getLocality() != null ? addr.getLocality() : addr.getFeatureName();
                String area = !subLocality.isEmpty() ? subLocality + ", " + locality : locality;
                binding.tvDashLocation.setText("📍 " + (area != null ? area : "Current Location"));
            }
        } catch (Exception e) {
            binding.tvDashLocation.setText("📍 Live GPS Active");
        }

        updateDynamicThreatRadar(lat, lng);
    }

    private void listenToDashboardCounts() {
        sosCountListener = FirebaseFirestore.getInstance().collection("emergency_broadcasts")
                .whereEqualTo("status", "ACTIVE_DANGER")
                .addSnapshotListener((snapshots, error) -> {
                    if (snapshots != null && binding != null) {
                        binding.tvActiveIncidents.setText(String.valueOf(snapshots.size()));
                        if (currentDeviceLocation != null) {
                            updateDynamicThreatRadar(currentDeviceLocation.getLatitude(), currentDeviceLocation.getLongitude());
                        }
                    }
                });
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        updateLocationUI(location);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (locationManager != null) locationManager.removeUpdates(this);
        if (sosCountListener != null) sosCountListener.remove();
        if (myActiveSosListener != null) myActiveSosListener.remove();
        if (activeChatListener != null) activeChatListener.remove();
        binding = null;
    }
}