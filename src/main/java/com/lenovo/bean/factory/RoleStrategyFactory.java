package com.lenovo.bean.factory;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @Description TODO 角色策略工厂：根据策略类型获取对应的策略类
 * @ClassName RoleStrategyFactory
 * @Author wangfenglong
 * @Date 2025/12/21 14:15
 **/
@Component
public class RoleStrategyFactory
{
    // 存储策略类型和对应的策略实例
    private final Map<RoleStrategyType, RoleRetrievalStrategy> strategyMap = new HashMap<>();

    // Spring自动注入所有策略实现类
    public RoleStrategyFactory(DynamicAuthRoleStrategy dynamicAuthStrategy, DelegationRoleStrategy delegationStrategy, UserRoleTableStrategy userRoleTableStrategy, UarProcesserRoleStrategy uarProcesserStrategy,OperationOwnerFocalRoleStrategy  operationOwnerFocalRoleStrategy)
    {
        strategyMap.put(RoleStrategyType.DYNAMIC_AUTH, dynamicAuthStrategy);
        strategyMap.put(RoleStrategyType.DELEGATION, delegationStrategy);
        strategyMap.put(RoleStrategyType.USER_ROLE_TABLE, userRoleTableStrategy);
        strategyMap.put(RoleStrategyType.UAR_PROCESSER, uarProcesserStrategy);
        strategyMap.put(RoleStrategyType.OPERATION_OWNER_FOCAL, operationOwnerFocalRoleStrategy);
    }

    /**
     * 获取策略实例
     * @param type 策略类型
     * @return 对应的策略类
     */
    public RoleRetrievalStrategy getStrategy(RoleStrategyType type)
    {
        RoleRetrievalStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("不支持的角色策略类型：" + type);
        }
        return strategy;
    }
}
