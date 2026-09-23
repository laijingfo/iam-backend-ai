package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.*;
import com.lenovo.entity.Role;
import com.lenovo.mapper.RoleMapper;
import com.lenovo.mapper.UserMapper;
import com.lenovo.security.service.dto.AuthorityDto;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.service.RoleService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色实现类
 *
 * @author iyesking
 * @date 2023-02-09
 */
@Service("roleService")
@AllArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final Logger log = LoggerFactory.getLogger(RoleServiceImpl.class);

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;


    /**
     * 查询角色详情
     *
     * @return /
     */
    @Override
    public Role getOne(String id) {
        return roleMapper.getOne(id);
    }

    /**
     * 查看当前角色是否存在
     *
     * @param name
     * @return
     */
    @Override
    public Role getName(String name, String roleId, Integer workspaceId) {
        return roleMapper.getName(name, roleId, workspaceId);
    }

    /**
     * 自定义分页
     *
     * @param role 分页条件
     * @return /
     */
    @Override
    public PageBean<Role> selectByPage(int currentPage, int pageSize, Role role) {
        List<Role> userList = roleMapper
                .selectByPage(Page.of(currentPage, pageSize, false), role)
                .getRecords();
        PageBean<Role> pageData = new PageBean<>(currentPage, pageSize, roleMapper.countByPage(role));
        pageData.setItems(userList);

        pageData.getItems().stream().forEach(item -> {

            Map<Integer, List<UserMenuBean>> map = getTreeMenu(item.getRoleMenuBeans()).stream().collect(Collectors.groupingBy(UserMenuBean::getLevel));
            item.setRoleMenuBeans(null);
            item.setMenus(!map.containsKey(1) ? "-" : map.get(1).stream().map(r -> {
                if (r.getChecked()) {
                    return r.getMenuKey() + ";";
                } else {
                    return this.getChildMenu(r, new ArrayList<>()).stream().map(UserMenuBean::getMenuKey).collect(Collectors.joining("/")) + ";";
                }
            }).collect(Collectors.joining()));
        });
        return pageData;
    }

    private List<UserMenuBean> getChildMenu(UserMenuBean menuBean, List<UserMenuBean> userMenuBeans) {
        if (Objects.nonNull(menuBean.getSubMenus())) {
            menuBean.getSubMenus().stream().forEach(item -> {
                if (Objects.isNull(item.getSubMenus())) {
                    userMenuBeans.add(item);
                } else {
                    getChildMenu(item, userMenuBeans);
                }
            });
        }

        return userMenuBeans;
    }

    /**
     * 查询角色全部数据
     *
     * @return /
     */
    @Override
    public List<Role> queryAll() {
        return roleMapper.selectList();
    }

    /**
     * 创建角色
     *
     * @param role /
     * @return /
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void create(Role role) {
        List<String> organizeItem = role.getOrganizeItem();
        roleMapper.create(role);

        Integer roleId = role.getId();

        List<UserMenuBean> roleMenuBeans = treeToList(role.getRoleMenuBeans());
        if (!roleMenuBeans.isEmpty()) {
            roleMapper.addRoleMenus(roleMenuBeans, roleId);
        }

        Set<RoleUserBean> userItem = role.getUserItem();
        if (userItem != null && !userItem.isEmpty()) {
            roleMapper.addRoleUser(roleId, userItem);
        }

        if (organizeItem != null && !organizeItem.isEmpty()) {
            roleMapper.saveOrganize(roleId, organizeItem);
        }

    }

    /**
     * 树形结构转换
     *
     * @param
     * @return
     */
    private List<UserMenuBean> treeToList(List<UserMenuBean> roleMenuBeanList) {

        List<UserMenuBean> resultList = new ArrayList<>();

        if (Objects.isNull(roleMenuBeanList)) {
            return resultList;
        }

        for (UserMenuBean roleMenu : roleMenuBeanList) {
            if (Objects.nonNull(roleMenu.getSubMenus())) {
                resultList.addAll(treeToList(roleMenu.getSubMenus()));
                roleMenu.setSubMenus(null);
            }
            resultList.add(roleMenu);
        }
        return resultList;
    }

    /**
     * 编辑角色
     *
     * @param role /
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void modifyById(Role role) {

        try {
            roleMapper.modifyById(role);

            Integer roleId = role.getId();
            List<UserMenuBean> roleMenuBeanSet = treeToList(role.getRoleMenuBeans());
            if (roleMenuBeanSet != null && !roleMenuBeanSet.isEmpty()) {
                roleMapper.delRoleMenus(roleId);
                roleMapper.addRoleMenus(roleMenuBeanSet, roleId);
            }


            Set<RoleUserBean> userItem = role.getUserItem();
            if (userItem != null && !userItem.isEmpty()) {
                roleMapper.delRoleUser(String.valueOf(roleId));
                roleMapper.addRoleUser(roleId, userItem);
            }
            List<String> organizeItem = role.getOrganizeItem();
            if (organizeItem != null && !organizeItem.isEmpty()) {
                roleMapper.deleteOrganizeBySocietyId(roleId);
                roleMapper.saveOrganize(roleId, organizeItem);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 删除角色
     *
     * @param id /
     */
    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public String deleteById(String id)
    {
        // 删除角色 就需要删除关联的用户,还有sys_role_menu
        //这个不要逻辑删除。直接删除
        //roleMapper.deleteById(id);
        Role role = roleMapper.getOne(id);
        if (Objects.isNull(role))
        {
            return "1";
        }
        if(role.getAdmin()== true)
        {
            return "2";
        }
        roleMapper.deleteByIdTrue(id);//删除角色
        roleMapper.delRoleUser(id);//删除角色关联的用户
        roleMapper.delRoleMenus(Integer.valueOf(id));//删除角色和菜单的关联
        return "3";
    }

    /**
     * 重置用户状态
     *
     * @param id
     */
    @Override
    public void resetStatus(String id) {
        roleMapper.resetStatus(id);
    }

    /**
     * 获取所有菜单以及当前角色包含的权限
     *
     * @param roleId
     * @return
     */
    @Override
    public List<UserMenuBean> findUserMenuByRoleId(String roleId) {
        List<UserMenuBean> newMenuBeans = new ArrayList<>();
        List<UserMenuBean> userMenuBeans = roleMapper.findUserMenuByRoleId(roleId);

        userMenuBeans.stream().collect(Collectors.groupingBy(UserMenuBean::getMenuId)).forEach((key, item) -> {
            if (Objects.nonNull(item) && item.size() == 2) {
                newMenuBeans.addAll(item.stream().filter(t -> t.getReadAndWrite()).collect(Collectors.toList()));
            } else {
                newMenuBeans.addAll(item);
            }
        });

        return getTreeMenu(newMenuBeans);
    }


    /**
     * 获取当前角色所拥有的菜单
     *
     * @param roleId
     * @return
     */
    @Override
    public List<UserMenuBean> findMenuByRoleId(String roleId) {
        List<UserMenuBean> userMenuBeans = roleMapper.findMenuByRoleId(roleId);
        return getTreeMenu(userMenuBeans);
    }

    @Override
    public List<UserMenuBean> findMenuByRoleIdAndCheckTrue(String roleId) {
        List<UserMenuBean> userMenuBeans = roleMapper.findMenuByRoleIdAndCheckTrue(roleId);
        return getTreeMenu(userMenuBeans);
    }

    @Override
    public List<UserBean> findUserListByRole(String roleId) {
        return userMapper.findUserListByRole(roleId);
    }

    @Override
    public Role detail(String roleId) {
        Role roleMapperOne = roleMapper.getOne(roleId);

        List<UserBean> userBeanList = this.findUserListByRole(roleId);

        roleMapperOne.setUserItem(userBeanList.stream().map(t -> new RoleUserBean(t.getType(), t.getId(), t.getUserName())).collect(Collectors.toSet()));
        roleMapperOne.setIds(userBeanList.stream().map(UserBean::getId).collect(Collectors.toSet()));
        roleMapperOne.setRoleMenuBeans(this.findMenuByRoleId(roleId));
        roleMapperOne.setOrganizeItem(roleMapper.findOrganizeItem(roleId));
        return roleMapperOne;
    }

    @Override
    public List<AuthorityDto> mapToGrantedAuthorities(UserLoginDto user) {
        Set<String> permissions = new HashSet<>();
        List<RoleMenuBean> menus = userMapper.findRoleMenuPermissionByUserId(String.valueOf(user.getId()));
        permissions = menus.stream()
                .map(RoleMenuBean::getRoleName)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        return permissions.stream().map(AuthorityDto::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuthorityDto> itcodeToGrantedAuthorities(String itcode) {
        Set<String> permissions = new HashSet<>();
        List<RoleMenuBean> menus = userMapper.findRoleMenuPermissionByItcode(itcode);
        permissions = menus.stream()
                .map(RoleMenuBean::getRoleName)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        return permissions.stream().map(AuthorityDto::new)
                .collect(Collectors.toList());
    }

    public String findUserByRoles(String roleId) {
        return roleMapper.findUserByRoles(roleId);
    }

    private List<UserMenuBean> getTreeMenu(List<UserMenuBean> userMenuBeans) {
        Map<Integer, List<UserMenuBean>> groupMenus = userMenuBeans.stream()
                .filter(menuRouterBean -> Objects.nonNull(menuRouterBean.getParentId()))
                .collect(Collectors.groupingBy(menuRouterBean -> menuRouterBean.getParentId()));

        List<UserMenuBean> treeMenus = userMenuBeans.stream()
                .filter(menuRouterBean -> Objects.isNull(menuRouterBean.getParentId())).sorted(
                        Comparator.comparing(item -> item.getRank())
                ).collect(Collectors.toList());

        treeMenus.forEach(s -> fillSubMenus(s, groupMenus));

        return treeMenus;
    }

    private void fillSubMenus(UserMenuBean menuBean, Map<Integer, List<UserMenuBean>> groupMenus) {
        List<UserMenuBean> subMenus = groupMenus.get(menuBean.getMenuId());
        if (subMenus != null) {
            menuBean.setSubMenus(subMenus.stream().sorted(Comparator.comparing(item -> item.getMenuId())).collect(Collectors.toList()));
            subMenus.forEach(s -> fillSubMenus(s, groupMenus));
        }
    }

    @Override
    public List<String> getOrganizeListByItcode(String itcode) {
        return userMapper.getOrganizeListByItcode(itcode);
    }
}
