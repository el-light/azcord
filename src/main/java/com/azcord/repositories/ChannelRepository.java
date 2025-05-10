package com.azcord.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.azcord.models.Channel;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByNameAndServer_Id(String name, Long serverId);
    List<Channel> findByServer_Id(Long serverId);
}
