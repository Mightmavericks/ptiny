package com.example.protegotinyever.tt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.protegotinyever.R;
import com.example.protegotinyever.webrtc.WebRTCClient;

import org.webrtc.DataChannel;

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
        holder.usernameText.setText(user.getUsername().toUpperCase());
        if (user.getUsername() != null && !user.getUsername().isEmpty()) {
            holder.usernameLetter.setText(String.valueOf(user.getUsername().charAt(0)).toUpperCase());
        }
        
        // Set phone number
        holder.phoneText.setText(user.getPhone());

        WebRTCClient webRTCClient = WebRTCClient.getInstance(holder.itemView.getContext(), null);
        DataChannel dataChannel = webRTCClient.getDataChannels().get(user.getUsername());
        
        // Check both connection and data channel state
        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            holder.statusText.setText("CONNECTED");
            holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.success_green));
        } else if (webRTCClient.isAttemptingConnection(user.getUsername())) {
            holder.statusText.setText("CONNECTING");
            holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.warning_yellow));
        } else {
            holder.statusText.setText("CLOSED");
            holder.statusText.setTextColor(holder.itemView.getContext().getColor(R.color.error_red));
        }

        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText, phoneText, usernameLetter;
        EditText statusText;

        public UserViewHolder(View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.usernameText);
            phoneText = itemView.findViewById(R.id.phoneText);
            usernameLetter = itemView.findViewById(R.id.usernameLetter);
            statusText = itemView.findViewById(R.id.statusIndicator);
        }
    }

    public interface OnUserClickListener {
        void onUserClick(UserModel user);
    }
}
