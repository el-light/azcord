package com.azcord.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.azcord.models.FriendRequest;

import java.util.List;
import java.util.UUID;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {
    boolean existsBySenderIdAndReceiverIdAndStatus(Long s, Long r, FriendRequest.Status st);
    List<FriendRequest> findByReceiverIdAndStatus(Long uid, FriendRequest.Status st);
} 