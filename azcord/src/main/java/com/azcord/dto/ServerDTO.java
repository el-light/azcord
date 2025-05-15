package com.azcord.dto;

import java.util.List;

import lombok.Data;
import com.azcord.dto.UserSimpleDTO;

@Data
public class ServerDTO {

    private long server_id; 
    private String name; 
    private List<ChannelDTO> channels; 
    private List<String> members; 
    private String avatarUrl; // URL to the server's avatar image
    private String description; // Description of the server
    private UserSimpleDTO owner;
    
    // public long getServer_id() {
    //     return server_id;
    // }

    // public void setServer_id(long server_id) {
    //     this.server_id = server_id;
    // }

    // public String getName() {
    //     return name;
    // }

    // public void setName(String name) {
    //     this.name = name;
    // }

    // public List<ChannelDTO> getChannels() {
    //     return channels;
    // }

    // public void setChannels(List<ChannelDTO> channels) {
    //     this.channels = channels;
    // }

    // public List<String> getMembers() {
    //     return members;
    // }

    // public void setMembers(List<String> members) {
    //     this.members = members;
    // }
}
