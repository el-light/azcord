package com.azcord.controllers;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.azcord.dto.ChannelCreateDTO;
import com.azcord.dto.InviteJoinDTO;
import com.azcord.dto.RoleCreateDTO;
import com.azcord.dto.RoleDTO;
import com.azcord.dto.ServerCreateDTO;
import com.azcord.dto.ServerDTO;
import com.azcord.exceptions.DuplicateServerNameException;
import com.azcord.models.Invite;
import com.azcord.models.Server;
import com.azcord.services.ServerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    @Autowired
    private ServerService serverService;  
    
    @Autowired
    private UserService userService; 

    //create a server 
    @PostMapping()
    public ResponseEntity<?> createServer(@Valid @RequestBody ServerCreateDTO serverCreateDTO){

        String username = SecurityContextHolder.getContext().getAuthentication().getName(); 
        Server srv = serverService.createServer(serverCreateDTO.getName(), username);

        if (srv == null) {
            throw new DuplicateServerNameException("Server with this name already exists.");
          }          
        ServerDTO serverDTO = new ServerDTO(); 
        serverService.mapServerToDTO(srv, serverDTO);
        return new ResponseEntity<>(serverDTO, HttpStatus.CREATED);
    }

    //for front end purposes we extract all servers where the user is
    @GetMapping()
    public ResponseEntity<List<ServerDTO>> getUsersServers(){
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Server> servers = serverService.getUserServers(username);
        
        //we map each server from servers list to the serverDTOs list, exposure reasons again
        List<ServerDTO> serverDTOs = servers.stream().
            map(srv -> {
                ServerDTO sdt = new ServerDTO();
                serverService.mapServerToDTO(srv, sdt);
                return sdt; 
            }).collect(Collectors.toList()); 
        return new ResponseEntity<>(serverDTOs, HttpStatus.OK);
    }

    @PostMapping("/{id}/channels")
    public ResponseEntity<?> createChannel(@PathVariable("id") long id , @RequestBody ChannelCreateDTO channelCreateDTO){
        serverService.createChannel(id, channelCreateDTO.getName()); 
        return ResponseEntity.ok("Channel " + channelCreateDTO.getName() + " created!");
    }

    //api to create invite link and return the code to the user
    @PostMapping("/{id}/invites")
    public ResponseEntity<?> createInvite(@PathVariable("id") Long id){
        String username = SecurityContextHolder.getContext().getAuthentication().getName(); 
        Invite invite = serverService.createInvite(id, username); 
        return ResponseEntity.ok(invite.getCode()); 
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinServer(@RequestBody InviteJoinDTO inviteJoinDTO){
        String username = SecurityContextHolder.getContext().getAuthentication().getName(); 
        serverService.joinWithInvite(username, inviteJoinDTO.getCode()); 
        return new ResponseEntity<String>("You succesfully joined the server", HttpStatus.OK); 
    }
    
    // New endpoints
    
    // 1. Change server name
    @PutMapping("/{id}/name")
    public ResponseEntity<?> updateServerName(@PathVariable("id") Long id, @Valid @RequestBody ServerCreateDTO serverUpdateDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Server updatedServer = serverService.updateServerName(id, serverUpdateDTO.getName(), username);
        
        if (updatedServer == null) {
            throw new DuplicateServerNameException("Server with this name already exists.");
        }
        
        ServerDTO serverDTO = new ServerDTO();
        serverService.mapServerToDTO(updatedServer, serverDTO);
        return new ResponseEntity<>(serverDTO, HttpStatus.OK);
    }
    
    // 2. Delete server
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteServer(@PathVariable("id") Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        serverService.deleteServer(id, username);
        return new ResponseEntity<>("Server successfully deleted", HttpStatus.OK);
    }
    
    // 3. Delete channel
    @DeleteMapping("/{serverId}/channels/{channelId}")
    public ResponseEntity<?> deleteChannel(@PathVariable("serverId") Long serverId, 
                                          @PathVariable("channelId") Long channelId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        serverService.deleteChannel(serverId, channelId, username);
        return new ResponseEntity<>("Channel successfully deleted", HttpStatus.OK);
    }
    
    // 4. Change channel name
    @PutMapping("/{serverId}/channels/{channelId}")
    public ResponseEntity<?> updateChannelName(@PathVariable("serverId") Long serverId,
                                              @PathVariable("channelId") Long channelId,
                                              @Valid @RequestBody ChannelCreateDTO channelUpdateDTO) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        serverService.updateChannelName(serverId, channelId, channelUpdateDTO.getName(), username);
        return new ResponseEntity<>("Channel name updated successfully", HttpStatus.OK);
    } 

    @PostMapping("/{id}/roles")
    public ResponseEntity<?> createRole(@PathVariable("id") Long id,@Valid @RequestBody RoleCreateDTO roleCreateDTO){
        String colorHex = roleCreateDTO.getColor_hex();
        if(colorHex==null|| colorHex.isBlank()){
            colorHex = "#808080";
        }
        serverService.createRole(id, roleCreateDTO.getName(), colorHex);
        return ResponseEntity.ok("Role " + roleCreateDTO.getName() + " created!");
    }


    //add role for the user on one server
    @PostMapping("/{id}/roles/{role_id}/members/{user_id}")
    public ResponseEntity<?> addRoleToUser(
        @PathVariable("id") Long id, 
        @PathVariable("role_id") Long role_id,
        @PathVariable("user_id") Long user_id){
        String username = userService.getUserById(id).getUsername();  
        serverService.assignRole(role_id, username, id); 
        return ResponseEntity.ok("Role added to user " + username); 
    }


    //get roles of 1 user on 1 server
    @GetMapping("/{id}/roles/{user_id}")
    public ResponseEntity<?> getUsersRoles(@PathVariable("id") Long server_id, @PathVariable("user_id") Long id){
        User user = userService.getUserById(id); 
        List<RoleDTO> roleDTOs = serverService.getUsersRolesOnTheServer(user.getUsername(),server_id).stream()
            .map(role -> {
                RoleDTO roleDTO = new RoleDTO();
                roleDTO.setColor_Hex(role.getColorHex());
                roleDTO.setId(role.getId());
                roleDTO.setName(role.getName());
                return roleDTO;  
            }).collect(Collectors.toList());

        return new ResponseEntity<>(roleDTOs, HttpStatus.OK);
    }
}