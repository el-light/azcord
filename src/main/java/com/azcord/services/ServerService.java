package com.azcord.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays; // For getAllPermissions
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.access.AccessDeniedException; // For permission checks
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Important for operations involving multiple saves

import com.azcord.dto.ChannelDTO;
import com.azcord.dto.RoleCreateDTO; // Assuming this exists from your provided code
import com.azcord.dto.RoleDTO;
import com.azcord.dto.RoleUpdateDTO; // New DTO
import com.azcord.dto.ServerDTO;
import com.azcord.exceptions.DuplicateRoleNameException; 

import com.azcord.exceptions.InviteExpiredException;
import com.azcord.exceptions.InviteNotFoundException;

import com.azcord.exceptions.ResourceNotFoundException;
import com.azcord.exceptions.RoleNotFoundException;
import com.azcord.exceptions.ServerNotFoundException;
import com.azcord.exceptions.UserNotFoundException; // Assuming you have or will create this
import com.azcord.models.Channel;
import com.azcord.models.Invite;
import com.azcord.models.Permission;
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

    // //service can reference itself
    // @Autowired
    // private ServerService serverService; 

    public Server createServer(String name, String userCreator){

        //we enforce uniqueness of the server name
        if(serverRepository.findByName(name).isPresent()){
            return null; 
        }
        
        Server srv = new Server(); 
        srv.setName(name); 
        User user = userRepository.findByUsername(userCreator).orElseThrow(); 
        List<Role> roles = new ArrayList<>();
        Role role = new Role();
        role.setName("Owner"); 
        role.setPermissions(Set.of(Permission.ADMINISTRATOR));
        role.setColorHex("#FF0000"); //red color for owner
        role.setServer(srv);
        roles.add(role); 
        user.setRoles(roles);
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
    @PreAuthorize("@serverService.hasPermission(#server_id, authentication.name, T(com.azcord.models.Permission).MANAGE_CHANNELS)")
    public Server createChannel(Long server_id, String name){

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


    @PreAuthorize("@serverService.hasPermission(#server_id, authentication.name, T(com.azcord.models.Permission).CREATE_INVITE)")
    public Invite createInvite(Long server_id , String username){

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


    @PreAuthorize("@serverService.hasPermission(#server_id, authentication.name, T(com.azcord.models.Permission).MANAGE_ROLES)")
    public Role createRole(Long server_id, String name, String colourHex, Set<Permission> permissions){
        Server server = serverRepository.findById(server_id)
            .orElseThrow(() -> new ServerNotFoundException("Server not found"));

        Role r = roleRepository.findByNameAndServer_Id(name,server_id).orElse(null);
        if(r !=null){
            if(r.getColorHex().equals(colourHex)){
                return null; 
            }
        }
        if(permissions == null || permissions.isEmpty()){
            permissions = new HashSet<>(); 
        }
        Role role = new Role(); 
        role.setName(name);
        role.setColorHex(colourHex);
        role.setServer(server); 
        role.setPermissions(permissions);
        return roleRepository.save(role); 
    }


    @PreAuthorize("@serverService.hasPermission(#server_id, authentication.name, T(com.azcord.models.Permission).MANAGE_ROLES)")
    public void assignRole(Long role_id, String username, Long server_id) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Server srv = serverRepository.findById(server_id).orElseThrow(() -> new ServerNotFoundException("No such a server")); 
        if(!srv.getUsers().contains(user)){
            throw new RuntimeException("User is not member of the server"); 
        }
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
        public RoleDTO mapRoleToDTO(Role role, User requestingUser, User targetUser, Long server_id) {
        if (role == null) {
            throw new RoleNotFoundException("Role not found");
        }
        
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setColor_Hex(role.getColorHex());
        
        // Handle null targetUser (for getAllServerRoles where there's no specific target user)
        boolean isAdmin = hasPermission(server_id, requestingUser.getUsername(), Permission.ADMINISTRATOR);
        boolean isSelf = targetUser != null && requestingUser.getId() == targetUser.getId();
        
        // Only include permissions if admin or looking at own roles
        if (isAdmin || isSelf || targetUser == null) {
            dto.setPermissions(role.getPermissions());
        }
        
        return dto;
    }
    //check if the user has a permission for doing smth on the server
    public boolean hasPermission(Long server_id, String username, Permission perm){
        List<Role> roles = roleRepository.findByUsers_UsernameAndServer_Id(username, server_id)
            .orElseThrow(() -> new RuntimeException("Roles not found"));

        if(roles.isEmpty()){
            return false; 
        }

        return roles.stream()
            .flatMap(role -> role.getPermissions().stream())
            .anyMatch(per -> per == perm || per == Permission.ADMINISTRATOR); 
    }

        /**
     * Updates an existing role on a server.
     * Requires MANAGE_ROLES permission.
     */
    @PreAuthorize("@serverService.hasPermission(#serverId, authentication.name, T(com.azcord.models.Permission).MANAGE_ROLES)")
    @Transactional
    public RoleDTO updateRole(Long serverId, Long roleId, RoleUpdateDTO roleUpdateDTO, String requestingUsername) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ServerNotFoundException("Server not found with ID: " + serverId));
        Role role = roleRepository.findByIdAndServer_Id(roleId, server.getId())
            .orElseThrow(() -> new RoleNotFoundException("Role with ID " + roleId + " not found on server " + server.getName()));

        // Prevent editing Administrator role by non-Administrators or removing Administrator permission
        boolean isEditingAdminRole = role.getPermissions().contains(Permission.ADMINISTRATOR);
        boolean isRequestingUserAdmin = hasPermission(serverId, requestingUsername, Permission.ADMINISTRATOR);

        if (roleUpdateDTO.getName() != null && !roleUpdateDTO.getName().isBlank() && !role.getName().equals(roleUpdateDTO.getName())) {
            if (roleRepository.findByNameAndServer_Id(roleUpdateDTO.getName(), serverId).isPresent()) {
                throw new DuplicateRoleNameException("Another role with the name '" + roleUpdateDTO.getName() + "' already exists on this server.");
            }
            if (role.getName().equals("Owner")) { // Basic protection for "Owner" role name
                 throw new RuntimeException("The 'Owner' role name cannot be changed.");
            }
            role.setName(roleUpdateDTO.getName());
        }

        if (roleUpdateDTO.getColorHex() != null && !roleUpdateDTO.getColorHex().isBlank()) {
            role.setColorHex(roleUpdateDTO.getColorHex());
        }

        if (roleUpdateDTO.getPermissions() != null) {
            // If the role being edited is an Administrator role, only another Administrator can change its permissions.
            // Also, an Administrator role cannot have its ADMINISTRATOR permission removed by a non-admin or if it's the last admin role.
            if (isEditingAdminRole && !isRequestingUserAdmin) {
                throw new AccessDeniedException("Only an Administrator can modify the permissions of an Administrator role.");
            }
            // Prevent removing ADMINISTRATOR permission if user is not an admin
            if (isEditingAdminRole && !roleUpdateDTO.getPermissions().contains(Permission.ADMINISTRATOR) && !isRequestingUserAdmin) {
                 throw new AccessDeniedException("You do not have permission to remove ADMINISTRATOR privilege from this role.");
            }
             // Prevent adding ADMINISTRATOR permission if user is not an admin
            if (!isEditingAdminRole && roleUpdateDTO.getPermissions().contains(Permission.ADMINISTRATOR) && !isRequestingUserAdmin) {
                 throw new AccessDeniedException("You do not have permission to grant ADMINISTRATOR privilege.");
            }

            role.setPermissions(new HashSet<>(roleUpdateDTO.getPermissions()));
        }

        Role updatedRole = roleRepository.save(role);
        User reqUser = userRepository.findByUsername(requestingUsername)
            .orElseThrow(() -> new UserNotFoundException("Requesting user not found")); // Should not happen if auth is working
        return mapRoleToDTO(updatedRole, reqUser, null, serverId); // targetUser is null as we are not viewing a specific user's role context
    }

    /**
     * Deletes a role from a server.
     * Requires MANAGE_ROLES permission.
     */
    @PreAuthorize("@serverService.hasPermission(#serverId, authentication.name, T(com.azcord.models.Permission).MANAGE_ROLES)")
    @Transactional
    public void deleteRole(Long serverId, Long roleId, String requestingUsername) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ServerNotFoundException("Server not found with ID: " + serverId));
        Role role = roleRepository.findByIdAndServer_Id(roleId, server.getId())
            .orElseThrow(() -> new RoleNotFoundException("Role with ID " + roleId + " not found on server " + server.getName()));

        // Basic protection for "Owner" role or any Administrator role if it's the last one
        if (role.getName().equals("Owner")) {
            throw new RuntimeException("The 'Owner' role cannot be deleted.");
        }
        if (role.getPermissions().contains(Permission.ADMINISTRATOR)) {
            boolean isRequestingUserAdmin = hasPermission(serverId, requestingUsername, Permission.ADMINISTRATOR);
            if (!isRequestingUserAdmin) {
                 throw new AccessDeniedException("Only an Administrator can delete another Administrator role.");
            }
            // Check if this is the last admin role on the server
            long adminRolesCount = server.getRoles().stream()
                .filter(r -> r.getPermissions().contains(Permission.ADMINISTRATOR))
                .count();
            if (adminRolesCount <= 1) {
                throw new RuntimeException("Cannot delete the last Administrator role on the server.");
            }
        }

        // Remove the role from all users who have it
        // Iterate over a copy of the users set to avoid ConcurrentModificationException
        for (User user : new HashSet<>(role.getUsers())) {
            user.getRoles().remove(role);
            userRepository.save(user); // Persist change for each user
        }
        role.getUsers().clear(); // Clear the association from the role's side

        server.getRoles().remove(role); // Remove from server's collection of roles
        
        roleRepository.delete(role);
        serverRepository.save(server); // Persist removal of role from server
    }

    /**
     * Removes a specific role from a user on a given server.
     * Requires MANAGE_ROLES permission.
     */
    @PreAuthorize("@serverService.hasPermission(#serverId, authentication.name, T(com.azcord.models.Permission).MANAGE_ROLES)")
    @Transactional
    public void removeRoleFromUser(Long serverId, Long roleId, Long targetUserId, String requestingUsername) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ServerNotFoundException("Server not found with ID: " + serverId));
        User targetUser = userRepository.findById(targetUserId)
            .orElseThrow(() -> new UserNotFoundException("Target user not found with ID: " + targetUserId));
        Role role = roleRepository.findByIdAndServer_Id(roleId, server.getId())
            .orElseThrow(() -> new RoleNotFoundException("Role with ID " + roleId + " not found on server " + server.getName()));

        if (!server.getUsers().contains(targetUser)) {
            throw new UserNotFoundException("User " + targetUser.getUsername() + " is not a member of server " + server.getName());
        }

        // Prevent removing Administrator role from a user if the remover is not an Administrator
        // or if the target user is the server owner (implicitly, the first admin).
        // More complex owner logic would be needed for full protection.
        if (role.getPermissions().contains(Permission.ADMINISTRATOR) && !hasPermission(serverId, requestingUsername, Permission.ADMINISTRATOR)) {
            throw new AccessDeniedException("You do not have permission to remove an Administrator role from a user.");
        }
        
        // Prevent removing the "Owner" role
        if (role.getName().equals("Owner") && targetUser.getRoles().contains(role)) {
             // Check if this user is the original creator or has the "Owner" role uniquely
             // This logic needs to be robust; for now, a simple name check.
             // A better check might involve checking if the user is the server creator if that info is stored.
             // Or if this is the *only* user with the "Owner" role.
             throw new RuntimeException("The 'Owner' role cannot be removed from this user.");
        }


        if (targetUser.getRoles().contains(role)) {
            targetUser.getRoles().remove(role);
            userRepository.save(targetUser);
        } else {
            // Optionally, inform that the user didn't have the role. For now, just a no-op.
        }
    }

    /**
     * Retrieves all roles for a specific server.
     * The requesting user's permissions will determine if they can see the 'permissions' field of each role.
     */
    @Transactional(readOnly = true) // Good practice for read-only operations
    public List<RoleDTO> getServerRoles(Long serverId, String requestingUsername) {
        Server server = serverRepository.findById(serverId)
            .orElseThrow(() -> new ServerNotFoundException("Server not found with ID: " + serverId));
        User requestingUser = userRepository.findByUsername(requestingUsername)
            .orElseThrow(() -> new UserNotFoundException("Requesting user not found: " + requestingUsername)); // Should be authenticated

        // Ensure user is part of the server to view its roles, unless they are a global admin (not implemented here)
        // For simplicity, let's assume if they know the serverId and are authenticated, they can try.
        // The `mapRoleToDTO` will handle permission visibility.

        List<Role> serverRoles = roleRepository.findAllByServer_id(serverId);
        return serverRoles.stream()
            .map(role -> mapRoleToDTO(role, requestingUser, null, serverId)) // targetUser is null, mapRoleToDTO handles admin check for permissions
            .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of all available permissions in the system.
     */
    public List<Permission> getAllPermissions() {
        return Arrays.asList(Permission.values());
    }

    
}