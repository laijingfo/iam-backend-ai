package com.lenovo;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@Slf4j
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.lenovo")
@MapperScan("com.lenovo.mapper")
public class ItscServerApplication
{
    public static void main(String[] args)
    {
        // ========== 临时调试代码，看值后删除 ==========
        SpringApplication.run(ItscServerApplication.class, args);
        log.info("----Service Launch Successful----");
    }


}
