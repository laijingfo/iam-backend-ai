package com.lenovo.service;

import com.lenovo.bean.PageBean;
import com.lenovo.bean.UserMenuBean;
import com.lenovo.security.service.dto.AuthorityDto;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.entity.Role;
import com.lenovo.bean.UserBean;
import java.util.List;

/**
 * 角色接口类
 *
 * @author iyesking
 * @date 2023-02-09
 */
public interface RoleService {


    /**
     * @return
     * @Description 自定义分页
     * @Param
     **/
    PageBean<Role> selectByPage(int currentPage, int pageSize, Role role);

    /**
     * 查询角色全部数据
     *
     * @return /
     */
    List<Role> queryAll();

    /**
     * 查询角色详情
     *
     * @return /
     */
    Role getOne(String id);

    /**
     * 查看当前角色是否存在
     * @param name
     * @return
     */
    Role getName(String name,String roleId, Integer workspaceId);

    /**
     * 创建角色
     *
     * @param role /
     * @return /
     */
    void create(Role role);

    /**
     * 编辑角色
     *
     * @param role /
     */
    void modifyById(Role role);

    /**
     * 删除角色
     *
     * @param id /
     */
    String deleteById(String id);

    /**
     * 重置用户状态
     * @param id
     */
    void resetStatus(String id);


    /**
     * 获取所有菜单以及当前角色包含的权限
     * @param roleId
     * @return
     */
    List<UserMenuBean>  findUserMenuByRoleId(String roleId);

    /**
     * 获取当前角色所拥有的菜单
     * @param roleId
     * @return
     */
    List<UserMenuBean>  findMenuByRoleId( String roleId);

    List<UserMenuBean>  findMenuByRoleIdAndCheckTrue( String roleId);

    List<UserBean>  findUserListByRole(String roleId);


    Role detail(String roleId);

    List<AuthorityDto> mapToGrantedAuthorities(UserLoginDto user);


    List<AuthorityDto> itcodeToGrantedAuthorities(String itcode);


    String findUserByRoles(String roleId);

    List<String> getOrganizeListByItcode(String itcode);

}
