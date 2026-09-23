package com.lenovo.ai.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component("dateTools")
@RequiredArgsConstructor
public class DateTools {

    @Tool("获取当前日期，返回格式为 YYYY-MM-DD")
    public String currentDate(
            @P(value = "时区，例如 Asia/Shanghai；不传则默认系统时区",
                    defaultValue = "") String zoneId) {
        ZoneId zone = (zoneId == null || zoneId.isBlank())
                ? ZoneId.systemDefault()
                : ZoneId.of(zoneId);
        return LocalDate.now(zone).toString();
    }

    @Tool("计算两个日期之间的天数")
    public long daysBetween(
            @P("开始日期，格式为 YYYY-MM-DD") String startDate,
            @P("结束日期，格式为 YYYY-MM-DD") String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return ChronoUnit.DAYS.between(start, end);
    }
}