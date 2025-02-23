package com.example.protegotinyever.tt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.R;
import com.example.protegotinyever.mode.MessageModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private final List<MessageModel> messageList;
    private final String currentUser;
    private final Context context;
    private final OnFileClickListener fileClickListener;
    private int rea = 1;

    public MessageAdapter(List<MessageModel> messageList, String currentUser, Context context, OnFileClickListener fileClickListener) {
        this.messageList = messageList;
        this.currentUser = currentUser;
        this.context = context;
        this.fileClickListener = fileClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        String sender = messageList.get(position).getSender();
        return (sender != null && sender.equals(currentUser)) ? 1 : 0;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                (viewType == 1) ? R.layout.message_received : R.layout.message_sent, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel message = messageList.get(position);
        String messageText = message.getText();

        holder.messageText.setVisibility(View.VISIBLE);
        holder.messageImage.setVisibility(View.GONE);

        if (messageText.startsWith("Received file:") && messageText.contains(" at ")) {
            String[] parts = messageText.split(" at ");
            String fileName = parts[0].replace("Received file: ", "").trim();
            String filePath = parts[1].trim();
            File file = new File(filePath);

            if (file.exists() && fileName.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp)")) {
                // Display image inline
                Bitmap bitmap = BitmapFactory.decodeFile(filePath);
                if (bitmap != null) {
                    holder.messageImage.setImageBitmap(bitmap);
                    holder.messageImage.setVisibility(View.VISIBLE);
                    holder.messageText.setVisibility(View.GONE);
                } else {
                    holder.messageText.setText(fileName);
                    holder.messageText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_light));
                    holder.itemView.setOnClickListener(v -> fileClickListener.onFileClick(filePath));
                }
            } else {
                // Non-image file
                holder.messageText.setText(fileName);
                holder.messageText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_light));
                holder.itemView.setOnClickListener(v -> fileClickListener.onFileClick(filePath));
            }
        } else if (messageText.startsWith("Sent file:")) {
            String fileName = messageText.replace("Sent file: ", "").trim();
            holder.messageText.setText(fileName);
            holder.messageText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_light));
            // Sent files aren't clickable unless stored locally with path
            holder.itemView.setOnClickListener(null);
        } else {
            holder.messageText.setText(messageText);
            holder.messageText.setTextColor(context.getResources().getColor(android.R.color.black));
            holder.itemView.setOnClickListener(null);
        }

        holder.itemView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;

        long timestamp = (message.getTimestamp() > 0) ? message.getTimestamp() : System.currentTimeMillis();
        String time = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date(timestamp));
        holder.messageTime.setText(time);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, messageTime;
        ImageView messageImage;

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageTime = itemView.findViewById(R.id.messageTime);
            messageImage = itemView.findViewById(R.id.messageImage);
        }
    }

    public void addMessage(MessageModel message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }

    public interface OnFileClickListener {
        void onFileClick(String filePath);
    }
}