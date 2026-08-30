package com.example.safeguardsosfinal.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.safeguardsosfinal.databinding.ItemEmergencyContactBinding;
import com.example.safeguardsosfinal.models.EmergencyContact;
import java.util.ArrayList;
import java.util.List;

public class EmergencyContactAdapter extends RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder> {

    public interface OnContactActionListener {
        void onEdit(EmergencyContact contact, int position);
        void onDelete(EmergencyContact contact, int position);
    }

    private List<EmergencyContact> contactList = new ArrayList<>();
    private final OnContactActionListener listener;

    public EmergencyContactAdapter(OnContactActionListener listener) {
        this.listener = listener;
    }

    public void updateList(List<EmergencyContact> list) {
        this.contactList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemEmergencyContactBinding binding = ItemEmergencyContactBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmergencyContact contact = contactList.get(position);
        holder.binding.tvContactName.setText(contact.getName());
        holder.binding.tvContactPhone.setText(contact.getPhone());

        holder.binding.btnEditContact.setOnClickListener(v -> listener.onEdit(contact, position));
        holder.binding.btnDeleteContact.setOnClickListener(v -> listener.onDelete(contact, position));
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemEmergencyContactBinding binding;
        public ViewHolder(ItemEmergencyContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}