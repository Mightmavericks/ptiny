package com.example.protegotinyever.tt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.protegotinyever.R;
import com.example.protegotinyever.service.ConnectionManager;
import com.example.protegotinyever.webrtc.WebRTCClient;

import org.webrtc.DataChannel;

import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {
    private final List<UserModel> userList;
    private final OnUserClickListener listener;
    private final boolean isRequestsTab;
    private int rea = 1;

    public UserAdapter(List<UserModel> userList, OnUserClickListener listener, boolean isRequestsTab) {
        this.userList = userList;
        this.listener = listener;
        this.isRequestsTab = isRequestsTab;
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
        if (!user.getUsername().isEmpty()) {
            holder.usernameLetter.setText(String.valueOf(user.getUsername().charAt(0)).toUpperCase());
        }
        
        // Set phone number
        holder.phoneText.setText(user.getPhone());
        
        // Set online status dot
        holder.onlineStatusDot.setVisibility(user.isOnline() ? View.VISIBLE : View.GONE);
        holder.onlineStatusDot.setColorFilter(holder.itemView.getContext().getColor(R.color.success_green));

        // Check WebRTC connection status
        WebRTCClient webRTCClient = WebRTCClient.getInstance(holder.itemView.getContext(), null);
        DataChannel dataChannel = webRTCClient.getDataChannels().get(user.getUsername());
        
        // Update connection button color based on status
        updateConnectionButtonStatus(holder.connectButton, dataChannel, webRTCClient, user.getUsername());

        // Set button click listeners
        holder.connectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onConnectionButtonClick(user);
            }
        });

        // Only show and setup chat button in chats tab
        if (isRequestsTab) {
            holder.chatButton.setVisibility(View.GONE);
        } else {
            holder.chatButton.setVisibility(View.VISIBLE);
            holder.chatButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatButtonClick(user);
                }
            });
        }
    }

    private void updateConnectionButtonStatus(Button button, DataChannel dataChannel, WebRTCClient webRTCClient, String username) {
        int backgroundColor;
        String statusText;

        if (dataChannel != null && dataChannel.state() == DataChannel.State.OPEN) {
            backgroundColor = R.color.success_green;
            statusText = "Connected";
        } else if (webRTCClient.isAttemptingConnection(username)) {
            backgroundColor = R.color.warning_yellow;
            statusText = "Connecting";
        } else if (!isRequestsTab && ConnectionManager.getInstance(button.getContext()).isUserConnected(username)) {
            backgroundColor = R.color.warning_yellow;
            statusText = "Offline";
        } else {
            backgroundColor = R.color.error_red;
            statusText = "Connect";
        }

        button.getContext().getResources().getColor(backgroundColor, button.getContext().getTheme());
        button.setBackgroundColor(button.getContext().getResources().getColor(backgroundColor, button.getContext().getTheme()));
        button.setText(statusText);
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView usernameText, phoneText, usernameLetter;
        EditText statusText;
        ImageView onlineStatusDot;
        Button connectButton, chatButton;

        public UserViewHolder(View itemView) {
            super(itemView);
            usernameText = itemView.findViewById(R.id.usernameText);
            phoneText = itemView.findViewById(R.id.phoneText);
            usernameLetter = itemView.findViewById(R.id.usernameLetter);
            onlineStatusDot = itemView.findViewById(R.id.onlineStatusDot);
            connectButton = itemView.findViewById(R.id.connectButton);
            chatButton = itemView.findViewById(R.id.chatButton);
        }
    }

    public interface OnUserClickListener {
        void onConnectionButtonClick(UserModel user);
        void onChatButtonClick(UserModel user);
    }
}
