package com.azcord.dto;

import com.azcord.models.MessageType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageDTO {
    private Long id;
    private UserSimpleDTO sender;
    private String content;
    private MessageType messageType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean edited;
    private Long channelId;
    private Long directMessageChatId;
    private List<AttachmentDTO> attachments;
    private Map<String, Integer> reactionCounts;
    private Map<String, Set<UserSimpleDTO>> reactionsByEmoji;
    private Long parentMessageId;
    private ParentMessageInfoDTO repliedTo;

    public MessageDTO() {}

    public MessageDTO(Long id, UserSimpleDTO sender, String content, MessageType messageType, LocalDateTime createdAt,
                      LocalDateTime updatedAt, boolean edited, Long channelId, Long directMessageChatId,
                      List<AttachmentDTO> attachments, Map<String, Integer> reactionCounts,
                      Map<String, Set<UserSimpleDTO>> reactionsByEmoji, Long parentMessageId) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.messageType = messageType;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.edited = edited;
        this.channelId = channelId;
        this.directMessageChatId = directMessageChatId;
        this.attachments = attachments;
        this.reactionCounts = reactionCounts;
        this.reactionsByEmoji = reactionsByEmoji;
        this.parentMessageId = parentMessageId;
    }

    public ParentMessageInfoDTO getRepliedTo() {
        return repliedTo;
    }
    public void setRepliedTo(ParentMessageInfoDTO repliedTo) {
        this.repliedTo = repliedTo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserSimpleDTO getSender() {
        return sender;
    }

    public void setSender(UserSimpleDTO sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public Long getDirectMessageChatId() {
        return directMessageChatId;
    }

    public void setDirectMessageChatId(Long directMessageChatId) {
        this.directMessageChatId = directMessageChatId;
    }

    public List<AttachmentDTO> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentDTO> attachments) {
        this.attachments = attachments;
    }

    public Map<String, Integer> getReactionCounts() {
        return reactionCounts;
    }

    public void setReactionCounts(Map<String, Integer> reactionCounts) {
        this.reactionCounts = reactionCounts;
    }

    public Map<String, Set<UserSimpleDTO>> getReactionsByEmoji() {
        return reactionsByEmoji;
    }

    public void setReactionsByEmoji(Map<String, Set<UserSimpleDTO>> reactionsByEmoji) {
        this.reactionsByEmoji = reactionsByEmoji;
    }

    public Long getParentMessageId() {
        return parentMessageId;
    }

    public void setParentMessageId(Long parentMessageId) {
        this.parentMessageId = parentMessageId;
    }
}