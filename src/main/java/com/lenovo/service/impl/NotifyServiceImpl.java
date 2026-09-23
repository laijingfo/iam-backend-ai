package com.lenovo.service.impl;


import com.lenovo.entity.CaptureSourceFrom;
import com.lenovo.entity.MailTemplate;
import com.lenovo.entity.ThirdToken;
import com.lenovo.notify.NotifyHelper;


import com.lenovo.service.MailTemplateService;
import com.lenovo.service.NotifyService;
import com.lenovo.service.RiskManagementService;
import com.lenovo.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PreDestroy;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


/**
 * @author : chenhao
 * @date : 2023/2/21
 * @description :
 */
@Service
@RequiredArgsConstructor
public class NotifyServiceImpl implements NotifyService
{
    private static final Logger logger = LoggerFactory.getLogger(NotifyServiceImpl.class);

}
