package com.lenovo.mapper;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.RoleMenuBean;
import com.lenovo.bean.UserDomainBean;
import com.lenovo.bean.UserMenuBean;
import com.lenovo.entity.User;
import com.lenovo.bean.UserBean;
import com.lenovo.security.service.dto.UserLoginDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.HashMap;
import java.util.List;

/**
 * 用户mapper
 *
 * @author iyesking
 * @date 2023-02-07
 */

@Mapper
public interface UserMapper {

    void deleteById(@Param("id") String id);

    void modifyById(@Param("item") User item);

    void create(@Param("item") User item);

    User getOne(@Param("id") String id);

    void resetPassword(@Param("id") String id, @Param("password") String password);

    void resetStatus(@Param("id") String id);

    /**
     * @return
     * @Description 自定义分页
     * @Param
     **/
    Page<UserBean> selectByPage(Page<UserBean> page, @Param("item") User item);

    Integer countByPage(@Param("item") User item);

    List<RoleMenuBean> findRoleMenuPermissionByUserId(String id);

    List<RoleMenuBean> findRoleMenuPermissionByItcode(String itcode);

    /**
     * 获取用户下的菜单权限列表，需要去重
     *
     * @param id
     * @return
     */
    List<UserMenuBean> findUserMenuByUserId(String id);

    /**
     * 获取当前角色下的用户列表
     *
     * @param roleId
     * @return
     */
    List<UserBean> findUserListByRole(String roleId);


    List<User> getADFSUserItem(@Param("name") String name);

    void updatePass(@Param("userName") String userName, @Param("newPass") String newPass);

    User getOneByUserName(String userName);

    User findByUserNameAndDelete(@Param("userName") String userName, @Param("delete") Boolean delete, @Param("userId") String userId);

    UserLoginDto getLoginData(String userName);

    User findByName(String userName);

    List<User> findAccountByEmail(String email);

    UserDomainBean getAdfsUserDomainByItcode(String itcode);

    User getAdfsUserByItcode(String itcode);

    List<String> getOrganizeListByItcode(String itcode);

    Integer checkInAdOverview(String itcode);

    List<User> queryAll(@Param("name") String name, @Param("type") String type, @Param("page") int page,
                        @Param("size") int size);

    Page<UserBean> queryByRole(Page<User> page, @Param("name") String name, @Param("roleName") String roleName, @Param("workspaceId") Integer workspaceId);

    void addUser(@Param("userInfo") HashMap jsonObject);
}
