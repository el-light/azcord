package com.azcord.controllers;

import com.azcord.dto.CreateDirectMessageChatDTO;
import com.azcord.dto.DirectMessageChatDTO;
import com.azcord.services.ChatService;
import com.azcord.services.UserService;
import com.azcord.models.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/dm-chats")
public class DirectMessageChatController {

    @Autowired
    private ChatService chatService;
    
    @Autowired
    private UserService userService;

    /** Create‑or‑fetch my 1‑to‑1 chat with {friendId} */
    @PostMapping("/with/{friendId}")
    public DirectMessageChatDTO open(@PathVariable Long friendId, Principal p) {
        Long me = userService.idOf(p.getName());
        return chatService.getOrCreate(me, friendId);
    }

    /**
     * Get all DM chats for the currently authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<DirectMessageChatDTO>> getUserDirectMessageChats() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userService.getUserByName(username);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<DirectMessageChatDTO> chats = chatService.getUserDirectMessageChats(currentUser.getId());
        return ResponseEntity.ok(chats);
    }

    /**
     * Create a new 1-on-1 DM or a group DM.
     * For 1-on-1, participantUserIds should contain one ID (the other user).
     * For group, participantUserIds can contain multiple IDs.
     * The creator is automatically added.
     */
    @PostMapping
    public ResponseEntity<DirectMessageChatDTO> createDirectMessageChat(@Valid @RequestBody CreateDirectMessageChatDTO createDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User creator = userService.getUserByName(username);
        if (creator == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        DirectMessageChatDTO chatDTO;
        // If only one participant ID is provided and no groupName, assume 1-on-1.
        // The service layer handles the logic of finding existing 1-on-1 or creating new.
        if (createDTO.getParticipantUserIds().size() == 1 && (createDTO.getGroupName() == null || createDTO.getGroupName().isBlank())) {
            Long otherUserId = createDTO.getParticipantUserIds().iterator().next();
            chatDTO = chatService.createOrGetDirectMessageChat(creator.getId(), otherUserId);
        } else {
            // For group chats or if a name is provided even with one other participant
            chatDTO = chatService.createGroupDirectMessageChat(creator.getId(), createDTO.getParticipantUserIds(), createDTO.getGroupName());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(chatDTO);
    }

    /**
     * Get a specific DM chat by its ID.
     * Ensures the requesting user is a participant.
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<DirectMessageChatDTO> getDirectMessageChatById(@PathVariable Long chatId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userService.getUserByName(username);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DirectMessageChatDTO chatDTO = chatService.getDirectMessageChatById(chatId, currentUser.getId());
        return ResponseEntity.ok(chatDTO);
    }

    /**
     * Add a user to a group DM chat.
     */
    @PostMapping("/{chatId}/participants/{userIdToAdd}")
    public ResponseEntity<DirectMessageChatDTO> addUserToGroupChat(
            @PathVariable Long chatId,
            @PathVariable Long userIdToAdd) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User requestingUser = userService.getUserByName(username);
        if (requestingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DirectMessageChatDTO updatedChat = chatService.addUserToGroupChat(chatId, userIdToAdd, requestingUser.getId());
        return ResponseEntity.ok(updatedChat);
    }

    /**
     * Remove a user from a group DM chat.
     * (Can be self-removal or removal by admin/creator - current policy is self or creator).
     */
    @DeleteMapping("/{chatId}/participants/{userIdToRemove}")
    public ResponseEntity<?> removeUserFromGroupChat(
            @PathVariable Long chatId,
            @PathVariable Long userIdToRemove) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User requestingUser = userService.getUserByName(username);
        if (requestingUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        DirectMessageChatDTO updatedChat = chatService.removeUserFromGroupChat(chatId, userIdToRemove, requestingUser.getId());
        if (updatedChat == null) { // Indicates chat was deleted because it became empty
            return ResponseEntity.ok("User removed and group chat was deleted as it became empty.");
        }
        return ResponseEntity.ok(updatedChat);
    }
}