package com.lenovo.strategy.factory;

import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.impl.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description 策略工厂类
 * @ClassName CheckStrategyFactory
 * @Author wangfenglong
 * @Date 2026/4/22 14:06
 **/
@Component
public class CheckStrategyFactory
{
    private final Map<SendEmailStrategyType, CheckStrategy<?, ?>> strategyMap = new HashMap<>();

    public CheckStrategyFactory(List<CheckStrategy<?, ?>> strategies)
    {
        for (CheckStrategy<?, ?> strategy : strategies)
        {
            strategyMap.put(strategy.getType(), strategy);
        }
    }

    @SuppressWarnings("unchecked")
    public <T, E> CheckStrategy<T, E> getStrategy(SendEmailStrategyType checkFlag)
    {
        CheckStrategy<?, ?> strategy = strategyMap.get(checkFlag);
        if (strategy == null)
        {
            throw new RuntimeException("不支持的 checkFlag：" + checkFlag);
        }
        return (CheckStrategy<T, E>)strategy;
    }
}
