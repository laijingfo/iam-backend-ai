package com.lenovo.bean;



public class RoleUserBean {

    private String type;

    private String id;

    private String userName;


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public RoleUserBean() {
    }

    public RoleUserBean(String type, String id, String userName) {
        this.type = type;
        this.id = id;
        this.userName = userName;
    }
}
