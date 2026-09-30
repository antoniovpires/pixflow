package com.pixflow.user;

import lombok.Getter;
import lombok.Setter;
import java.util.UUID;
import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")

public class User {
    @Id
    @Getter
    @UuidGenerator
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Getter
    @Setter
    @Column(name = "name")
    private String name;

    @Getter
    @Setter
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Getter
    @Setter
    @Column(name = "password_hash")
    private String passwordHash;

    @Getter
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Getter
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}