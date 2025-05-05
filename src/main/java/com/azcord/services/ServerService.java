package com.azcord.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.azcord.dto.ChannelDTO;
import com.azcord.dto.RoleDTO;
import com.azcord.dto.ServerCreateDTO;
import com.azcord.dto.ServerDTO;
import com.azcord.exceptions.InviteExpiredException;
import com.azcord.exceptions.InviteNotFoundException;

import com.azcord.exceptions.ResourceNotFoundException;
import com.azcord.exceptions.RoleNotFoundException;
import com.azcord.exceptions.ServerNotFoundException;
import com.azcord.models.Channel;
import com.azcord.models.Invite;
import com.azcord.models.Role;
import com.azcord.models.Server;
import com.azcord.models.User;
import com.azcord.repositories.InviteRepository;
import com.azcord.repositories.RoleRepository;
import com.azcord.repositories.ServerRepository;
import com.azcord.repositories.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class ServerService {
    
    @Autowired
    UserRepository userRepository;

    @Autowired
    ServerRepository serverRepository; 

    @Autowired
    InviteRepository inviteRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    // Helper method to check if user has permission to modify a server
    private void checkServerPermission(Server server, String username) {
        boolean isMember = server.getUsers().stream()
            .anyMatch(user -> user.getUsername().equals(username));
            
        if (!isMember) {
            throw new AccessDeniedException("You don't have permission to modify this server");
        }
    }

    @Autowired
    RoleRepository roleRepository; 

    public Server createServer(String name, String userCreator){

        //we enforce uniqueness of the server name
        if(serverRepository.findByName(name).isPresent()){
            return null; 
        }
        
        Server srv = new Server(); 
        srv.setName(name); 
        User user = userRepository.findByUsername(userCreator).orElseThrow(); 
        srv.getUsers().add(user); 
        return serverRepository.save(srv); 
    }

    public List<Server> getUserServers(String username){
        return serverRepository.findByUsers_Username(username); 
    }

    //whatever is in the Server will be mapped to ServerDTO for exposure reasons
    public void mapServerToDTO(Server server, ServerDTO serverDTO){
        if(server==null){
            return; 
        }
        serverDTO.setServer_id(server.getId());
        serverDTO.setName(server.getName());
        serverDTO.setMembers(server.getUsers().stream()
        .map(name -> name.getUsername())
        .collect(Collectors.toList()));

        //we put all channels from server in form of ChannelDTO into ServerDTO, again exposure reasons
        serverDTO.setChannels(
            server.getChannels().stream()
                  .map(ch -> {
                     ChannelDTO cd = new ChannelDTO();
                     cd.setId(ch.getId());
                     cd.setName(ch.getName());
                     return cd;
                  })
                  .collect(Collectors.toList())
          );
          serverDTO.setServer_id(server.getId());
    }


    //creating channel in the server
    public Server createChannel(long server_id, String name){

        Server srv = serverRepository.findById(server_id)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        Channel channel = new Channel(); 
        channel.setName(name);
        channel.setServer(srv);
        srv.getChannels().add(channel); 
        return serverRepository.save(srv);
    }


    public String generateUniqueCode(){
        int attempt = 0 ; 
        int max_attempts = 10; 
        String code; 

        do{
            if(attempt++>=max_attempts){
                throw new RuntimeException("Failed to generate invite code."); 
            }
                code = UUID.randomUUID().toString()
                .replaceAll("-", "").substring(0,8); 
        }while(inviteRepository.findByCode(code).isPresent());

        return code; 
    }


    public Invite createInvite(long server_id , String username){

        Server srv = serverRepository.findById(server_id)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found")); 
        Invite invt = new Invite(); 
        invt.setCreatedAt(LocalDateTime.now());
        invt.setExpiresAt(LocalDateTime.now().plusDays(7));
        invt.setGeneratedBy(username);
        invt.setServer(srv);
        invt.setCode(generateUniqueCode());
        return inviteRepository.save(invt); 
    }

    public Server joinWithInvite(String username, String code){
        Invite invite = inviteRepository.findByCode(code)
            .orElseThrow(() -> new InviteNotFoundException("Invalid link"));

        if(invite.getExpiresAt() != null && LocalDateTime.now().isAfter(invite.getExpiresAt())){
            throw new InviteExpiredException("Invitation is expired"); 
        }

        Server server = invite.getServer(); 
        User user = userRepository.findByUsername(username).orElse(null);
        if(user != null && !server.getUsers().contains(user)){
            server.getUsers().add(user); 
            return serverRepository.save(server);
        }
        
        return server;
    }
    
    // New methods for required endpoints
    
    // 1. Change server name
    public Server updateServerName(Long serverId, String newName, String username) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        
        // Check if user has permission
        checkServerPermission(server, username);
        
        // Check if the new name already exists for another server
        if(!server.getName().equals(newName) && serverRepository.findByName(newName).isPresent()) {
            return null; // Name already exists
        }
        
        server.setName(newName);
        return serverRepository.save(server);
    }
    
    // 2. Delete server - FIXED to handle invite records
    @Transactional
    public void deleteServer(Long serverId, String username) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        
        // Check if user has permission
        checkServerPermission(server, username);
        
        // First, delete all invite records associated with this server
        List<Invite> invites = inviteRepository.findByServer_Id(serverId);
        inviteRepository.deleteAll(invites);
        
        // Clear the relationship between server and users to avoid cascade delete issues
        server.getUsers().clear();
        serverRepository.save(server);
        
        // Now delete the server
        serverRepository.delete(server);
    }
    
    // 3. Delete channel
    @Transactional
    public void deleteChannel(Long serverId, Long channelId, String username) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        
        // Check if user has permission
        checkServerPermission(server, username);
        
        // Find the channel to remove
        Channel channelToRemove = server.getChannels().stream()
            .filter(channel -> channel.getId() == channelId)
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Channel not found in this server"));
        
        // Remove channel from server's collection
        server.getChannels().remove(channelToRemove);
        
        // Save the server with updated channel list
        serverRepository.save(server);
        
        // Now delete the channel entity from the database
        entityManager.remove(channelToRemove);
    }
    
    // 4. Change channel name
    public Server updateChannelName(Long serverId, Long channelId, String newName, String username) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ResourceNotFoundException("Server not found"));
        
        // Check if user has permission
        checkServerPermission(server, username);
        
        // Find and update the channel
        Channel channelToUpdate = server.getChannels().stream()
            .filter(channel -> channel.getId() == channelId)
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Channel not found in this server"));
        
        channelToUpdate.setName(newName);
        return serverRepository.save(server);


    public Role createRole(Long server_id, String name, String colourHex){
        Server server = serverRepository.findById(server_id)
            .orElseThrow(() -> new ServerNotFoundException("Server not found"));

        Role r = roleRepository.findByNameAndServer_Id(name,server_id).orElse(null);
        if(r !=null){
            if(r.getColorHex().equals(colourHex)){
                return null; 
            }
        }
        Role role = new Role(); 
        role.setName(name);
        role.setColorHex(colourHex);
        role.setServer(server); 
        return roleRepository.save(role); 
    }


    public void assignRole(Long role_id, String username, Long server_id) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        //roles of 1 user on 1 server 
        List<Role> roles = roleRepository.findByUsers_UsernameAndServer_Id(username, server_id)
            .orElse(null); 
        if(roles == null){
            roles = List.of(); 
        }
        //we check if user already has role with same id (aka same role aka same name same colour)
        List<Long> ids = roles.stream().map(r -> r.getId()).collect(Collectors.toList());

        if(ids.contains(role_id)){
            return; 
        }

            Role role = roleRepository.findById(role_id).orElseThrow(() -> new RoleNotFoundException("Role not found"));
            user.getRoles().add(role);
            userRepository.save(user); 
        
    }

    //get roles of 1 USER ON 1 SERVER 
    public List<Role> getUsersRolesOnTheServer(String username, Long server_id){
        return roleRepository.findByUsers_UsernameAndServer_Id(username, server_id)
            .orElseThrow(() -> new RuntimeException("Roles not found"));
    }

    //map Role to dto
    public RoleDTO mapRoleToDTO(Role role){
        if(role == null){
            throw new RoleNotFoundException("Role not found"); 
        }
        RoleDTO roleDTO = new RoleDTO(); 
        roleDTO.setId(role.getId());
        roleDTO.setName(role.getName());
        roleDTO.setColor_Hex(role.getColorHex());
        return roleDTO; 
    }
}