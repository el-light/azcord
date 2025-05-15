package com.azcord.controllers;

import com.azcord.dto.EditMessageDTO;
import com.azcord.dto.MessageDTO;
import com.azcord.dto.ReactionRequestDTO;
import com.azcord.dto.SendMessageDTO;
import com.azcord.services.MessageService;
import com.azcord.services.UserService;
import com.azcord.models.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api") // Base path for messages, can be /api/messages or nested under channels/dm-chats
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserService userService;


    // Endpoint to send a message (can handle file uploads via REST)
    // For WebSockets, clients usually send simpler JSON payloads after uploading files separately.
    @PostMapping(value = "/messages", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<MessageDTO> sendMessageWithAttachments(
            @RequestPart(value = "sendMessageDTO") @Valid SendMessageDTO sendMessageDTO, // JSON part
            @RequestPart(value = "files", required = false) List<MultipartFile> files // File parts
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        sendMessageDTO.setFiles(files); // Set files into DTO
        MessageDTO messageDTO = messageService.sendMessage(sendMessageDTO, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(messageDTO);
    }
     // Fallback for sending message without multipart (e.g. only text or pre-uploaded file URLs)
    @PostMapping(value = "/messages", consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<MessageDTO> sendMessageJSON(@Valid @RequestBody SendMessageDTO sendMessageDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // Ensure files list is null or empty if not using multipart
        if (sendMessageDTO.getFiles() != null && !sendMessageDTO.getFiles().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null); // Or throw error
        }
        MessageDTO messageDTO = messageService.sendMessage(sendMessageDTO, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(messageDTO);
    }


    @PutMapping("/messages/{messageId}")
    public ResponseEntity<MessageDTO> editMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageDTO editMessageDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        MessageDTO updatedMessage = messageService.editMessage(messageId, editMessageDTO, username);
        return ResponseEntity.ok(updatedMessage);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        messageService.deleteMessage(messageId, username);
        return ResponseEntity.noContent().build();
    }

    // --- Reactions ---
    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<MessageDTO> addReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody ReactionRequestDTO reactionRequestDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        MessageDTO updatedMessage = messageService.addReaction(messageId, username, reactionRequestDTO.getEmojiUnicode());
        return ResponseEntity.ok(updatedMessage);
    }

    @DeleteMapping("/messages/{messageId}/reactions") // Could also be /messages/{messageId}/reactions/{emojiUnicode}
    public ResponseEntity<MessageDTO> removeReaction(
            @PathVariable Long messageId,
            @Valid @RequestBody ReactionRequestDTO reactionRequestDTO) { // Assuming emoji is in body for consistency
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        MessageDTO updatedMessage = messageService.removeReaction(messageId, username, reactionRequestDTO.getEmojiUnicode());
        return ResponseEntity.ok(updatedMessage);
    }

    // --- Fetching Messages ---
    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<Page<MessageDTO>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MessageDTO> messages = messageService.getMessagesForChannel(channelId, username, pageable);
        return ResponseEntity.ok(messages);
    }
    
    @GetMapping("/channels/{channelId}/messages/before")
    public ResponseEntity<List<MessageDTO>> getChannelMessagesBefore(
            @PathVariable Long channelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp,
            @RequestParam(defaultValue = "20") int size) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<MessageDTO> messages = messageService.getMessagesForChannelBefore(channelId, username, timestamp, size);
        return ResponseEntity.ok(messages);
    }


    @GetMapping("/dm-chats/{dmChatId}/messages")
    public ResponseEntity<Page<MessageDTO>> getDirectMessageChatMessages(
            @PathVariable Long dmChatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MessageDTO> messages = messageService.getMessagesForDirectMessageChat(dmChatId, username, pageable);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/dm-chats/{dmChatId}/messages/before")
    public ResponseEntity<List<MessageDTO>> getDirectMessageChatMessagesBefore(
            @PathVariable Long dmChatId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime timestamp,
            @RequestParam(defaultValue = "20") int size) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
         List<MessageDTO> messages = messageService.getMessagesForDirectMessageChatBefore(dmChatId, username, timestamp, size);
        return ResponseEntity.ok(messages);
    }
}