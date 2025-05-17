package com.azcord.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebRTCSignalingController {

    private final SimpMessagingTemplate broker;

    @MessageMapping("/signal")
    public void signal(Map<String, Object> payload, Principal principal) {
        String serverId = String.valueOf(payload.get("serverId"));
        String channelId = String.valueOf(payload.get("channelId"));
        String type = String.valueOf(payload.get("type"));

        System.out.println("Signal type: " + type + " from user: " + principal.getName());

        // Broadcast to everyone subscribed to that channel
        broker.convertAndSend("/topic/video/" + serverId + "/" + channelId, payload);
    }
}
