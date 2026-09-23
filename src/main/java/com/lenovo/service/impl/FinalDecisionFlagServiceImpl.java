package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.FinalDecisionFlag;
import com.lenovo.mapper.FinalDecisionFlagMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.service.FinalDecisionFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinalDecisionFlagServiceImpl extends ServiceImpl<FinalDecisionFlagMapper, FinalDecisionFlag>
        implements FinalDecisionFlagService {

    private final FinalDecisionFlagMapper finalDecisionFlagMapper;
    private final UserAccessReviewMapper userAccessReviewMapper;


}