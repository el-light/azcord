package com.azcord.controllers;

import com.azcord.dto.TypingDTO;
import com.azcord.models.User;
import com.azcord.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class TypingController {

    private final SimpMessagingTemplate broker;
    private final UserRepository userRepo;

    @MessageMapping("/chat.typing")
    public void typing(TypingDTO dto, Principal pr) {
        User u = userRepo.findByUsername(pr.getName()).orElseThrow();
        String dest = dto.direct()
                ? "/topic/dm/" + dto.chatId() + "/typing"
                : "/topic/channels/" + dto.chatId() + "/typing";
        broker.convertAndSend(dest,
                new TypingDTO(dto.chatId(), u.getId(), u.getUsername(), dto.direct()));
    }
} 