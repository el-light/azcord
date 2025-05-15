package com.azcord.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.azcord.models.FriendPair;
import com.azcord.models.Friendship;

import java.util.List;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, FriendPair> {
    @Query("""
       select case when count(f)>0 then true else false end
       from Friendship f
       where (f.user1=:a and f.user2=:b) or (f.user1=:b and f.user2=:a)
    """)
    boolean areFriends(Long a, Long b);
    
    List<Friendship> findByUser1OrUser2(Long u1, Long u2);
} 