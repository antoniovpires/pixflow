ALTER TABLE transfers DROP CONSTRAINT transfers_idempotency_key_key;
ALTER TABLE transfers
    ADD CONSTRAINT uq_transfers_source_account_idempotency_key UNIQUE (source_account_id, idempotency_key);
