package com.azcord.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.azcord.models.Server;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long>{
    List<Server> findByUsers_Username(String username); 
    Optional<Server> findByName(String name); 
}
