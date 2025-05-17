package com.azcord.dto;

import java.util.List;

import lombok.Data;

@Data
public class ServerDTO {

    private long server_id; 
    private String name; 
    private List<ChannelDTO> channels; 
    private List<String> members; 
    private String avatarUrl; // URL to the server's avatar image
    private String description; // Description of the server
    

}
