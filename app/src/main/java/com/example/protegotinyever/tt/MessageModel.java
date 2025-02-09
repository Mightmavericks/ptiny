package com.example.protegotinyever.tt;

public class MessageModel {
    private String sender;
    private String text;
    private long timestamp;

    public MessageModel() {
        // Default constructor for Firebase
    }

    public MessageModel(String sender, String text, long timestamp) {
        this.sender = sender;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
