package com.azcord.models;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

@Entity
@IdClass(FriendPair.class)
public class Friendship {
    @Id 
    @Column(name="user1_id") 
    private Long user1;
    
    @Id 
    @Column(name="user2_id") 
    private Long user2;
    
    private Instant since = Instant.now();

    public Long getUser1() {
        return user1;
    }

    public void setUser1(Long user1) {
        this.user1 = user1;
    }

    public Long getUser2() {
        return user2;
    }

    public void setUser2(Long user2) {
        this.user2 = user2;
    }

    public Instant getSince() {
        return since;
    }

    public void setSince(Instant since) {
        this.since = since;
    }
} 