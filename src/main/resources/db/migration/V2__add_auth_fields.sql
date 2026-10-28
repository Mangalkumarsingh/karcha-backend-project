-- V2: Add OTP fields and Google email support to users table

-- Add OTP columns (for phone number login)
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expiry_time TIMESTAMP;

-- Add email column (for Google login)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255) UNIQUE;

-- Make phone_number optional (user can login via phone OR google)
ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
