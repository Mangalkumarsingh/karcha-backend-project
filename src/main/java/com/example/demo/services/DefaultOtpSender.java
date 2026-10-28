package com.example.demo.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(DefaultOtpSender.class);

    @Override
    public void send(String phoneNumber, String otp) {
        log.info("Sending OTP {} to phone number {}", otp, phoneNumber);
    }
}
