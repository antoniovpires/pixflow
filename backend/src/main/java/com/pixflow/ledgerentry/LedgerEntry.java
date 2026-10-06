package com.pixflow.ledgerentry;

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
import com.pixflow.account.Account;
import com.pixflow.transfer.Transfer;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Getter;

@Entity
@Table(name = "ledger_entries")
@Getter
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 20)
    private Direction direction;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public LedgerEntry(Transfer transfer, Account account, BigDecimal amount, Direction direction) {
        this.transfer = transfer;
        this.account = account;
        this.amount = amount;
        this.direction = direction;
    }

    public LedgerEntry() {}
}