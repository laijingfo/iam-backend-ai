package com.lenovo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.*;
import com.lenovo.bean.factory.RoleRetrievalStrategy;
import com.lenovo.bean.factory.RoleStrategyFactory;
import com.lenovo.bean.factory.RoleStrategyType;
import com.lenovo.bean.factory.RoleUtils;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.controller.BaseController;
import com.lenovo.entity.*;
import com.lenovo.mapper.*;
import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.service.UserCacheManager;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.security.utils.RsaUtils;
import com.lenovo.service.RoleService;
import com.lenovo.service.UserService;
import com.lenovo.util.RedisUtils;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 用户实现类
 *
 * @author iyesking
 * @date 2023-02-07
 */
@Service("userService")
@AllArgsConstructor
public class UserServiceImpl extends BaseController implements UserService
{
    private final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RedisUtils redisUtils;
    private final SecurityProperties securityProperties;
    //private final NotifyHelper notifyHelper;
    private final UserCacheManager userCacheManager;
    private final BPOMapper bpoMapper;
    private final DelegationMapper delegationMapper;
    private final RoleService roleService;
    private final ItsApplicationDataMapper itsApplicationDataMapper;
    private final RoleStrategyFactory strategyFactory;


    /**
     * 自定义分页
     *
     * @param user 分页条件
     * @return /
     */
    @Override
    public PageBean<UserBean> selectByPage(int currentPage, int pageSize, User user) {
        List<UserBean> userList = userMapper
                .selectByPage(Page.of(currentPage, pageSize, false), user)
                .getRecords();
        PageBean<UserBean> pageData = new PageBean<>(currentPage, pageSize, userMapper.countByPage(user));
        pageData.setItems(userList);
        return pageData;
    }

    /**
     * 创建用户
     *
     * @param user /
     * @return /
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public ResponseEntity create(User user) {
        userMapper.create(user);
        roleMapper.addUserRole(user);
        return ok("ok");
    }

    /**
     * 编辑用户
     *
     * @param user /
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public ResponseEntity modifyById(User user) {

        try {
            userMapper.modifyById(user);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("itcode重复");
        }
        roleMapper.delUserRole(user.getId());
        roleMapper.addUserRole(user);
        userCacheManager.cleanUserCache(user.getUserName());
        return ok("ok");

    }

    @Override
    public User modifyAdfsUserRole(User user)
    {
        //先去sys_user_roles表根据user_id删除该数据
        roleMapper.delUserRole(user.getId());
        //user.getRoleSet()是空就代表删除当前账户，不是空就是新增和修改
        if (user.getRoleSet().size() != 0)
        {
            roleMapper.addAFDFSUserRole(user);
        }
        userCacheManager.cleanUserCache(user.getUserName());
        return user;

        //roleMapper.delUserRole(user.getId());
        //return user;
    }

    @Override
    public User getAdfsUser(String itcode)
    {
        // 初始化角色列表
        List<Role> roleListForCurrentRole = new ArrayList<>();

        // 1. 执行动态授权策略 : 去user_access_review（用户当前审核表）获取用户角色（动态授权）
        RoleRetrievalStrategy dynamicAuthStrategy = strategyFactory.getStrategy(RoleStrategyType.DYNAMIC_AUTH);
        roleListForCurrentRole.addAll(dynamicAuthStrategy.retrieveRoles(itcode, roleListForCurrentRole));

        // 2. 执行委托授权策略 : 去授权表delegation表查询当前用户是否被授权
        RoleRetrievalStrategy delegationStrategy = strategyFactory.getStrategy(RoleStrategyType.DELEGATION);
        roleListForCurrentRole.addAll(delegationStrategy.retrieveRoles(itcode, roleListForCurrentRole));

        // 3. 执行用户角色表策略 : 去sys_user_roles表查询是否存在(去用户管理里面去查)
        RoleRetrievalStrategy userRoleTableStrategy = strategyFactory.getStrategy(RoleStrategyType.USER_ROLE_TABLE);
        roleListForCurrentRole.addAll(userRoleTableStrategy.retrieveRoles(itcode, roleListForCurrentRole));

        // 4. 执行UARProcesser策略 : 去its_application_data表查询判断是否是UARProcesser(目前要保留为了以后有需要的用户去自己发邮件)
        RoleRetrievalStrategy uarProcesserStrategy = strategyFactory.getStrategy(RoleStrategyType.UAR_PROCESSER);
        roleListForCurrentRole.addAll(uarProcesserStrategy.retrieveRoles(itcode, roleListForCurrentRole));

        // 5. 执行OPERATION_OWNER_FOCAL策略 : 去its_application_data表查询判断是否是operation_owner或operation_focal
        //    新增加了一个权限：应用负责人权限：its_application_data表的application_it_owner也要拥有该权限 2026-06-26
        RoleRetrievalStrategy operationOwnerFocalStrategy = strategyFactory.getStrategy(RoleStrategyType.OPERATION_OWNER_FOCAL);
        roleListForCurrentRole.addAll(operationOwnerFocalStrategy.retrieveRoles(itcode, roleListForCurrentRole));

        // 6. 删除当前角色列表中的默认角色Access_User；角色列表中如果有多个角色，则Access_User角色已经无意义所以删除。
        //roleListForCurrentRole = RoleUtils.isOnlyContainAccessUser(roleListForCurrentRole);

        // 7. 组装用户信息并返回
        User userReturn = userMapper.getAdfsUserByItcode(itcode);
        userReturn.setRoles(roleListForCurrentRole);
        setUserRoleToRedis(itcode, roleListForCurrentRole);
        return userReturn;
    }

    /**
     * @Description TODO 将用户角色信息放到redis中
     * @author wangfenglong
     * @date 2025/11/20 12:36
     **/
    private void setUserRoleToRedis(String itcode,List<Role> roleList)
    {
        Role role = new Role();
        role.setRoleList(roleList);
        long sessionSeconds = securityProperties.getTokenValidityInSeconds() / 1000;
        // 每次登录重新计算角色并刷新 TTL，使角色缓存与登录会话同时过期。
        redisUtils.hset(itcode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,
                itcode, role, sessionSeconds);
    }



