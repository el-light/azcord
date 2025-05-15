package com.azcord.services;

import com.azcord.dto.*;
import com.azcord.exceptions.ChatNotFoundException;
import com.azcord.exceptions.ForbiddenAccessException;
import com.azcord.exceptions.UserNotFoundException;
import com.azcord.models.*;
import com.azcord.repositories.DirectMessageChatRepository;
import com.azcord.repositories.MessageRepository;
import com.azcord.repositories.UserRepository;
import com.azcord.services.MapperUtil;

import org.hibernate.validator.internal.util.stereotypes.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private DirectMessageChatRepository dmChatRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository; // To fetch last message for DTO mapping

    @Autowired
    private MessageService messageService; // For mapping Message to MessageDTO


    /**
     * Creates or retrieves an existing 1-on-1 direct message chat.
     * @param userId1 ID of the first user.
     * @param userId2 ID of the second user.
     * @return The DirectMessageChatDTO for the chat.
     */
    @Transactional
    public DirectMessageChatDTO createOrGetDirectMessageChat(Long userId1, Long userId2) {
        if (userId1.equals(userId2)) {
            throw new IllegalArgumentException("Cannot create a DM chat with oneself.");
        }

        // Ensure users exist
        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + userId1 + " not found."));
        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + userId2 + " not found."));

        // Check if a DM chat already exists between these two users
        // Sort IDs to ensure consistent query order if your findDirectMessageChatByUsers query relies on it
        Long u1 = Math.min(userId1, userId2);
        Long u2 = Math.max(userId1, userId2);

        DirectMessageChat chat = dmChatRepository.findDirectMessageChatByUsers(u1, u2)
                .orElseGet(() -> {
                    DirectMessageChat newChat = new DirectMessageChat();
                    newChat.setChatType(ChatType.DIRECT_MESSAGE);
                    newChat.getParticipants().add(user1);
                    newChat.getParticipants().add(user2);
                    newChat.setCreator(user1); // Or the initiating user
                    newChat.setLastActivityAt(LocalDateTime.now());
                    logger.info("Creating new DM chat between user {} and user {}", userId1, userId2);
                    return dmChatRepository.save(newChat);
                });

        return mapDirectMessageChatToDTO(chat, userId1); // Pass current user ID for context (e.g. unread count later)
    }

    /**
     * Creates a new group direct message chat.
     * @param creatorUserId The ID of the user creating the group chat.
     * @param participantUserIds A set of user IDs to be included in the group chat (excluding the creator).
     * @param groupName Optional name for the group chat.
     * @return The DirectMessageChatDTO for the newly created group chat.
     */
    @Transactional
    public DirectMessageChatDTO createGroupDirectMessageChat(Long creatorUserId, Set<Long> participantUserIds, String groupName) {
        if (participantUserIds == null || participantUserIds.isEmpty()) {
            throw new IllegalArgumentException("Group chat must have at least one other participant besides the creator.");
        }

        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new UserNotFoundException("Creator user with ID " + creatorUserId + " not found."));

        DirectMessageChat groupChat = new DirectMessageChat();
        groupChat.setChatType(ChatType.GROUP_DIRECT_MESSAGE);
        groupChat.setName(groupName != null && !groupName.isBlank() ? groupName : "Group Chat"); // Default name
        groupChat.setCreator(creator);
        groupChat.getParticipants().add(creator); // Add creator to participants

        for (Long participantId : participantUserIds) {
            if (participantId.equals(creatorUserId)) continue; // Skip creator if accidentally included
            User participant = userRepository.findById(participantId)
                    .orElseThrow(() -> new UserNotFoundException("Participant user with ID " + participantId + " not found."));
            groupChat.getParticipants().add(participant);
        }

        if (groupChat.getParticipants().size() < 2) { // Should be at least creator + 1 other
             throw new IllegalArgumentException("Group chat must have at least two distinct participants.");
        }
        groupChat.setLastActivityAt(LocalDateTime.now());
        DirectMessageChat savedGroupChat = dmChatRepository.save(groupChat);
        logger.info("Created new group DM chat '{}' by user {}", savedGroupChat.getName(), creatorUserId);
        return mapDirectMessageChatToDTO(savedGroupChat, creatorUserId);
    }

    /**
     * Retrieves all direct message chats for a given user, ordered by last activity.
     * @param userId The ID of the user.
     * @return A list of DirectMessageChatDTOs.
     */
    @Transactional(readOnly = true)
    public List<DirectMessageChatDTO> getUserDirectMessageChats(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID " + userId + " not found."));
        List<DirectMessageChat> chats = dmChatRepository.findByParticipantIdOrderByLastActivityDesc(userId);
        return chats.stream()
                .map(chat -> mapDirectMessageChatToDTO(chat, userId))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific DM chat by its ID.
     * Ensures the requesting user is a participant.
     * @param chatId The ID of the chat.
     * @param requestingUserId The ID of the user requesting the chat.
     * @return The DirectMessageChatDTO.
     */
    @Transactional(readOnly = true)
    public DirectMessageChatDTO getDirectMessageChatById(Long chatId, Long requestingUserId) {
        DirectMessageChat chat = dmChatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Direct message chat with ID " + chatId + " not found."));
        if (!isUserParticipant(chat, requestingUserId)) {
            throw new ForbiddenAccessException("User is not a participant of chat ID " + chatId);
        }
        return mapDirectMessageChatToDTO(chat, requestingUserId);
    }

    /**
     * Gets or creates a 1-to-1 direct message chat between two users
     * @param userId1 The ID of the first user
     * @param userId2 The ID of the second user
     * @return The DirectMessageChatDTO for the chat
     */
    @Transactional
    public DirectMessageChatDTO getOrCreate(Long userId1, Long userId2) {
        return createOrGetDirectMessageChat(userId1, userId2);
    }

    /**
     * Adds a user to an existing group DM chat.
     * @param chatId The ID of the group DM chat.
     * @param userIdToAdd The ID of the user to add.
     * @param requestingUserId The ID of the user performing the action (must be a participant).
     * @return The updated DirectMessageChatDTO.
     */
    @Transactional
    public DirectMessageChatDTO addUserToGroupChat(Long chatId, Long userIdToAdd, Long requestingUserId) {
        DirectMessageChat chat = dmChatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Group chat with ID " + chatId + " not found."));

        if (chat.getChatType() != ChatType.GROUP_DIRECT_MESSAGE) {
            throw new IllegalArgumentException("Cannot add users to a 1-on-1 DM chat.");
        }

        if (!isUserParticipant(chat, requestingUserId)) {
            throw new ForbiddenAccessException("Requesting user is not a participant of group chat ID " + chatId + " and cannot add members.");
        }

        User userToAdd = userRepository.findById(userIdToAdd)
                .orElseThrow(() -> new UserNotFoundException("User to add with ID " + userIdToAdd + " not found."));

        if (isUserParticipant(chat, userIdToAdd)) {
            logger.warn("User {} is already a participant in group chat {}", userIdToAdd, chatId);
            return mapDirectMessageChatToDTO(chat, requestingUserId); // Or throw exception/return specific response
        }

        chat.getParticipants().add(userToAdd);
        chat.setLastActivityAt(LocalDateTime.now()); // Update activity timestamp
        DirectMessageChat updatedChat = dmChatRepository.save(chat);
        logger.info("Added user {} to group DM chat {}", userIdToAdd, chatId);
        // TODO: Send a system message to the chat about the user joining.
        return mapDirectMessageChatToDTO(updatedChat, requestingUserId);
    }

    /**
     * Removes a user from a group DM chat.
     * @param chatId The ID of the group DM chat.
     * @param userIdToRemove The ID of the user to remove.
     * @param requestingUserId The ID of the user performing the action.
     * (Can be self-removal or removal by group admin/creator - admin logic not yet implemented)
     * @return The updated DirectMessageChatDTO.
     */
    @Transactional
    public DirectMessageChatDTO removeUserFromGroupChat(Long chatId, Long userIdToRemove, Long requestingUserId) {
        DirectMessageChat chat = dmChatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Group chat with ID " + chatId + " not found."));

        if (chat.getChatType() != ChatType.GROUP_DIRECT_MESSAGE) {
            throw new IllegalArgumentException("Cannot remove users from a 1-on-1 DM chat.");
        }

        User userToRemove = userRepository.findById(userIdToRemove)
                .orElseThrow(() -> new UserNotFoundException("User to remove with ID " + userIdToRemove + " not found."));

        // Allow self-removal or removal by creator (simple policy for now)
        boolean canRemove = requestingUserId.equals(userIdToRemove) ||
                            (chat.getCreator() != null && requestingUserId.equals(chat.getCreator().getId()));

        if (!canRemove && !isUserParticipant(chat, requestingUserId)) { // Double check participant status if not self-remove or creator
             throw new ForbiddenAccessException("Requesting user " + requestingUserId + " cannot remove user " + userIdToRemove + " from group chat " + chatId);
        }
        
        if (!isUserParticipant(chat, userIdToRemove)) {
            logger.warn("User {} is not a participant in group chat {}", userIdToRemove, chatId);
            return mapDirectMessageChatToDTO(chat, requestingUserId); // Or throw
        }

        if (chat.getParticipants().size() <= 2 && chat.getParticipants().contains(userToRemove)) {
            // If removing a user leaves less than 2 participants, consider deleting the group or special handling.
            // For now, let's prevent removal if it empties the chat or leaves one person.
            // Or, if it's the creator leaving, ownership might need to transfer or chat becomes ownerless/archived.
            // This logic can be complex. For now, we'll allow removal as long as it's not the last person.
            if(chat.getParticipants().size() == 1 && chat.getParticipants().contains(userToRemove)){
                 dmChatRepository.delete(chat);
                 logger.info("Deleted empty group DM chat {} after removing last participant {}", chatId, userIdToRemove);
                 return null; // Or a DTO indicating deletion
            }
        }


        chat.getParticipants().remove(userToRemove);
        chat.setLastActivityAt(LocalDateTime.now());
        DirectMessageChat updatedChat = dmChatRepository.save(chat);
        logger.info("Removed user {} from group DM chat {}", userIdToRemove, chatId);

        // TODO: Send a system message to the chat about the user leaving.
        return mapDirectMessageChatToDTO(updatedChat, requestingUserId);
    }


    /**
     * Updates the last activity timestamp for a chat.
     * @param chatId The ID of the chat.
     */
    @Transactional
    public void updateChatActivity(Long chatId) {
        DirectMessageChat chat = dmChatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat with ID " + chatId + " not found for activity update."));
        chat.setLastActivityAt(LocalDateTime.now());
        dmChatRepository.save(chat);
    }


    private boolean isUserParticipant(DirectMessageChat chat, Long userId) {
        return chat.getParticipants().stream().anyMatch(p -> p.getId().equals(userId));
    }

    public DirectMessageChatDTO mapDirectMessageChatToDTO(DirectMessageChat chat, Long currentUserId) {
        if (chat == null) return null;

        DirectMessageChatDTO dto = new DirectMessageChatDTO();
        dto.setId(chat.getId());
        dto.setChatType(chat.getChatType());
        
        Set<UserSimpleDTO> participantDTOs = chat.getParticipants().stream()
                .map(MapperUtil::toSimple)
                .collect(Collectors.toSet());
        dto.setParticipants(participantDTOs);

        if (chat.getChatType() == ChatType.DIRECT_MESSAGE && chat.getParticipants().size() == 2) {
            // For 1-on-1 DM, set the name to the other participant's username
            User otherParticipant = chat.getParticipants().stream()
                                        .filter(p -> !p.getId().equals(currentUserId))
                                        .findFirst()
                                        .orElse(null); // Should find one if currentUserId is a participant
            if (otherParticipant != null) {
                dto.setName(otherParticipant.getUsername());
            } else {
                 // Fallback if currentUserId is not in participants list (should not happen)
                 // or if it's a DM with self (which we prevent)
                dto.setName(chat.getName() != null ? chat.getName() : "Direct Message");
            }
        } else {
            dto.setName(chat.getName()); // For group DMs or if logic above fails
        }


        // Fetch and map the last message
        Optional<Message> lastMessageOpt = messageRepository.findTopByDirectMessageChatIdOrderByCreatedAtDesc(chat.getId());
        if (lastMessageOpt.isPresent()) {
            // Need a way to map Message to MessageDTO. Assuming MessageService has a mapper.
            // For now, a simplified mapping or use a dedicated mapper.
            dto.setLastMessage(messageService.mapMessageToDTO(lastMessageOpt.get()));
        }

        dto.setCreatedAt(chat.getCreatedAt());
        dto.setLastActivityAt(chat.getLastActivityAt());
        // dto.setUnreadCount(0); // Placeholder for unread count logic

        return dto;
    }
}