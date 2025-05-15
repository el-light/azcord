package com.azcord.dto;

public class ParentMessageInfoDTO {
    private Long id;
    private String senderUsername;
    private String contentSnippet; //first 50 chars of parent message content
    
    public String getContentSnippet() {
        return contentSnippet;
    }
    public void setContentSnippet(String contentSnippet) {
        this.contentSnippet = contentSnippet;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getSenderUsername() {
        return senderUsername;
    }
    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

}
