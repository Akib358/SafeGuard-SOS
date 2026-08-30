package com.example.safeguardsosfinal.ui.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.safeguardsosfinal.adapters.EmergencyContactAdapter;
import com.example.safeguardsosfinal.databinding.FragmentContactsSettingsBinding;
import com.example.safeguardsosfinal.models.EmergencyContact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ContactsSettingsFragment extends Fragment implements EmergencyContactAdapter.OnContactActionListener {

    private FragmentContactsSettingsBinding binding;
    private EmergencyContactAdapter adapter;
    private final List<EmergencyContact> contactList = new ArrayList<>();
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentContactsSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireActivity().getSharedPreferences("SafeGuardSOS_Prefs", Context.MODE_PRIVATE);

        adapter = new EmergencyContactAdapter(this);
        binding.rvEmergencyContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvEmergencyContacts.setAdapter(adapter);

        // লোড কাস্টম মেসেজ
        String savedMsg = prefs.getString("custom_sos_message", "I am in extreme danger! Please send help.");
        binding.etCustomMessage.setText(savedMsg);

        // সেভ কাস্টম মেসেজ
        binding.btnSaveMessage.setOnClickListener(v -> {
            String msg = binding.etCustomMessage.getText() != null ? binding.etCustomMessage.getText().toString().trim() : "";
            prefs.edit().putString("custom_sos_message", msg).apply();
            Toast.makeText(getContext(), "SOS Alert Message Updated!", Toast.LENGTH_SHORT).show();
        });

        // লোড কন্টাক্টস
        loadSavedContacts();

        binding.btnAddContact.setOnClickListener(v -> {
            if (contactList.size() >= 5) {
                Toast.makeText(getContext(), "Maximum 5 contacts allowed!", Toast.LENGTH_SHORT).show();
                return;
            }
            showContactDialog(null, -1);
        });
    }

    private void showContactDialog(@Nullable EmergencyContact existingContact, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(existingContact == null ? "Add Emergency Contact" : "Edit Contact");

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText nameInput = new EditText(getContext());
        nameInput.setHint("Contact Name");
        if (existingContact != null) nameInput.setText(existingContact.getName());
        layout.addView(nameInput);

        final EditText phoneInput = new EditText(getContext());
        phoneInput.setHint("Phone Number (+880...)");
        if (existingContact != null) phoneInput.setText(existingContact.getPhone());
        layout.addView(phoneInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone)) {
                Toast.makeText(getContext(), "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (existingContact == null) {
                contactList.add(new EmergencyContact(UUID.randomUUID().toString(), name, phone));
            } else {
                existingContact.setName(name);
                existingContact.setPhone(phone);
            }

            saveContactsToPrefs();
            adapter.updateList(new ArrayList<>(contactList));
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void saveContactsToPrefs() {
        try {
            JSONArray array = new JSONArray();
            for (EmergencyContact c : contactList) {
                JSONObject obj = new JSONObject();
                obj.put("id", c.getId());
                obj.put("name", c.getName());
                obj.put("phone", c.getPhone());
                array.put(obj);
            }
            prefs.edit().putString("emergency_contacts_json", array.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadSavedContacts() {
        contactList.clear();
        String json = prefs.getString("emergency_contacts_json", null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    contactList.add(new EmergencyContact(
                            obj.getString("id"),
                            obj.getString("name"),
                            obj.getString("phone")
                    ));
                }
            } catch (Exception ignored) {}
        }
        adapter.updateList(new ArrayList<>(contactList));
    }

    @Override
    public void onEdit(EmergencyContact contact, int position) {
        showContactDialog(contact, position);
    }

    @Override
    public void onDelete(EmergencyContact contact, int position) {
        contactList.remove(position);
        saveContactsToPrefs();
        adapter.updateList(new ArrayList<>(contactList));
        Toast.makeText(getContext(), "Contact removed", Toast.LENGTH_SHORT).show();
    }
}