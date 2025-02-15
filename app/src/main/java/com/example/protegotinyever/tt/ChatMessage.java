package com.example.protegotinyever.tt;

public class ChatMessage {
    private String sender;
    private String receiver;
    private String message;

    public ChatMessage() { }

    public ChatMessage(String sender, String receiver, String message) {
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
    }

    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getMessage() { return message; }
}
