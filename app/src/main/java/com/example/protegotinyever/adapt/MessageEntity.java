package com.example.protegotinyever.adapt;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class MessageEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String sender;
    private String message;
    private long timestamp;
    private String peerUsername; // Stores messages per peer

    public MessageEntity(String sender, String message, long timestamp, String peerUsername) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
        this.peerUsername = peerUsername;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public String getPeerUsername() { return peerUsername; }
}
