package com.lenovo.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.lenovo.bean.MenuRouterBean;
import com.lenovo.constant.HttpConstants;
import com.lenovo.entity.MenuRouter;
import com.lenovo.mapper.MenuRouterMapper;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.MenuRouterService;
import com.lenovo.util.AmazonUtil;
import com.lenovo.util.RedisUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MenuRouterServiceImpl implements MenuRouterService {

    private final MenuRouterMapper menuRouterMapper;
    private final RedisUtils redisUtils;

    public MenuRouterServiceImpl(MenuRouterMapper menuRouterMapper, RedisUtils redisUtils) {
        this.menuRouterMapper = menuRouterMapper;
        this.redisUtils = redisUtils;
    }

    @Override
    public List<MenuRouterBean> menus(Boolean isMenu, Boolean defaultPage) {
        List<MenuRouter> menus = null;
        if (defaultPage) {
            menus = menuRouterMapper.findAll();
        } else {
            menus = this.findMenuListByUserId(SecurityUtils.getCurrentUserId());
        }

        // MenuRouterBean 包含了读写的控制
        List<MenuRouterBean> menuRouterBeans = menus.stream().map(MenuRouterBean::new).collect(Collectors.toList());

        // 按照parent id 分组
        Map<Integer, List<MenuRouterBean>> groupMenus = menuRouterBeans.stream()
                .filter(menuRouterBean -> menuRouterBean.getMenu().getParentId() != null)
                .collect(Collectors.groupingBy(menuRouterBean -> menuRouterBean.getMenu().getParentId()));

        // 根据rank字段排序一级菜单
        List<MenuRouterBean> topMenus = menuRouterBeans.stream()
                .filter(menuRouterBean -> Objects.nonNull(menuRouterBean.getMenu()) && menuRouterBean.getMenu().getParentId() == null).sorted(
                        Comparator.comparing(item -> item.getMenu().getRank())
                ).collect(Collectors.toList());

        topMenus.forEach(s -> fillSubMenus(s, groupMenus));
        return topMenus;
    }


    @Override
    public List<MenuRouter> findMenuListByUserId(String userId) {
        List<MenuRouter> menuRouterList = null;
        Object ob = redisUtils.hget(HttpConstants.MENU_KEY, userId);
        if (redisUtils.hasKey(HttpConstants.MENU_KEY) && Objects.nonNull(ob)) {
            menuRouterList = JSONObject.parseArray(JSON.toJSONString(ob), MenuRouter.class);
        } else {
            menuRouterList = menuRouterMapper.findByUserId(SecurityUtils.getCurrentUserId(), null);
            if (menuRouterList.isEmpty()) {
                menuRouterList = menuRouterMapper.findByDefaultUser();
            }
            redisUtils.hset(HttpConstants.MENU_KEY, userId, menuRouterList, -1);
        }
        return menuRouterList;
    }

    public List<MenuRouterBean> findMenuListByUserRole(String userRole) {
        List<MenuRouter> menus = menuRouterMapper.findByUserRole(userRole);
        // MenuRouterBean 包含了读写的控制
        List<MenuRouterBean> menuRouterBeans = menus.stream().map(MenuRouterBean::new).collect(Collectors.toList());

        // 按照parent id 分组
        Map<Integer, List<MenuRouterBean>> groupMenus = menuRouterBeans.stream()
                .filter(menuRouterBean -> menuRouterBean.getMenu().getParentId() != null)
                .collect(Collectors.groupingBy(menuRouterBean -> menuRouterBean.getMenu().getParentId()));

        // 根据rank字段排序一级菜单
        List<MenuRouterBean> topMenus = menuRouterBeans.stream()
                .filter(menuRouterBean -> Objects.nonNull(menuRouterBean.getMenu()) && menuRouterBean.getMenu().getParentId() == null).sorted(
                        Comparator.comparing(item -> item.getMenu().getRank())
                ).collect(Collectors.toList());

        topMenus.forEach(s -> fillSubMenus(s, groupMenus));
        return topMenus;
    }

    @Override
    public MenuRouter addMenuRouter(MenuRouter menuRouter) {
        MenuRouter router = menuRouterMapper.getOne(menuRouter.getMenuKey());
        if (Objects.nonNull(router)) {
            throw new BadRequestException("The menuKey must be unique");
        }
        Integer rank = menuRouterMapper.getMenuRank(menuRouter.getParentId());
        menuRouter.setRank(Objects.nonNull(rank) ? rank + 1 : 1);
        menuRouterMapper.addMenuRouter(menuRouter);
        this.cleanMenuCache();
        return menuRouter;
    }

    @Override
    public MenuRouter updateMenuRouter(MenuRouter menuRouter) {
        menuRouterMapper.updateMenuRouter(menuRouter);
        this.cleanMenuCache();
        return menuRouter;
    }

    @Override
    public void delMenuRouter(Integer menuId) {
        menuRouterMapper.delMenuRouter(menuId);
        this.cleanMenuCache();
    }

    @Override
    public void updateMenuRouterShow(Set<Integer> ids) {
        menuRouterMapper.updateMenuRouterShow(ids);
        this.cleanMenuCache();
    }

    @Override
    public List<MenuRouter> updateMenuRouterSort(List<MenuRouterBean> list) {
        List<MenuRouter> routerList = treeToList(list, null);
        menuRouterMapper.updateMenuRouterSort(routerList);
        this.cleanMenuCache();
        return routerList;
    }


    /**
     * 树形结构转list
     *
     * @param
     * @return
     */
    private List<MenuRouter> treeToList(List<MenuRouterBean> list, Integer pid) {

        List<MenuRouter> resultList = new ArrayList<>();

        for (MenuRouterBean menuRouterBean : list) {
//            menuRouterBean.getMenu().setParentId(pid);
            if (menuRouterBean.getSubMenus() != null && menuRouterBean.getSubMenus().size() > 0) {
                resultList.addAll(treeToList(menuRouterBean.getSubMenus(), menuRouterBean.getMenu().getParentId()));
                menuRouterBean.setSubMenus(null);
                resultList.add(menuRouterBean.getMenu());
            } else {
                resultList.add(menuRouterBean.getMenu());
            }
        }
        return resultList;
    }

    /**
     * 置顶
     *
     * @param menuId
     */
    @Override
    public void menuRouterHead(Integer menuId) {
        menuRouterMapper.menuRouterHead(menuId);
        this.cleanMenuCache();
    }

    /**
     * 置尾
     *
     * @param menuId
     */
    @Override
    public void menuRouterTail(Integer menuId) {
        menuRouterMapper.menuRouterTail(menuId);
        this.cleanMenuCache();
    }

    /**
     * 拖动
     *
     * @param menuId
     * @param sort
     */
    @Override
    public void menuRouterDrag(Integer menuId, Integer sort) {


        this.cleanMenuCache();
    }

    @Override
    public String uploadImage(MultipartFile file) {
        try {
            return AmazonUtil.uploadFile(file).get("url");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 清楚菜单缓存
     */
    @Override
    public void cleanMenuCache() {
        redisUtils.del(HttpConstants.MENU_KEY);
    }

    private void fillSubMenus(MenuRouterBean menuBean, Map<Integer, List<MenuRouterBean>> groupMenus) {
        List<MenuRouterBean> subMenus = groupMenus.get(menuBean.getMenu().getId());
        if (subMenus != null) {
            menuBean.setSubMenus(subMenus.stream().sorted(Comparator.comparing(item -> item.getMenu().getRank())).collect(Collectors.toList()));
            subMenus.forEach(s -> fillSubMenus(s, groupMenus));
        }
    }
}
