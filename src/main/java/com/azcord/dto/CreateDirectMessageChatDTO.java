package com.azcord.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CreateDirectMessageChatDTO {
    @NotEmpty(message = "Participant IDs cannot be empty.")
    // For 1-on-1, this will contain one other user ID.
    // For group, multiple user IDs.
    @Size(min = 1, message = "At least one participant ID is required (excluding self).")
    private Set<Long> participantUserIds;

    @Size(max = 100, message = "Group chat name cannot exceed 100 characters.")
    private String groupName; // Optional, for creating a named group DM

    public Set<Long> getParticipantUserIds() {
        return participantUserIds;
    }
    
    public void setParticipantUserIds(Set<Long> participantUserIds) {
        this.participantUserIds = participantUserIds;
    }

    public String getGroupName() {
        return groupName;
    }

    
}