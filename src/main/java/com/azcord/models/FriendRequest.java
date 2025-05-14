package com.azcord.models;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(uniqueConstraints=@UniqueConstraint(columnNames={"sender_id","receiver_id"}))
public class FriendRequest {
    @Id 
    @GeneratedValue 
    private UUID id;
    
    @ManyToOne(fetch=LAZY) 
    private User sender;
    
    @ManyToOne(fetch=LAZY) 
    private User receiver;
    
    @Enumerated(EnumType.STRING) 
    private Status status = Status.PENDING;
    
    private Instant createdAt = Instant.now();
    
    public enum Status { PENDING, ACCEPTED, DECLINED }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
} 