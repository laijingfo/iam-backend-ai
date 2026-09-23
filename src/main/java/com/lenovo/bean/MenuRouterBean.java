package com.lenovo.bean;


import com.lenovo.entity.MenuRouter;

import java.util.List;

public class MenuRouterBean {

    private MenuRouter menu;

    private List<MenuRouterBean> subMenus;

    private boolean writeable;

    public MenuRouterBean() {
    }

    public MenuRouterBean(MenuRouter menu) {
        this.menu = menu;
        this.writeable = true;
    }

    public MenuRouterBean(MenuRouter menu, List<MenuRouterBean> subMenus) {
        this.menu = menu;
        this.subMenus = subMenus;
        this.writeable = true;
    }

    public MenuRouterBean(MenuRouter menu, List<MenuRouterBean> subMenus, boolean writeable) {
        this.menu = menu;
        this.subMenus = subMenus;
        this.writeable = writeable;
    }

    public MenuRouter getMenu() {
        return menu;
    }

    public void setMenu(MenuRouter menu) {
        this.menu = menu;
    }

    public List<MenuRouterBean> getSubMenus() {
        return subMenus;
    }

    public void setSubMenus(List<MenuRouterBean> subMenus) {
        this.subMenus = subMenus;
    }

    public boolean isWriteable() {
        return writeable;
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }
}
