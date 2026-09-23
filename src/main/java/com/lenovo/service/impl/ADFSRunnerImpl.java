package com.lenovo.service.impl;

import com.lenovo.adfs.common.IDPMetaData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class ADFSRunnerImpl implements ApplicationRunner {

    @Value("${location:us}")
    private String location;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IDPMetaData.initialize(location);
    }
}
