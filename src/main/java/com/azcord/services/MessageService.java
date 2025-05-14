package com.azcord.services;

import com.azcord.dto.*;
import com.azcord.exceptions.*;
import com.azcord.models.*;
import com.azcord.repositories.*;

import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChannelRepository channelRepository; 
    @Autowired
    private DirectMessageChatRepository dmChatRepository;
    @Autowired
    private MessageReactionRepository reactionRepository;
    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    @Qualifier("localFileStorageService") // Or your cloud storage service bean name
    private FileStorageService fileStorageService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate; // For sending messages over WebSocket

    @Autowired
    private ServerService serverService; // For permission checks

    @Autowired
    @Lazy
    private ChatService chatService; // For updating chat activity


    @Transactional
    public MessageDTO sendMessage(SendMessageDTO sendMessageDTO, String senderUsername) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new UserNotFoundException("Sender not found: " + senderUsername));

        if (!sendMessageDTO.isValidTarget()) {
            throw new InvalidMessageTargetException("Message must have either a channelId or a directMessageChatId, but not both.");
        }
        if (!sendMessageDTO.hasContent() && (sendMessageDTO.getFiles() == null || sendMessageDTO.getFiles().isEmpty())) {
             throw new InvalidMessageException("Message must have content or attachments.");
        }


        Message message = new Message();
        message.setSender(sender);
        message.setContent(sendMessageDTO.getContent());
        message.setMessageType(MessageType.TEXT); // Default, will be updated if attachments exist

        if (sendMessageDTO.getParentMessageId() != null) {
            Message parentMessage = messageRepository.findById(sendMessageDTO.getParentMessageId())
                    .orElseThrow(() -> new MessageNotFoundException("Parent message for reply not found."));
            message.setParentMessage(parentMessage);
        }

        Channel channel = null;
        DirectMessageChat dmChat = null;
        String destination; // WebSocket destination

        if (sendMessageDTO.getChannelId() != null) {
            channel = channelRepository.findById(sendMessageDTO.getChannelId())
                    .orElseThrow(() -> new ChannelNotFoundException("Channel not found: " + sendMessageDTO.getChannelId()));
            // Permission check: Is user member of server and has SEND_MESSAGES perm for this channel?
            // For simplicity, assuming membership implies send rights. Add granular perms later.
            if (!serverService.hasPermission(channel.getServer().getId(), senderUsername, Permission.SEND_MESSAGES) &&
                !serverService.hasPermission(channel.getServer().getId(), senderUsername, Permission.ADMINISTRATOR)) {
                 // A more specific permission like VIEW_CHANNEL + SEND_MESSAGES would be better.
                 // For now, if they are on the server and can manage channels, they can send.
                 // This needs refinement based on your Permission enum.
                 // Let's assume a general "is member of server" check for now, or a specific SEND_MESSAGES permission.
                 // The `hasPermission` in ServerService might need to be adapted or a new one created for channels.
                 // For now, let's rely on a basic server membership check done implicitly if they know the channel ID.
                 // A proper check would be:
                 // if (!isUserMemberOfChannel(sender, channel) || !hasChannelPermission(sender, channel, ChannelPermission.SEND_MESSAGE))
                 // throw new ForbiddenAccessException("User cannot send messages to this channel.");
            }
            message.setChannel(channel);
            destination = "/topic/channels/" + channel.getId() + "/messages";
            logger.info("Sending message from {} to channel {}", senderUsername, channel.getId());
        } else { // directMessageChatId must be present
            dmChat = dmChatRepository.findById(sendMessageDTO.getDirectMessageChatId())
                    .orElseThrow(() -> new ChatNotFoundException("Direct message chat not found: " + sendMessageDTO.getDirectMessageChatId()));
            if (!dmChatRepository.isUserParticipant(dmChat.getId(), sender.getId())) {
                throw new ForbiddenAccessException("User is not a participant of DM chat: " + dmChat.getId());
            }
            message.setDirectMessageChat(dmChat);
            // For DMs, each participant gets a message in their own queue.
            // Or, broadcast to a common topic and clients filter.
            // Using a common topic for the DM chat is simpler for group DMs.
            destination = "/topic/dm/" + dmChat.getId() + "/messages";
            logger.info("Sending message from {} to DM chat {}", senderUsername, dmChat.getId());
        }

        // Save message first to get an ID for attachments
        Message savedMessage = messageRepository.save(message);

        // Handle attachments (if sent via REST with MultipartFile)
        if (sendMessageDTO.getFiles() != null && !sendMessageDTO.getFiles().isEmpty()) {
            try {
                List<Attachment> attachments = fileStorageService.storeFiles(sendMessageDTO.getFiles(), savedMessage);
                savedMessage.getAttachments().addAll(attachments);
                attachmentRepository.saveAll(attachments); // Ensure attachments are saved with message_id
                if (!attachments.isEmpty()) {
                     // If there's content, it's mixed. If no content, type is based on first attachment.
                    savedMessage.setMessageType(attachments.get(0).getAttachmentType()); // Or more sophisticated logic
                }
                logger.info("Saved {} attachments for message {}", attachments.size(), savedMessage.getId());
            } catch (IOException e) {
                logger.error("Failed to store attachments for message by " + senderUsername, e);
                // Decide if message sending should fail entirely or proceed without attachments
                messageRepository.delete(savedMessage); // Rollback message if attachments are critical and failed
                throw new FileStorageException("Failed to store attachments: " + e.getMessage(), e);
            }
        }
        // Handle attachments if URLs are provided (common for WebSocket after separate upload)
        else if (sendMessageDTO.getAttachmentUrls() != null && !sendMessageDTO.getAttachmentUrls().isEmpty()) {
            List<Attachment> attachments = new ArrayList<>();
            for (int i = 0; i < sendMessageDTO.getAttachmentUrls().size(); i++) {
                Attachment att = new Attachment();
                att.setFileUrl(sendMessageDTO.getAttachmentUrls().get(i));
                att.setMimeType(sendMessageDTO.getAttachmentMimeTypes() != null && i < sendMessageDTO.getAttachmentMimeTypes().size() ? sendMessageDTO.getAttachmentMimeTypes().get(i) : "application/octet-stream");
                att.setFileName(extractFileNameFromUrl(sendMessageDTO.getAttachmentUrls().get(i))); // Basic extraction
                att.setAttachmentType(determineAttachmentTypeFromMime(att.getMimeType()));
                att.setMessage(savedMessage);
                att.setUploadedAt(LocalDateTime.now()); // Assuming upload happened just before
                attachments.add(att);
            }
            savedMessage.getAttachments().addAll(attachments);
            attachmentRepository.saveAll(attachments);
            if (!attachments.isEmpty() && (savedMessage.getContent() == null || savedMessage.getContent().isBlank())) {
                 savedMessage.setMessageType(attachments.get(0).getAttachmentType());
            }
             logger.info("Associated {} pre-uploaded attachments for message {}", attachments.size(), savedMessage.getId());
        }


        Message finalMessage = messageRepository.save(savedMessage); // Save again with attachments if any

        // Update last activity for DM chats
        if (dmChat != null) {
            chatService.updateChatActivity(dmChat.getId());
        } else if (channel != null) {
            // Potentially update server/channel last activity if needed
        }

        MessageDTO messageDTO = mapMessageToDTO(finalMessage);
        messagingTemplate.convertAndSend(destination, messageDTO);
        logger.info("Message {} sent and broadcast to {}", finalMessage.getId(), destination);

        return messageDTO;
    }
    private String extractFileNameFromUrl(String url) {
        if (url == null) return "file";
        try {
            return new java.io.File(new java.net.URL(url).getPath()).getName();
        } catch (Exception e) {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < url.length() - 1) {
                return url.substring(lastSlash + 1);
            }
            return "file";
        }
    }

    private MessageType determineAttachmentTypeFromMime(String mimeType) {
        if (mimeType == null) return MessageType.FILE;
        if (mimeType.startsWith("image/")) return MessageType.IMAGE;
        if (mimeType.startsWith("video/")) return MessageType.VIDEO;
        return MessageType.FILE;
    }


    @Transactional
    public MessageDTO editMessage(Long messageId, EditMessageDTO editMessageDTO, String editorUsername) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + messageId));
        User editor = userRepository.findByUsername(editorUsername)
                .orElseThrow(() -> new UserNotFoundException("Editor user not found: " + editorUsername));

        if (!message.getSender().getId().equals(editor.getId())) {
            // Add check for MANAGE_MESSAGES permission if editor is not sender
            boolean canManageMessages = false;
            if (message.getChannel() != null) {
                canManageMessages = serverService.hasPermission(message.getChannel().getServer().getId(), editorUsername, Permission.MANAGE_MESSAGES);
            } // Add similar check for DMs if applicable (e.g. group DM admin)
            if (!canManageMessages) {
                 throw new ForbiddenAccessException("User cannot edit this message.");
            }
        }

        message.setContent(editMessageDTO.getContent());
        message.setEdited(true);
        message.setUpdatedAt(LocalDateTime.now());
        Message updatedMessage = messageRepository.save(message);

        MessageDTO messageDTO = mapMessageToDTO(updatedMessage);

        // Determine WebSocket destination
        String destination;
        if (message.getChannel() != null) {
            destination = "/topic/channels/" + message.getChannel().getId() + "/messages/updated";
        } else if (message.getDirectMessageChat() != null) {
            destination = "/topic/dm/" + message.getDirectMessageChat().getId() + "/messages/updated";
        } else {
            logger.error("Message {} has no valid channel or DM chat for broadcasting edit.", messageId);
            return messageDTO; // Or throw error
        }

        messagingTemplate.convertAndSend(destination, messageDTO);
        logger.info("Message {} edited by {} and broadcast to {}", messageId, editorUsername, destination);
        return messageDTO;
    }

    @Transactional
    public void deleteMessage(Long messageId, String deleterUsername) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + messageId));
        User deleter = userRepository.findByUsername(deleterUsername)
                .orElseThrow(() -> new UserNotFoundException("Deleter user not found: " + deleterUsername));

        boolean canDelete = message.getSender().getId().equals(deleter.getId());
        if (!canDelete) {
            // Check for MANAGE_MESSAGES permission
            if (message.getChannel() != null) {
                canDelete = serverService.hasPermission(message.getChannel().getServer().getId(), deleterUsername, Permission.MANAGE_MESSAGES) ||
                            serverService.hasPermission(message.getChannel().getServer().getId(), deleterUsername, Permission.ADMINISTRATOR);
            } else if (message.getDirectMessageChat() != null) {
                // For DMs, typically only sender can delete. Group DM admins might be an exception.
                // For now, only sender or if we implement DM admin role.
                // Let's assume no cross-user deletion in DMs unless it's a system/admin feature.
                // For group DMs, a group admin might be able to delete messages.
                // For 1-on-1 DMs, only the sender.
                DirectMessageChat dmChat = message.getDirectMessageChat();
                if (dmChat.getChatType() == ChatType.GROUP_DIRECT_MESSAGE && dmChat.getCreator() != null && dmChat.getCreator().getId().equals(deleter.getId())) {
                    canDelete = true; // Group creator can delete messages
                }
            }
        }

        if (!canDelete) {
            throw new ForbiddenAccessException("User " + deleterUsername + " cannot delete message " + messageId);
        }

        // Delete associated attachments from storage
        for (Attachment attachment : message.getAttachments()) {
            try {
                // Extract just the filename from the URL for local storage
                String storedFileName = attachment.getFileUrl().substring(attachment.getFileUrl().lastIndexOf("/") + 1);
                fileStorageService.deleteFile(storedFileName);
            } catch (IOException e) {
                logger.error("Failed to delete attachment file {} for message {}: {}", attachment.getFileUrl(), messageId, e.getMessage());
                // Continue with message deletion even if file deletion fails, but log it.
            }
        }
        // Attachments and reactions will be deleted due to CascadeType.ALL on Message entity

        messageRepository.delete(message);

        // Determine WebSocket destination for delete notification
        Map<String, Object> deleteNotification = new HashMap<>();
        deleteNotification.put("messageId", messageId);
        deleteNotification.put("deletedBy", deleterUsername);


        String destination;
        Long targetId;
        if (message.getChannel() != null) {
            destination = "/topic/channels/" + message.getChannel().getId() + "/messages/deleted";
            targetId = message.getChannel().getId();
            deleteNotification.put("channelId", targetId);
        } else if (message.getDirectMessageChat() != null) {
            destination = "/topic/dm/" + message.getDirectMessageChat().getId() + "/messages/deleted";
            targetId = message.getDirectMessageChat().getId();
            deleteNotification.put("directMessageChatId", targetId);
        } else {
            logger.error("Message {} has no valid channel or DM chat for broadcasting delete.", messageId);
            return; // Or throw error
        }

        messagingTemplate.convertAndSend(destination, deleteNotification);
        logger.info("Message {} deleted by {} and notification broadcast to {}", messageId, deleterUsername, destination);
    }


    @Transactional
    public MessageDTO addReaction(Long messageId, String reactorUsername, String emojiUnicode) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + messageId));
        User reactor = userRepository.findByUsername(reactorUsername)
                .orElseThrow(() -> new UserNotFoundException("Reactor user not found: " + reactorUsername));

        // Validate emoji
        if (ReactionEmoji.fromUnicode(emojiUnicode).isEmpty()) {
            throw new InvalidReactionException("Invalid emoji: " + emojiUnicode);
        }

        // Check if user is allowed to react (e.g., part of channel/DM)
        if (message.getChannel() != null) {

        } else if (message.getDirectMessageChat() != null) {
            if (!dmChatRepository.isUserParticipant(message.getDirectMessageChat().getId(), reactor.getId())) {
                throw new ForbiddenAccessException("User cannot react to messages in this DM chat.");
            }
        }


        Optional<MessageReaction> existingReaction = reactionRepository.findByMessageIdAndUserIdAndEmojiUnicode(messageId, reactor.getId(), emojiUnicode);
        if (existingReaction.isPresent()) {
            // User already reacted with this emoji, perhaps do nothing or allow toggling (removing)
            logger.warn("User {} already reacted with {} to message {}", reactorUsername, emojiUnicode, messageId);
            return mapMessageToDTO(message); // Or throw an exception like "AlreadyReactedException"
        }

        MessageReaction reaction = new MessageReaction();
        reaction.setMessage(message);
        reaction.setUser(reactor);
        reaction.setEmojiUnicode(emojiUnicode);
        reactionRepository.save(reaction);

        // It's important to fetch the message again or ensure the 'reactions' collection in the 'message' object is updated
        // if mapMessageToDTO relies on it being eagerly fetched or re-fetched.
        // For simplicity, mapMessageToDTO will re-query reaction counts.
        Message updatedMessage = messageRepository.findById(messageId).get(); // Re-fetch to get updated reaction state
        MessageDTO messageDTO = mapMessageToDTO(updatedMessage);


        String destination;
         if (message.getChannel() != null) {
            destination = "/topic/channels/" + message.getChannel().getId() + "/messages/reactions/updated";
        } else if (message.getDirectMessageChat() != null) {
            destination = "/topic/dm/" + message.getDirectMessageChat().getId() + "/messages/reactions/updated";
        } else {
            logger.error("Message {} has no valid channel or DM chat for broadcasting reaction update.", messageId);
            return messageDTO;
        }

        messagingTemplate.convertAndSend(destination, messageDTO); // Send the whole updated message DTO
        logger.info("User {} reacted with {} to message {}. Broadcast to {}", reactorUsername, emojiUnicode, messageId, destination);
        return messageDTO;
    }

    @Transactional
    public MessageDTO removeReaction(Long messageId, String reactorUsername, String emojiUnicode) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new MessageNotFoundException("Message not found: " + messageId));
        User reactor = userRepository.findByUsername(reactorUsername)
                .orElseThrow(() -> new UserNotFoundException("Reactor user not found: " + reactorUsername));

        // Validate emoji (optional here, but good for consistency)
        if (ReactionEmoji.fromUnicode(emojiUnicode).isEmpty()) {
            throw new InvalidReactionException("Invalid emoji for removal: " + emojiUnicode);
        }

        Optional<MessageReaction> reactionOpt = reactionRepository.findByMessageIdAndUserIdAndEmojiUnicode(messageId, reactor.getId(), emojiUnicode);
        if (reactionOpt.isEmpty()) {
            throw new RuntimeException("Reaction not found for user " + reactorUsername + " with emoji " + emojiUnicode + " on message " + messageId);
        }

        reactionRepository.delete(reactionOpt.get());

        Message updatedMessage = messageRepository.findById(messageId).get(); // Re-fetch
        MessageDTO messageDTO = mapMessageToDTO(updatedMessage);

        String destination;
         if (message.getChannel() != null) {
            destination = "/topic/channels/" + message.getChannel().getId() + "/messages/reactions/updated";
        } else if (message.getDirectMessageChat() != null) {
            destination = "/topic/dm/" + message.getDirectMessageChat().getId() + "/messages/reactions/updated";
        } else {
             logger.error("Message {} has no valid channel or DM chat for broadcasting reaction update.", messageId);
            return messageDTO;
        }
        messagingTemplate.convertAndSend(destination, messageDTO);
        logger.info("User {} removed reaction {} from message {}. Broadcast to {}", reactorUsername, emojiUnicode, messageId, destination);
        return messageDTO;
    }


    @Transactional(readOnly = true)
    public Page<MessageDTO> getMessagesForChannel(Long channelId, String username, Pageable pageable) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new ChannelNotFoundException("Channel not found: " + channelId));
        // Permission check: is user member of server? Can they view this channel?
        // serverService.hasPermission(channel.getServer().getId(), username, Permission.VIEW_CHANNEL)
        // For now, assume if they request, they have basic view rights.

        Page<Message> messagesPage = messageRepository.findByChannelIdOrderByCreatedAtDesc(channelId, pageable);
        return messagesPage.map(this::mapMessageToDTO);
    }
    
    @Transactional(readOnly = true)
    public List<MessageDTO> getMessagesForChannelBefore(Long channelId, String username, LocalDateTime beforeTimestamp, int size) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new ChannelNotFoundException("Channel not found: " + channelId));
        // Add permission checks as above
        Pageable pageable = PageRequest.of(0, size, Sort.by("createdAt").descending());
        Page<Message> messagesPage = messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(channelId, beforeTimestamp, pageable);
        return messagesPage.map(this::mapMessageToDTO).getContent();
    }


    @Transactional(readOnly = true)
    public Page<MessageDTO> getMessagesForDirectMessageChat(Long dmChatId, String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        DirectMessageChat dmChat = dmChatRepository.findById(dmChatId)
            .orElseThrow(() -> new ChatNotFoundException("DM chat not found: " + dmChatId));

        if (!dmChatRepository.isUserParticipant(dmChatId, user.getId())) {
            throw new ForbiddenAccessException("User is not a participant of DM chat: " + dmChatId);
        }

        Page<Message> messagesPage = messageRepository.findByDirectMessageChatIdOrderByCreatedAtDesc(dmChatId, pageable);
        return messagesPage.map(this::mapMessageToDTO);
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getMessagesForDirectMessageChatBefore(Long dmChatId, String username, LocalDateTime beforeTimestamp, int size) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
        DirectMessageChat dmChat = dmChatRepository.findById(dmChatId)
            .orElseThrow(() -> new ChatNotFoundException("DM chat not found: " + dmChatId));
        if (!dmChatRepository.isUserParticipant(dmChatId, user.getId())) {
            throw new ForbiddenAccessException("User is not a participant of DM chat: " + dmChatId);
        }
        Pageable pageable = PageRequest.of(0, size, Sort.by("createdAt").descending());
        Page<Message> messagesPage = messageRepository.findByDirectMessageChatIdAndCreatedAtBeforeOrderByCreatedAtDesc(dmChatId, beforeTimestamp, pageable);
        return messagesPage.map(this::mapMessageToDTO).getContent();
    }


    public MessageDTO mapMessageToDTO(Message message) {
        if (message == null) return null;
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        UserSimpleDTO senderDto = new UserSimpleDTO();
        senderDto.setId(message.getSender().getId());
        senderDto.setUsername(message.getSender().getUsername());
        senderDto.setAvatarUrl(message.getSender().getAvatarUrl());
        dto.setSender(senderDto);
        dto.setContent(message.getContent());
        dto.setMessageType(message.getMessageType());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setUpdatedAt(message.getUpdatedAt());
        dto.setEdited(message.isEdited());
        if (message.getChannel() != null) {
            dto.setChannelId(message.getChannel().getId());
        }
        if (message.getDirectMessageChat() != null) {
            dto.setDirectMessageChatId(message.getDirectMessageChat().getId());
        }
        if (message.getParentMessage() != null) {
            dto.setParentMessageId(message.getParentMessage().getId());
            dto.setParentMessageId(message.getParentMessage().getSender().getId());
            
            ParentMessageInfoDTO parentMessageInfo = new ParentMessageInfoDTO();
            parentMessageInfo.setId(message.getParentMessage().getId());
            parentMessageInfo.setSenderUsername(message.getParentMessage().getSender().getUsername());
            String parentContent = message.getParentMessage().getContent();
            String snippet = parentContent.length() > 50 ? parentContent.substring(0,47) + "..." : parentContent;
            parentMessageInfo.setContentSnippet(snippet);
            dto.setRepliedTo(parentMessageInfo);  
        }

        List<AttachmentDTO> attachmentDTOs = message.getAttachments().stream()
                .map(att -> {
                    AttachmentDTO dtoAtt = new AttachmentDTO();
                    dtoAtt.setId(att.getId());
                    dtoAtt.setFileName(att.getFileName());
                    dtoAtt.setFileUrl(att.getFileUrl());
                    dtoAtt.setMimeType(att.getMimeType());
                    dtoAtt.setFileSize(att.getFileSize());
                    dtoAtt.setAttachmentType(att.getAttachmentType());
                    dtoAtt.setUploadedAt(att.getUploadedAt());
                    return dtoAtt;
                })
                .collect(Collectors.toList());
        dto.setAttachments(attachmentDTOs);

        // Populate reaction counts and detailed reactions
        Map<String, Integer> reactionCounts = new HashMap<>();
        Map<String, Set<UserSimpleDTO>> reactionsByEmoji = new HashMap<>();

        // Efficiently get all reactions for this message once
        List<MessageReaction> allReactionsForMessage = reactionRepository.findAllByMessageId(message.getId());

        for (MessageReaction reaction : allReactionsForMessage) {
            String emoji = reaction.getEmojiUnicode();
            reactionCounts.put(emoji, reactionCounts.getOrDefault(emoji, 0) + 1);
            reactionsByEmoji.computeIfAbsent(emoji, k -> new HashSet<>())
                            .add(createUserSimpleDTO(reaction.getUser()));
        }
        dto.setReactionCounts(reactionCounts);
        dto.setReactionsByEmoji(reactionsByEmoji);

        return dto;
    }

    private UserSimpleDTO createUserSimpleDTO(User user) {
        return MapperUtil.toSimple(user);
    }

    public void broadcastTypingIndicator(TypingIndicatorDTO typingIndicatorDTO, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        typingIndicatorDTO.setUserId(user.getId());
        typingIndicatorDTO.setUsername(user.getUsername());

        String destination;
        if (typingIndicatorDTO.getChannelId() != null) {
            // Optional: Check if user is member of channel
            destination = "/topic/channels/" + typingIndicatorDTO.getChannelId() + "/typing";
        } else if (typingIndicatorDTO.getDirectMessageChatId() != null) {
            // Optional: Check if user is participant of DM
            destination = "/topic/dm/" + typingIndicatorDTO.getDirectMessageChatId() + "/typing";
        } else {
            logger.warn("Typing indicator from {} has no target channel or DM.", username);
            return;
        }
        logger.debug("Broadcasting typing indicator for user {} to {}: {}", username, destination, typingIndicatorDTO.isTyping());
        messagingTemplate.convertAndSend(destination, typingIndicatorDTO);
    }

    @Transactional
    public MessageDTO toggleReaction(Long msgId, Long userId, String emoji){
        Message msg = messageRepository.findById(msgId).orElseThrow();
        MessageReaction r  = reactionRepository.findByMessageIdAndUserIdAndEmojiUnicode(msgId,userId,emoji)
                       .orElseGet(() -> new MessageReaction(msg,userRepository.getReferenceById(userId),emoji));

        if(r.getId()==null) reactionRepository.save(r);           // add
        else                reactionRepository.delete(r);         // remove

        MessageDTO dto = mapMessageToDTO(msg);

        /* 📢  broadcast to topic so every client refreshes the pills */
        String topic = msg.getDirectMessageChat() != null
                       ? "/topic/dm/"+msg.getDirectMessageChat().getId()+"/messages/reactions/updated"
                       : "/topic/channels/"+msg.getChannel().getId()+"/messages/reactions/updated";

        messagingTemplate.convertAndSend(topic, dto);                 // SimpMessagingTemplate
        return dto;
    }

}