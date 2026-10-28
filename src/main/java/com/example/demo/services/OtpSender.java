package com.example.demo.services;

public interface OtpSender {
    void send(String phoneNumber, String otp);
}