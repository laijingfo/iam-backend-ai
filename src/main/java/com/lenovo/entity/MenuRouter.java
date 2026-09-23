package com.lenovo.entity;


import lombok.Data;

@Data
public class MenuRouter {

    private Integer id;

    private String menuKey;

    private String menuPath;

    private String menuUrl;

    private Integer parentId;

    private Integer level;

    private Integer rank;

    private Boolean show;

    private String description;

    private Boolean menuFlag;

    private Boolean writeable;

    // 0、默认、1、platform
    private Integer menuType;

    //菜单打开方式：window，inside
    private String openType;

    //菜单类型：内部菜单：Interior，外部菜单 ： Custom
    private String type;

    private String icon;

    private String nameEn;

    private String nameCn;

    private String fullUrl;
}
