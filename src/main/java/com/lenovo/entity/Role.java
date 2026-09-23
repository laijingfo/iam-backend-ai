package com.lenovo.entity;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lenovo.bean.RoleUserBean;
import com.lenovo.bean.UserMenuBean;

import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 角色Entity
 *
 * @author iyesking
 * @date 2023-02-09
 */
@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"})
public class Role {


    private Integer id;

    @Size(min = 1, max = 64, message = "Role Name  limited  64 characters!")
    private String name;

    @Size(max = 300, message = "Description limited 300 characters!")
    private String description;

    private Boolean status = true;

    private String createTime;

    private String updateTime;

    private Boolean delete;

    private Boolean internal;

    private Integer workspaceId;

    private List<UserMenuBean> roleMenuBeans;

    private Set<RoleUserBean> userItem;

    private String menuKeys;

    private Set<String> ids;

    private Boolean isAdmin;

    private String users;

    private String menus;

    private List<String> organizeItem;

    private String delegationType; //授权类型，用于标记当前权限是被授权的

    private String delegator;//授权人

    private List<Role> roleList;//存储在redis中的当前用户具备的角色

    private String cmdbIdOfUarProcesser;

    private String cmdbIdOfOperationOwnerFocalRole;

    public String getCmdbIdOfUarProcesser() {
        return cmdbIdOfUarProcesser;
    }

    public void setCmdbIdOfUarProcesser(String cmdbIdOfUarProcesser) {
        this.cmdbIdOfUarProcesser = cmdbIdOfUarProcesser;
    }

    public String getCmdbIdOfOperationOwnerFocalRole() {
        return cmdbIdOfOperationOwnerFocalRole;
    }

    public void setCmdbIdOfOperationOwnerFocalRole(String cmdbIdOfOperationOwnerFocalRole) {
        this.cmdbIdOfOperationOwnerFocalRole = cmdbIdOfOperationOwnerFocalRole;
    }

    public List<Role> getRoleList() {
        return roleList;
    }

    public void setRoleList(List<Role> roleList) {
        this.roleList = roleList;
    }

    // 核心修复：初始化 userItem 为空 HashSet，避免 null 导致序列化异常
    public Role() {
        this.userItem = new HashSet<>(); // 关键：不再是 null，序列化时会生成 [] 或合法格式
    }

    public String getDelegationType() {
        return delegationType;
    }

    public void setDelegationType(String delegationType) {
        this.delegationType = delegationType;
    }

    public String getDelegator() {
        return delegator;
    }

    public void setDelegator(String delegator) {
        this.delegator = delegator;
    }

    public String getMenuKeys() {
        return menuKeys;
    }

    public void setMenuKeys(String menuKeys) {
        this.menuKeys = menuKeys;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public @Size(min = 1, max = 64, message = "Role Name  limited  64 characters!") String getName() {
        return name;
    }

    public void setName(@Size(min = 1, max = 64, message = "Role Name  limited  64 characters!") String name) {
        this.name = name;
    }

    public @Size(max = 300, message = "Description limited 300 characters!") String getDescription() {
        return description;
    }

    public void setDescription(@Size(max = 300, message = "Description limited 300 characters!") String description) {
        this.description = description;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
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

    public Boolean getDelete() {
        return delete;
    }

    public void setDelete(Boolean delete) {
        this.delete = delete;
    }

    public Boolean getInternal() {
        return internal;
    }

    public void setInternal(Boolean internal) {
        this.internal = internal;
    }

    public Integer getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Integer workspaceId) {
        this.workspaceId = workspaceId;
    }

    public List<UserMenuBean> getRoleMenuBeans() {
        return roleMenuBeans;
    }

    public void setRoleMenuBeans(List<UserMenuBean> roleMenuBeans) {
        this.roleMenuBeans = roleMenuBeans;
    }

    public Set<RoleUserBean> getUserItem() {
        return userItem;
    }

    public void setUserItem(Set<RoleUserBean> userItem) {
        this.userItem = userItem;
    }

    public Set<String> getIds() {
        return ids;
    }

    public void setIds(Set<String> ids) {
        this.ids = ids;
    }

    public String getUsers() {
        return users;
    }

    public void setUsers(String users) {
        this.users = users;
    }

    public String getMenus() {
        return menus;
    }

    public void setMenus(String menus) {
        this.menus = menus;
    }

    public List<String> getOrganizeItem() {
        return organizeItem;
    }

    public void setOrganizeItem(List<String> organizeItem) {
        this.organizeItem = organizeItem;
    }

    public Boolean getAdmin() {
        return isAdmin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
    }
}
