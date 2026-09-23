package com.lenovo.service;

import com.lenovo.entity.ApplicationAccessLink;

import java.util.List;

public interface ApplicationAccessLinkService {

    void saveOrUpdateLink(ApplicationAccessLink link);

    List<ApplicationAccessLink> queryList(String searchKey);
}
