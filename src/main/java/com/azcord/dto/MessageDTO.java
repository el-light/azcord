package com.azcord.dto;

import com.azcord.models.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private UserSimpleDTO sender;
    private String content;
    private MessageType messageType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean edited;
    private Long channelId; // Null if DM
    private Long directMessageChatId; // Null if channel message
    private List<AttachmentDTO> attachments;
    // Simplified reactions: Map of emoji to count, and a list of users per emoji for detail
    private Map<String, Integer> reactionCounts; // e.g., {"👍": 5, "❤️": 2}
    private Map<String, Set<UserSimpleDTO>> reactionsByEmoji; // Detailed list of users per emoji
    private Long parentMessageId; // For replies
    // We can also include a simplified parentMessageDTO if needed for replies
}