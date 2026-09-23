package com.lenovo.service;


import com.lenovo.bean.MenuRouterBean;
import com.lenovo.entity.MenuRouter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MenuRouterService {
    List<MenuRouterBean> menus(Boolean isMenu, Boolean defaultPage);

    void cleanMenuCache();

    List<MenuRouter> findMenuListByUserId(String userId);

    MenuRouter addMenuRouter(MenuRouter menuRouter);

    MenuRouter updateMenuRouter(MenuRouter menuRouter);

    void delMenuRouter(Integer menuId);

    void updateMenuRouterShow(Set<Integer> ids);

    List<MenuRouterBean> findMenuListByUserRole(String userRole);

    List<MenuRouter> updateMenuRouterSort(List<MenuRouterBean> list);

    /**
     * 置顶
     */
    void menuRouterHead(Integer menuId);

    /**
     * 置尾
     */
    void menuRouterTail(Integer menuId);


    /**
     * 拖动
     */
    void menuRouterDrag(Integer menuId, Integer sort);


    String uploadImage(MultipartFile file);
}
