package com.azcord.repositories;

import com.azcord.models.DirectMessageChat;
import com.azcord.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface DirectMessageChatRepository extends JpaRepository<DirectMessageChat, Long> {

    // Find chats a user is part of, ordered by last activity
    @Query("SELECT dmc FROM DirectMessageChat dmc JOIN dmc.participants p WHERE p.id = :userId ORDER BY dmc.lastActivityAt DESC")
    List<DirectMessageChat> findByParticipantIdOrderByLastActivityDesc(@Param("userId") Long userId);

    // Find a 1-on-1 DM chat between two specific users
    // This query is a bit complex because we need to match exactly two participants regardless of order.
    @Query("SELECT c FROM DirectMessageChat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.chatType = com.azcord.models.ChatType.DIRECT_MESSAGE " +
           "AND p1.id = :userId1 AND p2.id = :userId2 " +
           "AND (SELECT COUNT(p) FROM c.participants p) = 2")
    Optional<DirectMessageChat> findDirectMessageChatByUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    
    // Find group chats by name (if you implement search for group DMs)
    List<DirectMessageChat> findByChatTypeAndNameContainingIgnoreCaseOrderByLastActivityAtDesc(com.azcord.models.ChatType chatType, String name);

    // Check if a user is a participant in a specific chat
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
           "FROM DirectMessageChat c JOIN c.participants p " +
           "WHERE c.id = :chatId AND p.id = :userId")
    boolean isUserParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);
}