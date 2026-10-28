-- V4: Add Group and Splitwise expense sharing tables

-- 1. Add profile fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS name VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

-- 2. Expense Groups table
CREATE TABLE IF NOT EXISTS expense_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    created_by_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    archived_at TIMESTAMP,
    CONSTRAINT fk_group_created_by FOREIGN KEY (created_by_id) REFERENCES users(id)
);

-- 3. Group Members table
CREATE TABLE IF NOT EXISTS group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_member_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id)
);

-- 4. Group Invitations table
CREATE TABLE IF NOT EXISTS group_invitations (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    invited_phone_number VARCHAR(20) NOT NULL,
    invited_user_id UUID,
    invited_by_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_invitation_group FOREIGN KEY (group_id) REFERENCES expense_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_invitation_user FOREIGN KEY (invited_user_id) REFERENCES users(id),
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by_id) REFERENCES users(id)
);

-- 5. Extend existing expenses table
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES expense_groups(id);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS currency VARCHAR(10) DEFAULT 'INR';
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_by_id UUID REFERENCES users(id);
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS split_type VARCHAR(20) DEFAULT 'EQUAL';
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 6. Expense Splits table
CREATE TABLE IF NOT EXISTS expense_splits (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL,
    user_id UUID NOT NULL,
    share_amount DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_split_expense FOREIGN KEY (expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
    CONSTRAINT fk_split_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_expense_split_user UNIQUE (expense_id, user_id)
);

-- 7. Settlements table
CREATE TABLE IF NOT EXISTS settlements (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    from_user_id UUID NOT NULL,
    to_user_id UUID NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(255),
    idempotency_key VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    CONSTRAINT fk_settlement_group FOREIGN KEY (group_id) REFERENCES expense_groups(id),
    CONSTRAINT fk_settlement_from_user FOREIGN KEY (from_user_id) REFERENCES users(id),
    CONSTRAINT fk_settlement_to_user FOREIGN KEY (to_user_id) REFERENCES users(id)
);

-- Indexes for optimal performance
CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_id);
CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_invitations_phone ON group_invitations(invited_phone_number);
CREATE INDEX IF NOT EXISTS idx_group_invitations_user ON group_invitations(invited_user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses(group_id);
CREATE INDEX IF NOT EXISTS idx_expense_splits_expense ON expense_splits(expense_id);
CREATE INDEX IF NOT EXISTS idx_expense_splits_user ON expense_splits(user_id);
CREATE INDEX IF NOT EXISTS idx_settlements_group ON settlements(group_id);
