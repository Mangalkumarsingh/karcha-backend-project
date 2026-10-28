package com.example.demo.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PhoneUtil {

    private PhoneUtil() {}

    public static String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        }

        String cleaned = rawPhone.replaceAll("[\\s\\-\\(\\)]", "");

        if (!cleaned.startsWith("+")) {
            if (cleaned.length() == 10 && cleaned.matches("\\d{10}")) {
                cleaned = "+91" + cleaned;
            } else if (cleaned.length() == 12 && cleaned.startsWith("91") && cleaned.matches("\\d{12}")) {
                cleaned = "+" + cleaned;
            } else {
                cleaned = "+" + cleaned;
            }
        }

        if (!cleaned.matches("^\\+[1-9]\\d{6,14}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid phone number format. Please provide a valid E.164 phone number (e.g., +919876543210)"
            );
        }

        return cleaned;
    }
}
