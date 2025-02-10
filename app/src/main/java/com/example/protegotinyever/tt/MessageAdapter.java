package com.example.protegotinyever.tt;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.protegotinyever.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    private final List<MessageModel> messageList;
    private final String currentUser;

    public MessageAdapter(List<MessageModel> messageList, String currentUser) {
        this.messageList = messageList;
        this.currentUser = currentUser;
    }

    @Override
    public int getItemViewType(int position) {
        // ✅ Prevent NullPointerException
        String sender = messageList.get(position).getSender();
        return (sender != null && sender.equals(currentUser)) ? 1 : 0;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                (viewType == 1) ? R.layout.message_sent : R.layout.message_received, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel message = messageList.get(position);
        holder.messageText.setText(message.getText());

        // ✅ Ensure timestamp is valid, then format it
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

        public MessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageTime = itemView.findViewById(R.id.messageTime);
        }
    }

    // ✅ Efficiently add new messages
    public void addMessage(MessageModel message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }
}
