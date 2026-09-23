package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.ApplicationAccessLink;
import com.lenovo.entity.ItsApplicationData;
import com.lenovo.mapper.ApplicationAccessLinkMapper;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.service.ApplicationAccessLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationAccessLinkServiceImpl extends ServiceImpl<ApplicationAccessLinkMapper, ApplicationAccessLink> implements ApplicationAccessLinkService {

    final private ItsApplicationDataMapper itsApplicationDataMapper;

    @Override
    public void saveOrUpdateLink(ApplicationAccessLink link) {
        if (StringUtils.isBlank(link.getCmdbId())) {
            throw new RuntimeException("cmdbId cannot be empty");
        }

        ItsApplicationData applicationData = itsApplicationDataMapper.findByCmdbId(link.getCmdbId());
        if (applicationData == null) {
            throw new RuntimeException("cmdbId is not exist");
        }

        LambdaQueryWrapper<ApplicationAccessLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplicationAccessLink::getCmdbId, link.getCmdbId());
        ApplicationAccessLink existing = getOne(wrapper);

        if (existing != null) {
            existing.setAccessLink(link.getAccessLink().trim());
            existing.setUpdateTime(LocalDateTime.now());
            updateById(existing);
        } else {
            save(link);
        }
    }

    @Override
    public List<ApplicationAccessLink> queryList(String searchKey) {
        return getBaseMapper().queryBySearchKey(searchKey);
    }
}
