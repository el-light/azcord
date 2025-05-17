package com.azcord.models;

import java.io.Serializable;
import java.util.Objects;

public class FriendPair implements Serializable {
    private Long user1;
    private Long user2;


    public FriendPair() {
    }

    public FriendPair(Long user1, Long user2) {
        this.user1 = user1;
        this.user2 = user2;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FriendPair that = (FriendPair) o;
        return Objects.equals(user1, that.user1) && Objects.equals(user2, that.user2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user1, user2);
    }

    @Override
    public String toString() {
        return "FriendPair{" +
                "user1=" + user1 +
                ", user2=" + user2 +
                '}';
    }
} 