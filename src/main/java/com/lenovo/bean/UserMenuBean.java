package com.lenovo.bean;


import lombok.Data;

import java.util.List;


@Data
public class UserMenuBean {
    private Integer menuId;

    private String menuKey;

    private String menuPath;

    private String menuUrl;

    private Integer parentId;

    private Integer level;

    private boolean readAndWrite;

    private boolean checked;

    private boolean menuFlag;

    private Integer rank;

    private String description;

    private Integer menuType;

    private String openType;

    private String type;

    private String icon;

    private Boolean show;

    private List<UserMenuBean> subMenus;



    public Integer getMenuId() {
        return menuId;
    }

    public void setMenuId(Integer menuId) {
        this.menuId = menuId;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public void setMenuKey(String menuKey) {
        this.menuKey = menuKey;
    }

    public String getMenuPath() {
        return menuPath;
    }

    public void setMenuPath(String menuPath) {
        this.menuPath = menuPath;
    }

    public String getMenuUrl() {
        return menuUrl;
    }

    public void setMenuUrl(String menuUrl) {
        this.menuUrl = menuUrl;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public boolean getReadAndWrite() {
        return readAndWrite;
    }

    public void setReadAndWrite(boolean readAndWrite) {
        this.readAndWrite = readAndWrite;
    }

    public List<UserMenuBean> getSubMenus() {
        return subMenus;
    }

    public void setSubMenus(List<UserMenuBean> subMenus) {
        this.subMenus = subMenus;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean getChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public boolean isMenuFlag() {
        return menuFlag;
    }

    public void setMenuFlag(boolean menuFlag) {
        this.menuFlag = menuFlag;
    }


    public Integer getMenuType() {
        return menuType;
    }

    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
    }

    public String getOpenType() {
        return openType;
    }

    public void setOpenType(String openType) {
        this.openType = openType;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Boolean getShow() {
        return show;
    }

    public void setShow(Boolean show) {
        this.show = show;
    }
}
