package com.example.protegotinyever.tt;

public class DataModel {
    private String type;  // Offer, Answer, ICE, Chat
    private String sender;
    private String target;
    private String data;

    public DataModel() {}  // Required for Firebase

    public DataModel(String type, String sender, String target, String data) {
        this.type = type;
        this.sender = sender;
        this.target = target;
        this.data = data;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
