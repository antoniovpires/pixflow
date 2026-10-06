package com.pixflow.pixkey;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.pixflow.account.Account;

import lombok.Getter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;

@Entity
@Table(name = "pixkeys")
public class PixKey {
    @Id
    @Getter
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Getter
    @Column(name = "key_value", unique = true)
    private String keyValue;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false,length = 16)
    private KeyType keyType;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PixKey(Account account, String keyValue, KeyType keyType) {
        this.account = account;
        this.keyValue = normalizeKeyValue(keyValue, keyType);
        this.keyType = keyType;
    }

    protected PixKey() {}

    private String normalizeKeyValue(String keyValue, KeyType keyType) {
        if (keyType == KeyType.EMAIL) {
            return keyValue.toLowerCase();
        }
        
        return keyValue.replaceAll("[^0-9]", "");
    }
}