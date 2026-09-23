package com.lenovo.mapper;

import com.lenovo.entity.MenuRouter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface MenuRouterMapper {
    List<MenuRouter> findAll();

    List<MenuRouter> findByUserRole(@Param("roleName")String roleName);

    List<MenuRouter> findInIds(List<Integer> ids);

    List<MenuRouter> findByUserId(@Param("userId") String userId, @Param("isMenu") Boolean isMenu);

    List<MenuRouter> findByDefaultUser();

    void addMenuRouter(MenuRouter menuRouter);

    Integer updateMenuRouter(MenuRouter menuRouter);

    void updateMenuRouterShow(@Param("ids") Set<Integer> ids);

    void delMenuRouter(Integer menuId);

    MenuRouter getOne(String menukey);

    Integer  getMenuRank(Integer menuParentId);

    /**
     * 菜单上移拿到区间的菜单
     *
     * @param menuId
     * @param sort
     * @return
     */
    List<MenuRouter> getMenuRouterByHeadSort(@Param("menuId") Integer menuId, @Param("sort") Integer sort);

    /**
     * 菜单下移拿到区间的菜单
     *
     * @param menuId
     * @param sort
     * @return
     */
    List<MenuRouter> getMenuRouterByTailSort(@Param("menuId") Integer menuId, @Param("sort") Integer sort);


    /**
     * 置顶
     */
    void menuRouterHead(Integer menuId);

    /**
     * 置尾
     */
    void menuRouterTail(Integer menuId);


    void updateMenuRouterSort(List<MenuRouter> list);
}
