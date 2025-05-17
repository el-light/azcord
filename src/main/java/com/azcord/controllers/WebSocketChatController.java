package com.azcord.controllers;

import com.azcord.dto.*;
import com.azcord.exceptions.ForbiddenAccessException;
import com.azcord.exceptions.InvalidMessageException;
import com.azcord.exceptions.MessageNotFoundException;
import com.azcord.services.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal; // Standard Principal

@Controller
public class WebSocketChatController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketChatController.class);

    @Autowired
    private MessageService messageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;


    // Handles sending a message to a channel or DM
    @MessageMapping("/chat.sendMessage")
    // No @SendTo here, service will broadcast to appropriate topic
    public void sendMessage(@Payload SendMessageDTO sendMessageDTO, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Attempt to send message from unauthenticated WebSocket session. SID: {}", headerAccessor.getSessionId());
            // Optionally send error back to user's private queue
             sendErrorMessageToUser(headerAccessor.getSessionId(), "Authentication required to send messages.");
            return;
        }
        String username = principal.getName();
        logger.info("Received message from {}: channelId={}, dmChatId={}, content='{}'",
                username, sendMessageDTO.getChannelId(), sendMessageDTO.getDirectMessageChatId(), sendMessageDTO.getContent());
        try {
            messageService.sendMessage(sendMessageDTO, username);
        } catch (Exception e) {
            logger.error("Error sending message from user {}: {}", username, e.getMessage());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Error sending message: " + e.getMessage());
        }
    }

    // Handles editing a message
    @MessageMapping("/chat.editMessage")
    public void editMessage(@Payload MessageEditRequestPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
         if (principal == null || principal.getName() == null) {
            logger.warn("Attempt to edit message from unauthenticated WebSocket session. SID: {}", headerAccessor.getSessionId());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Authentication required to edit messages.");
            return;
        }
        String username = principal.getName();
        logger.info("Received edit request from {} for message {}: newContent='{}'",
                username, payload.getMessageId(), payload.getEditMessageDTO().getContent());
        try {
            messageService.editMessage(payload.getMessageId(), payload.getEditMessageDTO(), username);
        } catch (Exception e) {
            logger.error("Error editing message {} by user {}: {}", payload.getMessageId(), username, e.getMessage());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Error editing message: " + e.getMessage());
        }
    }
    // Helper DTO for edit payload
    public static class MessageEditRequestPayload {
        private Long messageId;
        private EditMessageDTO editMessageDTO;
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public EditMessageDTO getEditMessageDTO() { return editMessageDTO; }
        public void setEditMessageDTO(EditMessageDTO editMessageDTO) { this.editMessageDTO = editMessageDTO; }
    }


    // Handles deleting a message
    @MessageMapping("/chat.deleteMessage")
    public void deleteMessage(@Payload MessageDeleteRequestPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
         if (principal == null || principal.getName() == null) {
            logger.warn("Attempt to delete message from unauthenticated WebSocket session. SID: {}", headerAccessor.getSessionId());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Authentication required to delete messages.");
            return;
        }
        String username = principal.getName();
        logger.info("Received delete request from {} for message {}", username, payload.getMessageId());
        try {
            messageService.deleteMessage(payload.getMessageId(), username);
        } catch (Exception e) {
            logger.error("Error deleting message {} by user {}: {}", payload.getMessageId(), username, e.getMessage());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Error deleting message: " + e.getMessage());
        }
    }
    // Helper DTO for delete payload
    public static class MessageDeleteRequestPayload {
        private Long messageId;
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
    }


    // Handles adding a reaction
    @MessageMapping("/chat.addReaction")
    public void addReaction(@Payload ReactionAddRequestPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Attempt to add reaction from unauthenticated WebSocket session. SID: {}", headerAccessor.getSessionId());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Authentication required to add reactions.");
            return;
        }
        String username = principal.getName();
        logger.info("Received add reaction request from {} for message {} with emoji '{}'",
                username, payload.getMessageId(), payload.getReactionRequestDTO().getEmojiUnicode());
        try {
            messageService.addReaction(payload.getMessageId(), username, payload.getReactionRequestDTO().getEmojiUnicode());
        } catch (Exception e) {
            logger.error("Error adding reaction by user {}: {}", username, e.getMessage());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Error adding reaction: " + e.getMessage());
        }
    }
     // Helper DTO for reaction payload
    public static class ReactionAddRequestPayload {
        private Long messageId;
        private ReactionRequestDTO reactionRequestDTO;
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public ReactionRequestDTO getReactionRequestDTO() { return reactionRequestDTO; }
        public void setReactionRequestDTO(ReactionRequestDTO reactionRequestDTO) { this.reactionRequestDTO = reactionRequestDTO; }
    }


    // Handles removing a reaction
    @MessageMapping("/chat.removeReaction")
    public void removeReaction(@Payload ReactionRemoveRequestPayload payload, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
         if (principal == null || principal.getName() == null) {
            logger.warn("Attempt to remove reaction from unauthenticated WebSocket session. SID: {}", headerAccessor.getSessionId());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Authentication required to remove reactions.");
            return;
        }
        String username = principal.getName();
         logger.info("Received remove reaction request from {} for message {} with emoji '{}'",
                username, payload.getMessageId(), payload.getReactionRequestDTO().getEmojiUnicode());
        try {
            messageService.removeReaction(payload.getMessageId(), username, payload.getReactionRequestDTO().getEmojiUnicode());
        } catch (Exception e) {
            logger.error("Error removing reaction by user {}: {}", username, e.getMessage());
            sendErrorMessageToUser(headerAccessor.getSessionId(), "Error removing reaction: " + e.getMessage());
        }
    }
    // Helper DTO for reaction payload
    public static class ReactionRemoveRequestPayload { // Same as Add for now
        private Long messageId;
        private ReactionRequestDTO reactionRequestDTO;
        public Long getMessageId() { return messageId; }
        public void setMessageId(Long messageId) { this.messageId = messageId; }
        public ReactionRequestDTO getReactionRequestDTO() { return reactionRequestDTO; }
        public void setReactionRequestDTO(ReactionRequestDTO reactionRequestDTO) { this.reactionRequestDTO = reactionRequestDTO; }
    }


    @MessageExceptionHandler
    @SendToUser("/queue/errors") // User-specific error queue
    public WebSocketErrorMessageDTO handleException(Throwable exception, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        String username = (principal != null) ? principal.getName() : "UnknownUser (SID: " + headerAccessor.getSessionId() + ")";
        logger.error("Error processing WebSocket message for user {}: {}", username, exception.getMessage(), exception);
        
        String errorMessage = "An unexpected error occurred.";
        if (exception instanceof IllegalArgumentException || exception instanceof InvalidMessageException ||
            exception instanceof MessageNotFoundException || exception instanceof ForbiddenAccessException) {
            errorMessage = exception.getMessage();
        }
        // You can customize the error message based on exception type
        return new WebSocketErrorMessageDTO("Processing Error", errorMessage);
    }

    private void sendErrorMessageToUser(String sessionId, String errorDetails) {
        if (sessionId == null) return;
        // Note: This requires the user to be subscribed to their specific error queue.

        messagingTemplate.convertAndSendToUser(
            sessionId, // This should ideally be the username (Principal.getName())
            "/queue/errors",
            new WebSocketErrorMessageDTO("Error", errorDetails),
            createHeaders(sessionId) // Create headers with session ID if needed
        );
    }
    private org.springframework.messaging.MessageHeaders createHeaders(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }

}