package com.example.protegotinyever.util;

public class Contact {
    private String name;
    private String phone;
    private int rea = 1;

    public Contact(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}