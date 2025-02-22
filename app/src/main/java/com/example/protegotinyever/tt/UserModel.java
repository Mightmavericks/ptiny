package com.example.protegotinyever.tt;

public class UserModel {
    private String username;
    private String phone;
    private boolean isOnline;
    private int rea = 1;

    // Default constructor (important for Firebase!)
    public UserModel() {
    }

    // Constructor
    public UserModel(String username, String phone, boolean isOnline) {
        this.username = username;
        this.phone = phone;
        this.isOnline = isOnline;
    }

    public UserModel(String username, String phone) {
        this.username = username;
        this.phone = phone;
    }

    // Getters & Setters (needed for Firebase to map data)
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
