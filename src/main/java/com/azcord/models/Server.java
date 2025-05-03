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
public class Server {
    
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private long id;

    @NotBlank
    @Column(unique= true)
    private String name; 

    //we don't specifically need channels to exist in our server intitially
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Channel> channels = new ArrayList<>(); 

    @ManyToMany(fetch = FetchType.LAZY,cascade= CascadeType.ALL)
    @JoinTable(
        name = "server_users",
        joinColumns= @JoinColumn(name = "server_id"),
        inverseJoinColumns= @JoinColumn(name = "user_id")
    )
    private Set<User> users = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, cascade=CascadeType.ALL)
    private Set<Role> roles = new HashSet<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public void setChannels(List<Channel> channels) {
        this.channels = channels;
    }

    public Set<User> getUsers() {
        return users;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }
}
