package com.lenovo.service;



public interface SplunkSyncService {


    void saveAllAdAccountFromSplunk();

    void saveCmdbApplicationListFromSplunk();

    void saveYearlyAllocationDataFromSplunk();

    void saveBillingDataFromSplunk();

    void syncOpex();
}
