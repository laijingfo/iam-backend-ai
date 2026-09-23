package com.lenovo.strategy.impl;

import com.lenovo.bean.DelegationBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.service.DelegationService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @Description TODO
 * @ClassName DelegationSendEmailStrategy
 * @Author wangfenglong
 * @Date 2026/4/22 15:55
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class DelegationSendEmailStrategy implements CheckStrategy<DelegationBean, ResponseEntity<?>>
{
    private final DelegationService delegationService;

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, DelegationBean delegationBean)
    {
        delegationService.sendDelegateeEmail(delegationBean);
        return ok("ok");
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.DELEGATION_CYCLE_TYPE;
    }
}
