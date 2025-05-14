package com.azcord.services;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.azcord.dto.FriendRequestDTO;
import com.azcord.dto.UserSimpleDTO;
import com.azcord.exceptions.ForbiddenException;
import com.azcord.exceptions.NotFoundException;
import com.azcord.models.FriendRequest;
import com.azcord.models.Friendship;
import com.azcord.repositories.FriendRequestRepository;
import com.azcord.repositories.FriendshipRepository;
import com.azcord.repositories.UserRepository;

@Service
public class FriendService {
    
    @Autowired
    private UserRepository userRepo;
    
    @Autowired
    private FriendRequestRepository reqRepo;
    
    @Autowired
    private FriendshipRepository frRepo;
    
    @Autowired
    private SimpMessagingTemplate broker;

    @Transactional
    public void sendRequest(Long senderId, Long receiverId) {
        if (frRepo.areFriends(senderId, receiverId)) {
            throw new IllegalStateException("Already friends");
        }
        
        if (reqRepo.existsBySenderIdAndReceiverIdAndStatus(
                senderId, receiverId, FriendRequest.Status.PENDING)) {
            throw new IllegalStateException("Request already sent");
        }
        
        FriendRequest fr = new FriendRequest();
        fr.setSender(userRepo.getReferenceById(senderId));
        fr.setReceiver(userRepo.getReferenceById(receiverId));
        reqRepo.save(fr);

        broker.convertAndSendToUser(
                receiverId.toString(),
                "/queue/friend-requests",
                new FriendRequestDTO(fr));
    }

    @Transactional
    public void respond(UUID requestId, Long receiverId, boolean accept) {
        FriendRequest fr = reqRepo.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Friend request"));
                
        if (!fr.getReceiver().getId().equals(receiverId)) {
            throw new ForbiddenException();
        }
        
        fr.setStatus(accept ? FriendRequest.Status.ACCEPTED
                            : FriendRequest.Status.DECLINED);
        reqRepo.save(fr);
        
        if (accept) {
            Long a = fr.getSender().getId();
            Long b = fr.getReceiver().getId();
            Friendship fs = new Friendship();
            fs.setUser1(Math.min(a, b));
            fs.setUser2(Math.max(a, b));
            frRepo.save(fs);
        }
        
        broker.convertAndSendToUser(
                fr.getSender().getId().toString(),
                "/queue/friend-requests/updates",
                new FriendRequestDTO(fr));
    }

    public List<UserSimpleDTO> myFriends(Long uid) {
        return frRepo.findByUser1OrUser2(uid, uid).stream()
                .map(fp -> userRepo.findById(
                        fp.getUser1().equals(uid) ? fp.getUser2() : fp.getUser1())
                        .orElseThrow())
                .map(MapperUtil::toSimple)
                .toList();
    }

    public List<FriendRequestDTO> pending(Long uid) {
        return reqRepo.findByReceiverIdAndStatus(uid, FriendRequest.Status.PENDING)
                .stream()
                .map(FriendRequestDTO::new)
                .toList();
    }
} 