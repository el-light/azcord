package com.azcord.repositories;

import com.azcord.models.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {
    Optional<MessageReaction> findByMessageIdAndUserIdAndEmojiUnicode(Long messageId, Long userId, String emojiUnicode);
    List<MessageReaction> findAllByMessageId(Long messageId);
    List<MessageReaction> findAllByMessageIdAndEmojiUnicode(Long messageId, String emojiUnicode);
    void deleteByMessageIdAndUserIdAndEmojiUnicode(Long messageId, Long userId, String emojiUnicode);
    long countByMessageIdAndEmojiUnicode(Long messageId, String emojiUnicode);
}