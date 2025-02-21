package com.example.protegotinyever.adapt;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

@Entity(tableName = "messages")
public class MessageEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String sender;
    private String message;
    private long timestamp;
    private String peerUsername;

    @Ignore
    public MessageEntity() {
        // Required empty constructor for Room
    }

    public MessageEntity(String sender, String message, long timestamp, String peerUsername) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
        this.peerUsername = peerUsername;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getPeerUsername() {
        return peerUsername;
    }

    public void setPeerUsername(String peerUsername) {
        this.peerUsername = peerUsername;
    }
}
