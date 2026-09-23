package com.lenovo.service.impl;

import com.lenovo.mapper.SplunkMapper;
import com.lenovo.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SplunkSyncServiceImpl implements SplunkSyncService {
    @Value("${splunk.address}")
    protected String splunkAddress;

    @Value("${splunk.authorization}")
    protected String itscSplunkToken;

    @Autowired
    private SplunkMapper splunkMapper;


    @Override
    public void saveAllAdAccountFromSplunk() {

    }

    @Override
    public void saveCmdbApplicationListFromSplunk() {

    }

    @Override
    public void saveYearlyAllocationDataFromSplunk() {

    }

    @Override
    public void saveBillingDataFromSplunk() {

    }

    @Override
    public void syncOpex() {

    }
}
