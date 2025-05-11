package com.azcord.models;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Data
public class Server {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
  
    @NotBlank
    @Column(unique= true)
    private String name;
    
    // Modified: Changed cascade type to not include REMOVE/DELETE operations
    @OneToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Channel> channels = new ArrayList<>();
    
    // Modified: Changed cascade type to remove ALL and specifically exclude REMOVE/DELETE operations
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "server_users",
        joinColumns= @JoinColumn(name = "server_id"),
        inverseJoinColumns= @JoinColumn(name = "user_id")
    )
    private Set<User> users = new HashSet<>();

    @OneToMany(mappedBy = "server",fetch = FetchType.LAZY, cascade=CascadeType.ALL)
    private Set<Role> roles = new HashSet<>();

    private String description; // Description of the server
    private String avatarUrl; // URL to the server's avatar image


    // public Set<Role> getRoles() {
    //     return roles;
    // }

    // public void setRoles(Set<Role> roles) {
    //     this.roles = roles;
    // }

    // public long getId() {
    //     return id;
    // }
    
    // public void setId(long id) {
    //     this.id = id;
    // }
    
    // public String getName() {
    //     return name;
    // }
    
    // public void setName(String name) {
    //     this.name = name;
    // }
    
    // public List<Channel> getChannels() {
    //     return channels;
    // }
    
    // public void setChannels(List<Channel> channels) {
    //     this.channels = channels;
    // }
    
    // public Set<User> getUsers() {
    //     return users;
    // }
    
    // public void setUsers(Set<User> users) {
    //     this.users = users;
    // }
}