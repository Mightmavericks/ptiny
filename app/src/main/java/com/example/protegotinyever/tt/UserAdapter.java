package com.example.protegotinyever.tt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.protegotinyever.R;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private final List<UserModel> userList;
    private final OnUserClickListener listener;

    public UserAdapter(List<UserModel> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        UserModel user = userList.get(position);
        
        // Set username and its first letter
        holder.usernameText.setText(user.getUsername());
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            holder.usernameLetter.setText(String.valueOf(user.getUsername().charAt(0)).toUpperCase());
        }
        
        // Set phone number
        holder.phoneText.setText(user.getPhone());

        // Set connection status
        if (user.isOnline()) {
            holder.statusIndicator.setText("ACTIVE");
            holder.statusIndicator.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.success_green, null));
        } else {
            holder.statusIndicator.setText("CLOSED");
            holder.statusIndicator.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.error_red, null));
        }

        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText, phoneText, usernameLetter, statusIndicator;

        public UserViewHolder(View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.usernameText);
            phoneText = itemView.findViewById(R.id.phoneText);
            usernameLetter = itemView.findViewById(R.id.usernameLetter);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
        }
    }

    public interface OnUserClickListener {
        void onUserClick(UserModel user);
    }
}
