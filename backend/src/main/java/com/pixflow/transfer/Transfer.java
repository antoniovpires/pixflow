package com.pixflow.transfer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Column;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import com.pixflow.pixkey.PixKey;
import com.pixflow.account.Account;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Getter;

@Entity
@Table(name = "transfers")
@Getter
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_account_id")
    private Account targetAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pix_key_id")
    private PixKey pixKey;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Transfer(Account sourceAccount, Account targetAccount, PixKey pixKey, BigDecimal amount, String idempotencyKey) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        
        this.sourceAccount = sourceAccount;
        this.targetAccount = targetAccount;
        this.pixKey = pixKey;
        this.amount = amount;
        this.status = Status.PENDING;
        this.idempotencyKey = idempotencyKey;
    }

    public Transfer() {}

    public void markPending() {
        this.status = Status.PENDING;
    }

    public void markCompleted() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Transfer is not pending");
        }
        this.status = Status.SUCCESS;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public void markCancelled() {
        this.status = Status.CANCELLED;
    }

    public void markReversed() {
        this.status = Status.REVERSED;
    }
}