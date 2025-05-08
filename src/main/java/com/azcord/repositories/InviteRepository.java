package com.azcord.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.azcord.models.Invite;


@Repository
public interface  InviteRepository extends JpaRepository<Invite, Long>{
    Optional<Invite> findByCode(String code); 
    List<Invite> findByServer_Id(Long serverId);
}
