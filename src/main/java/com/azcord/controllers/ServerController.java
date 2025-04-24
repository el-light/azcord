package com.azcord.controllers;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.azcord.dto.ChannelCreateDTO;
import com.azcord.dto.InviteJoinDTO;
import com.azcord.dto.ServerCreateDTO;
import com.azcord.dto.ServerDTO;
import com.azcord.models.Invite;
import com.azcord.models.Server;
import com.azcord.models.User;
import com.azcord.services.ServerService;
import com.azcord.services.UserService;

@RestController
@RequestMapping("/api/servers")
public class ServerController {

    @Autowired
    private ServerService serverService;    

    //create a server 
    @PostMapping()
    public ResponseEntity<?> createServer(@RequestBody ServerCreateDTO serverCreateDTO){

        String username = SecurityContextHolder.getContext().getAuthentication().getName(); 
        Server srv = serverService.createServer(serverCreateDTO.getName(), username);

        if (srv == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Server with such a name already exists!");
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
}