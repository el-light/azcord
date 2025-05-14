package com.azcord.controllers;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.azcord.dto.FriendRequestCreateDTO;
import com.azcord.dto.FriendRequestDTO;
import com.azcord.dto.UserSimpleDTO;
import com.azcord.repositories.UserRepository;
import com.azcord.services.FriendService;
import com.azcord.services.MapperUtil;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    
    @Autowired
    private FriendService svc;
    
    @Autowired
    private UserRepository userRepo;

    @GetMapping("/search")
    public List<UserSimpleDTO> search(@RequestParam String q) {
        return userRepo.findTop10ByUsernameContainingIgnoreCase(q)
                .stream()
                .map(MapperUtil::toSimple)
                .toList();
    }

    @PostMapping("/requests")
    public void send(@RequestBody FriendRequestCreateDTO dto, Principal p) {
        Long id = userRepo.findIdByUsername(p.getName());
        svc.sendRequest(id, dto.receiverId());
    }

    @PutMapping("/requests/{id}")
    public void respond(@PathVariable UUID id, @RequestParam boolean accept, Principal p) {
        Long me = userRepo.findIdByUsername(p.getName());
        svc.respond(id, me, accept);
    }

    @GetMapping
    public List<UserSimpleDTO> myFriends(Principal p) {
        Long me = userRepo.findIdByUsername(p.getName());
        return svc.myFriends(me);
    }

    @GetMapping("/requests")
    public List<FriendRequestDTO> myPending(Principal p) {
        Long me = userRepo.findIdByUsername(p.getName());
        return svc.pending(me);
    }
} 