package com.lenovo.bean;


import java.util.List;

public class UserDomainBean {
    private String id;
    private String currentDomain;
    private List<String> whiteListDomain;
    private boolean whiteListUser;
    private String email;

    public String getCurrentDomain() {
        return currentDomain;
    }

    public void setCurrentDomain(String currentDomain) {
        this.currentDomain = currentDomain;
    }

    public List<String> getWhiteListDomain() {
        return whiteListDomain;
    }

    public void setWhiteListDomain(List<String> whiteListDomain) {
        this.whiteListDomain = whiteListDomain;
    }

    public boolean isWhiteListUser() {
        return whiteListUser;
    }

    public void setWhiteListUser(boolean whiteListUser) {
        this.whiteListUser = whiteListUser;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
