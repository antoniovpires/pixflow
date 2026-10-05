CREATE TABLE pixkeys (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    key_value VARCHAR(255) UNIQUE NOT NULL,
    key_type VARCHAR(16) NOT NULL CHECK (key_type IN ('EMAIL', 'PHONE', 'CPF')),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id) REFERENCES accounts(id)
);