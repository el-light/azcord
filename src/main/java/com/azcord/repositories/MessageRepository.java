package com.azcord.repositories;

import com.azcord.models.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Fetch messages for a specific channel with pagination
    Page<Message> findByChannelIdOrderByCreatedAtDesc(Long channelId, Pageable pageable);
    Page<Message> findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(Long channelId, java.time.LocalDateTime beforeTimestamp, Pageable pageable);


    // Fetch messages for a specific direct message chat with pagination
    Page<Message> findByDirectMessageChatIdOrderByCreatedAtDesc(Long directMessageChatId, Pageable pageable);
    Page<Message> findByDirectMessageChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(Long directMessageChatId, java.time.LocalDateTime beforeTimestamp, Pageable pageable);



    // Get the last message for a DM chat (useful for chat list previews)
    Optional<Message> findTopByDirectMessageChatIdOrderByCreatedAtDesc(Long directMessageChatId);

    // Example of a more complex query if needed, e.g., searching messages
    @Query("SELECT m FROM Message m WHERE (m.channel.id = :channelId OR m.directMessageChat.id = :dmChatId) AND LOWER(m.content) LIKE LOWER(concat('%', :searchTerm, '%')) ORDER BY m.createdAt DESC")
    Page<Message> searchMessages(@Param("channelId") Long channelId, @Param("dmChatId") Long dmChatId, @Param("searchTerm") String searchTerm, Pageable pageable);
}