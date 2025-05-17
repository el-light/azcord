package com.azcord.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.azcord.models.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    

    //find List of roles of 1 user on 1 server
    Optional<List<Role>>  findByUsers_UsernameAndServer_Id(String username, Long server_Id); 

    //find the role by its name on 1 server
    Optional<Role> findByNameAndServer_Id(String name, Long serverId); 

    List<Role> findAllByServer_id(Long server_id); 
    //return the role by its id and server id
    Optional<Role> findByIdAndServer_Id(Long roleId, Long serverId);
}
