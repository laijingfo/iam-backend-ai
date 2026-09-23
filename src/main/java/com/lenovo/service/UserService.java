package com.lenovo.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.*;
import com.lenovo.entity.User;
import com.lenovo.security.service.dto.UserLoginDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 用户Service
 *
 * @author iyesking
 * @date 2023-02-07
 */
public interface UserService {

    /**
     * @return
     * @Description 自定义分页
     * @Param
     **/
    PageBean<UserBean> selectByPage(int currentPage, int pageSize, User user);

    /**
     * 创建用户
     *
     * @param user /
     * @return /
     */
    ResponseEntity create(User user);

    /**
     * 编辑用户
     *
     * @param user /
     */
    ResponseEntity modifyById(User user);

    User modifyAdfsUserRole(User user);

    User getAdfsUser(String itcode);

    /**
     * 删除用户
     *
     * @param id /
     */
    void deleteById(String id);


    User getOne(String id);


    List<RoleMenuBean> findRoleMenuPermissionByUserId(String id);

    /**
     * 重置密码
     *
     * @param id
     */
    void resetPassword(String id, String password);

    /**
     * 重置用户状态
     *
     * @param id
     */
    void resetStatus(String id);


    List<User> getADFSUserItem(String name);


    /**
     * 获取菜单列表及当前用户可操作的权限
     *
     * @param id 用户id
     * @return
     */
    List<UserMenuBean> findUserMenuByUserId(String id);

    UserLoginDto getLoginData(String userName);

    User findByName(String userName);

    /**
     * 修改密码
     *
     * @param userName
     * @param newPass
     */
    void updatePass(String userName, String newPass);

    User getOneByUserName(String userName, Boolean delete, String userId);

    /**
     * 找回密码
     *
     * @param email
     */
    void forgotPassword(String email) throws Exception;


    UserDomainBean getUserDomain(String itcode);

    List<User> queryAll(String name, String type, int page, int size);

    Page<UserBean> queryByRole(String name, String roleName, Integer workspaceId, int page, int size);
}
