-- 1. Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Expenses Table
CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Yahan DB level Cascading lag rahi hai
    CONSTRAINT fk_expense_user FOREIGN KEY (user_id) 
    REFERENCES users(id) ON DELETE CASCADE
);