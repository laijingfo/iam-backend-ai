package com.lenovo.config;

import net.dreamlu.mica.ip2region.config.Ip2regionProperties;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.impl.Ip2regionSearcherImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Spring Boot 3 compatible registration for mica-ip2region 2.x. */
@Configuration(proxyBeanMethods = false)
public class Ip2regionConfig {

    @Bean
    public Ip2regionSearcher ip2regionSearcher(
            ResourceLoader resourceLoader,
            @Value("${mica.ip2region.db-file-location:classpath:ip2region/ip2region.db}") String dbFileLocation) {
        Ip2regionProperties properties = new Ip2regionProperties();
        properties.setDbFileLocation(dbFileLocation);
        return new Ip2regionSearcherImpl(resourceLoader, properties);
    }
}
