package com.example.protegotinyever.tt;

public class UserModel {
    private String phoneNumber;
    private String username;
    private boolean isOnline;

    // Default constructor (important for Firebase!)
    public UserModel() {
    }

    // Constructor
    public UserModel(String phoneNumber, String username, boolean isOnline) {
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.isOnline = isOnline;
    }

    public UserModel(String username, String phone) {
        this.phoneNumber = phone;
        this.username = username;
    }

    // Getters & Setters (needed for Firebase to map data)
    public String getPhone() {
        return phoneNumber;
    }

    public void setPhone(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }
}
