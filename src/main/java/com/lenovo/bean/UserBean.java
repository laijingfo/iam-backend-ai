package com.lenovo.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lenovo.entity.Role;

import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.Set;

/**
 * 用户实体
 *
 * @author iyesking
 * @date 2023-02-07
 */

@JsonIgnoreProperties(value = { "hibernateLazyInitializer", "handler" })
public class UserBean implements Serializable {

    private String id;

    @NotEmpty(message = "用户名不能为空")
    private String userName;

    private String itcode;

    private String type;

    //    ("联系方式")
    private String phoneNumber;

    private String mail;

    //    ("描述")
    private String description;

    private String createTime;

    private String updateTime;

    //    ("1、启动0、禁用")
    private boolean status;
    // 是否删除
    private boolean delete = false;

    private String password;

    private String dateRange;

    private Set<Role> roles;


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

    public String getItcode() {
        return itcode;
    }

    public void setItcode(String itcode) {
        this.itcode = itcode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getMail() {
        return mail;
    }

    public void setMail(String mail) {
        this.mail = mail;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public boolean getStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }


    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }
}
