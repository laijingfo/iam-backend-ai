package com.lenovo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 用户实体
 *
 * @author iyesking
 * @date 2023-02-07
 */
@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"})
public class User {

    private String id;

    @NotEmpty(message = "用户名不能为空")
    @Length(min = 8,max = 18,message = "User Name Limit input to 8~18 characters")
    private String userName;

    //@NotEmpty(message = "itcode不能为空")
    private String itcode;

    //("Inside:内部账号，Outside:外部账号")
    //@NotEmpty(message = "账号类型不能为空")
    private String type;

    //("联系方式")
    //@NotEmpty(message = "联系方式不能为空")
    private String phoneNumber;

    //@NotEmpty(message = "邮箱不能为空")
    private String mail;

    //("描述")
    private String description;

    private String createTime;

    private String updateTime;

    //("true、启动false、禁用")
    @NotNull(message = "状态不能为空")
    private boolean status  = true;
    //是否删除
    private boolean delete = false;

    //@Length(min = 8,max = 18,message = "密码限制输入8~18个字符")
    private String password;

    private Set<Long> roleSet;
    private List<Role> roles;

    private String dateRange;

    // itsc增加的用户或者角色对应的组织数据
    private List<String> organizeRange;

    //当前登陆账户的itcode
    private String currentLoggedUserItCode;

    public String getCurrentLoggedUserItCode()
    {
        return currentLoggedUserItCode;
    }

    public void setCurrentLoggedUserItCode(String currentLoggedUserItCode)
    {
        this.currentLoggedUserItCode = currentLoggedUserItCode;
    }

    public Set<Long> getRoleSet() {
        return roleSet;
    }

    public void setRoleSet(Set<Long> roleSet) {
        this.roleSet = roleSet;
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


    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public List<String> getOrganizeRange() {
        return organizeRange;
    }

    public void setOrganizeRange(List<String> organizeRange) {
        this.organizeRange = organizeRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return status == user.status &&
                delete == user.delete &&
                Objects.equals(id, user.id) &&
                Objects.equals(userName, user.userName) &&
                Objects.equals(itcode, user.itcode) &&
                Objects.equals(type, user.type) &&
                Objects.equals(phoneNumber, user.phoneNumber) &&
                Objects.equals(mail, user.mail) &&
                Objects.equals(description, user.description) &&
                Objects.equals(createTime, user.createTime) &&
                Objects.equals(updateTime, user.updateTime) &&
                Objects.equals(password, user.password) &&
                Objects.equals(roles, user.roles) &&
                Objects.equals(dateRange, user.dateRange) &&
                Objects.equals(organizeRange, user.organizeRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, userName, itcode, type, phoneNumber, mail, description, createTime, updateTime, status, delete, password, roles, dateRange, organizeRange);
    }
}
