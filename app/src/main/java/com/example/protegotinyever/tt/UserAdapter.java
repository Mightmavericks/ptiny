package com.example.protegotinyever.tt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
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
        holder.username.setText(user.getUsername());
        holder.profileImage.setImageResource(R.drawable.avatar3);

        // ✅ Set online status color
        if (user.isOnline()) {
            holder.userStatus.setText("Online");
            holder.userStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.onlineGreen, null));
        } else {
            holder.userStatus.setText("Offline");
            holder.userStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.offlineGray, null));
        }

        holder.itemView.setOnClickListener(v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView username, userStatus;
        ImageView profileImage;

        public UserViewHolder(View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.userName);
            userStatus = itemView.findViewById(R.id.userStatus);
            profileImage = itemView.findViewById(R.id.userProfileImage);
        }
    }

    public interface OnUserClickListener {
        void onUserClick(UserModel user);
    }
}
