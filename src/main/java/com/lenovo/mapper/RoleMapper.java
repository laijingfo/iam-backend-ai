package com.lenovo.mapper;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.RoleUserBean;
import com.lenovo.bean.UserMenuBean;
import com.lenovo.entity.Role;
import com.lenovo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

/**
 * 角色Mapper
 *
 * @author iyesking
 * @date 2023-02-09
 */

@Mapper
public interface RoleMapper {

    void deleteById(@Param("id") String id);
    void deleteByIdTrue(@Param("id") String id);

    void modifyById(@Param("item") Role item);

    void create(@Param("item") Role item);

    void addRoleMenus(@Param("menus") List<UserMenuBean> RoleMenuBeans, @Param("roleId") Integer roleId);

    void delRoleMenus(@Param("roleId") Integer roleId);

    void delRoleUser(@Param("roleId") String roleId);

    Role getOne(@Param("id") String id);
    Role getRoleIdByName(@Param("name") String name);

    Role getName(@Param("name") String name, @Param("roleId") String roleId, @Param("workspaceId") Integer workspaceId);


    void resetStatus(@Param("id") String id);

    void addUserRole(@Param("user") User user);

    void addAFDFSUserRole(@Param("user") User user);

    void addRoleUser(@Param("roleId") Integer roleId, @Param("user") Set<RoleUserBean> user);

    void delUserRole(@Param("id") String id);

    void delADFSUserRole(@Param("id") String id);

    void deleteByWorkspaceId(@Param("workspaceId") Integer workspaceId);

    /**
     * @return
     * @Description 自定义分页
     * @Param
     **/
    Page<Role> selectByPage(Page<Role> page, @Param("item") Role item);


    Integer countByPage(@Param("item") Role item);


    List<Role> selectList();

    List<Role> findRoleByUserId(@Param("id") String id, @Param("type") String type);


    /**
     * 获取所有菜单以及当前角色包含的权限
     *
     * @param roleId
     * @return
     */
    List<UserMenuBean> findUserMenuByRoleId(@Param("roleId") String roleId);

    /**
     * 获取当前角色所拥有的菜单
     *
     * @param roleId
     * @return
     */
    List<UserMenuBean> findMenuByRoleId(@Param("roleId") String roleId);

    List<UserMenuBean> findMenuByRoleIdAndCheckTrue(@Param("roleId") String roleId);


    String findUserByRoles(@Param("roleId") String roleId);

    void saveOrganize(@Param("roleId") Integer roleId, @Param("organizeItem") List<String> organizeItem);

    void deleteOrganizeBySocietyId(@Param("societyId") Integer societyId);

    List<String> findOrganizeItem(String id);
}