    /**
     * 删除用户
     *
     * @param id /
     */
    @Override
    public void deleteById(String id) {
        User user = this.getOne(id);
        if (Objects.nonNull(user)) {
            userMapper.deleteById(id);
            userCacheManager.cleanUserCache(user.getUserName());
        }

    }

    /**
     * 获取用户详情
     *
     * @param id /
     */
    @Override
    public User getOne(String id) {
        return userMapper.getOne(id);
    }

    @Override
    public List<RoleMenuBean> findRoleMenuPermissionByUserId(String id) {
        return userMapper.findRoleMenuPermissionByUserId(id);
    }

    /**
     * 重置密码
     *
     * @param id
     */
    @Override
    public void resetPassword(String id, String password) {
        User user = getOne(id);
        if (Objects.nonNull(user)) {
            userMapper.resetPassword(id, password);
            flushCache(user.getUserName());
            // TODO 发邮件
        } else {
            throw new BadRequestException("用户不存在");
        }

    }

    /**
     * 重置用户状态
     *
     * @param id
     */
    @Override
    public void resetStatus(String id) {
        User user = this.getOne(id);
        if (Objects.nonNull(user)) {
            userMapper.resetStatus(id);
            userCacheManager.cleanUserCache(user.getUserName());
        }

    }

    @Override
    public List<User> getADFSUserItem(String name) {
        return userMapper.getADFSUserItem(name);
    }

    /**
     * 获取菜单列表及当前用户可操作的权限
     *
     * @param id 用户id
     * @return
     */
    @Override
    public List<UserMenuBean> findUserMenuByUserId(String id) {

        List<UserMenuBean> userMenuBeans = userMapper.findUserMenuByUserId(id);
        return treeMenu(userMenuBeans);
    }

    private List<UserMenuBean> treeMenu(List<UserMenuBean> userMenuBeans) {
        List<UserMenuBean> newMenuBeans = new ArrayList<>();
        userMenuBeans.stream().collect(Collectors.groupingBy(UserMenuBean::getMenuId)).forEach((key, item) -> {
            if (Objects.nonNull(item) && item.size() == 2) {
                newMenuBeans.addAll(item.stream().filter(t -> t.getReadAndWrite()).collect(Collectors.toList()));
            } else {
                newMenuBeans.addAll(item);
            }
        });

        Map<Integer, List<UserMenuBean>> groupMenus = newMenuBeans.stream()
                .filter(menuRouterBean -> Objects.nonNull(menuRouterBean.getParentId()))
                .collect(Collectors.groupingBy(menuRouterBean -> menuRouterBean.getParentId()));

        List<UserMenuBean> treeMenus = newMenuBeans.stream()
                .filter(menuRouterBean -> Objects.isNull(menuRouterBean.getParentId())).collect(Collectors.toList());

        treeMenus.forEach(s -> fillSubMenus(s, groupMenus));

        return treeMenus;
    }


    @Override
    public UserLoginDto getLoginData(String userName) {
        return userMapper.getLoginData(userName);
    }

    @Override
    public User findByName(String userName) {
        return userMapper.findByName(userName);

    }

    /**
     * 修改密码
     *
     * @param userName
     * @param newPass
     */
    @Override
    public void updatePass(String userName, String newPass) {
        userMapper.updatePass(userName, newPass);
        flushCache(userName);
    }

    @Override
    public User getOneByUserName(String userName, Boolean delete, String userId) {
        return userMapper.findByUserNameAndDelete(userName, delete, userId);
    }

    /**
     * 找回密码
     *
     * @param email
     */
    @Override
    public void forgotPassword(String email) throws Exception {
        List<User> accountList = userMapper.findAccountByEmail(email);
        if (CollectionUtils.isEmpty(accountList)) {
            throw new BadRequestException("Unable to find the associated mailbox account!");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("email", email);
        map.put("item", accountList);
        redisUtils.hset("forgot", email, map, 30 * 60 * 1000);
    }

    private void fillSubMenus(UserMenuBean menuBean, Map<Integer, List<UserMenuBean>> groupMenus) {
        List<UserMenuBean> subMenus = groupMenus.get(menuBean.getMenuId());
        if (subMenus != null) {
            menuBean.setSubMenus(subMenus);
            subMenus.forEach(s -> fillSubMenus(s, groupMenus));
        }
    }

    /**
     * 清理 登陆时 用户缓存信息
     *
     * @param username /
     */
    private void flushCache(String username) {
        userCacheManager.cleanUserCache(username);
    }

    @Override
    public UserDomainBean getUserDomain(String itcode) {
        UserDomainBean bean = userMapper.getAdfsUserDomainByItcode(itcode);
        if (Objects.nonNull(bean)) {
            bean.setWhiteListUser(false);
        }
        return bean;
    }

    @Override
    public List<User> queryAll(String name, String type, int page, int size) {
        return userMapper.queryAll(name, type, page, size);
    }

    @Override
    public Page<UserBean> queryByRole(String name, String roleName, Integer workspaceId, int page, int size) {
        return userMapper.queryByRole(new Page<>(page, size), name, roleName, workspaceId);
    }
}
