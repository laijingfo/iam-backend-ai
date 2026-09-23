package com.lenovo.util;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateTime;

import java.text.ParseException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.lenovo.entity.UserAccessReviewTemp;

public class DateUtil
{
    private static final DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    //格式4：yyyyMMdd（紧凑格式，如20251214）
    private static final DateTimeFormatter formatter4 = DateTimeFormatter.ofPattern("yyyyMMdd");




    /**
     * 根据当前日期判断属于哪一个季度
     * 4.1-6.31   Q1
     * 7.1-9.31   Q2
     * 10.1-12.31 Q3
     * 1.1-3.31   Q4
     *
     * @return
     */
    public static String getQuarter() {
        String currentYearMonth = cn.hutool.core.date.DateUtil.format(new Date(), DatePattern.NORM_MONTH_FORMATTER);
        String result = "";
        String month = currentYearMonth.substring(currentYearMonth.length() - 2);
        if ("04".equals(month) || "05".equals(month) || "06".equals(month)) {
            result = "Q1";
        } else if ("07".equals(month) || "08".equals(month) || "09".equals(month)) {
            result = "Q2";
        } else if ("10".equals(month) || "11".equals(month) || "12".equals(month)) {
            result = "Q3";
        } else if ("01".equals(month) || "02".equals(month) || "03".equals(month)) {
            result = "Q4";
        }
        return result;
    }

    public static Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneOffset.systemDefault()).toInstant());
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.systemDefault());
    }

    public static List<LocalDateTime> generateTimeRangeChunk(LocalDateTime startTime, LocalDateTime endTime, int type, int param) {
        List<LocalDateTime> result = null;
        if (type == 1) {
            // 每日
            result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofDays(1)).map(localDate -> localDate.atTime(0, 0)).collect(Collectors.toList());
            if (param == 1) {
                // 排除周末
                result = result.stream().filter(localDateTime -> localDateTime.getDayOfWeek().getValue() < 6).collect(Collectors.toList());
            }
        } else if (type == 2) {
            // 每周
            startTime = startTime.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(param)));

            result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofWeeks(1)).map(localDate -> localDate.atTime(0, 0)).collect(Collectors.toList());

        } else if (type == 3) {
            // 每月
            if (param > 0 && param < 28) {
                if (startTime.getDayOfMonth() > param) {
                    startTime = startTime.plusMonths(1).withDayOfMonth(param);
                } else {
                    startTime = startTime.withDayOfMonth(param);
                }
                result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofMonths(1)).map(localDate -> localDate.atTime(0, 0)).collect(Collectors.toList());

            } else {
                result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofMonths(1)).map(localDate -> localDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(0, 0)).collect(Collectors.toList());
            }
        } else if (type == 4) {
            // 每年
            if (param > 0 && param < 13) {
                if (startTime.getMonthValue() > param) {
                    startTime = startTime.plusYears(1).withMonth(param);
                } else {
                    startTime = startTime.withMonth(param);
                }
                result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofMonths(1)).map(localDate -> localDate.with(TemporalAdjusters.lastDayOfMonth()).atTime(0, 0)).collect(Collectors.toList());
            } else {
                result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofYears(1)).map(localDate -> localDate.with(TemporalAdjusters.lastDayOfYear()).atTime(0, 0)).collect(Collectors.toList());
            }
        } else if (type == 5) {
            // 每季度
            int month = startTime.getMonthValue();
            if (month >= 1 && month <= 3) {
                startTime = startTime.withMonth(1);
            } else if (month >= 4 && month <= 6) {
                startTime = startTime.withMonth(4);
            } else if (month >= 7 && month <= 9) {
                startTime = startTime.withMonth(7);
            } else {
                startTime = startTime.withMonth(10);
            }
            TemporalAdjuster adjuster;
            if (param > 0 && param < 90) {
                adjuster = (temporal -> {
                    int monthValue = temporal.get(ChronoField.MONTH_OF_YEAR);
                    if (monthValue >= 1 && monthValue <= 3) {
                        return temporal.with(ChronoField.MONTH_OF_YEAR, 1).with(TemporalAdjusters.firstDayOfMonth()).plus(param - 1, ChronoUnit.DAYS);
                    } else if (monthValue >= 4 && monthValue <= 6) {
                        return temporal.with(ChronoField.MONTH_OF_YEAR, 4).with(TemporalAdjusters.firstDayOfMonth()).plus(param - 1, ChronoUnit.DAYS);
                    } else if (monthValue >= 7 && monthValue <= 9) {
                        return temporal.with(ChronoField.MONTH_OF_YEAR, 7).with(TemporalAdjusters.firstDayOfMonth()).plus(param - 1, ChronoUnit.DAYS);
                    } else {
                        return temporal.with(ChronoField.MONTH_OF_YEAR, 10).with(TemporalAdjusters.firstDayOfMonth()).plus(param - 1, ChronoUnit.DAYS);
                    }
                });
            } else {
                adjuster = nextQuarterEnd();
            }


            result = startTime.toLocalDate().datesUntil(endTime.toLocalDate(), Period.ofMonths(3))
                    .map(localDate -> localDate.with(adjuster).atTime(0, 0))
                    .collect(Collectors.toList());

        }

        return result;
    }

    public static TemporalAdjuster nextQuarterEnd() {
        return temporal -> {
            int monthValue = temporal.get(ChronoField.MONTH_OF_YEAR);
            if (monthValue >= 1 && monthValue <= 3) {
                return temporal.with(ChronoField.MONTH_OF_YEAR, 3).with(TemporalAdjusters.lastDayOfMonth());
            } else if (monthValue >= 4 && monthValue <= 6) {
                return temporal.with(ChronoField.MONTH_OF_YEAR, 6).with(TemporalAdjusters.lastDayOfMonth());
            } else if (monthValue >= 7 && monthValue <= 9) {
                return temporal.with(ChronoField.MONTH_OF_YEAR, 9).with(TemporalAdjusters.lastDayOfMonth());
            } else {
                return temporal.with(ChronoField.MONTH_OF_YEAR, 12).with(TemporalAdjusters.lastDayOfMonth());
            }
        };
    }

    public static Boolean filterWithCronTime(String cron, ZoneId zoneId) throws ParseException {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING);
        CronParser parser = new CronParser(cronDefinition);
        Cron c = parser.parse(cron);
        ExecutionTime t = ExecutionTime.forCron(c);
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return t.isMatch(now);
    }

    public static LocalDate getCurrentQuarterLastDay()
    {
        return getQuarterLastDay(LocalDate.now());
    }

    /**
     * @Description TODO 返回当前日期所属季度的最后一天
     * @author wangfenglong
     * @date 2025/12/9 16:38
    **/
    public static LocalDate getQuarterLastDay(LocalDate date)
    {
        // 入参校验
        if (date == null)
        {
            throw new IllegalArgumentException("日期参数不能为空");
        }

        Month month = date.getMonth();
        Month lastMonthOfQuarter;

        // 确定当前季度的最后一个月
        if (month.getValue() <= 3) {
            lastMonthOfQuarter = Month.MARCH; // 第一季度：1-3月 → 3月
        } else if (month.getValue() <= 6) {
            lastMonthOfQuarter = Month.JUNE;  // 第二季度：4-6月 → 6月
        } else if (month.getValue() <= 9) {
            lastMonthOfQuarter = Month.SEPTEMBER; // 第三季度：7-9月 → 9月
        } else {
            lastMonthOfQuarter = Month.DECEMBER;  // 第四季度：10-12月 → 12月
        }
        // 构造季度最后一天：年份 + 季度最后一个月 + 该月最后一天（自动适配2月天数）
        return LocalDate.of(date.getYear(), lastMonthOfQuarter, 1).with(TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * @Description TODO 获取当前日期
     * @author wangfenglong
     * @date 2025/12/14 02:12
    **/
    public static String getCurrentTime()
    {
        //获取当前日期（可指定时区，这里用上海时区）
        LocalDate currentDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String dateStr1 = currentDate.format(formatter1);
        String dateStr2 = currentDate.format(formatter2);
        String dateStr3 = currentDate.format(formatter3);
        return currentDate.format(formatter4);
    }



    public static void main(String... args) {
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusDays(1), 1, 1).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, 1).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusDays(7), 1, 2).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusWeeks(4), 2, 5).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusWeeks(4), 2, 7).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusMonths(12), 3, 1).forEach(System.out::println);
        System.out.println("=====================");
        generateTimeRangeChunk(LocalDateTime.now(), LocalDateTime.now().plusMonths(12), 3, -1).forEach(System.out::println);
        System.out.println("～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～");


        // 示例1：当前日期（假设为2025-02-15）→ 第一季度最后一天：2025-03-31
        LocalDate currentLastDay = getCurrentQuarterLastDay();
        System.out.println("当前季度最后一天：" + currentLastDay);
        currentLastDay.toString();

        // 示例2：指定日期2025-04-20 → 第二季度最后一天：2025-06-30
        LocalDate specifiedDate = LocalDate.of(2025, 4, 20);
        LocalDate specifiedLastDay = getQuarterLastDay(specifiedDate);
        System.out.println("2025-04-20所属季度最后一天：" + specifiedLastDay);




        String content = "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Hi,UAR_IT_CODE</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>背景：</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">为确保IT应用系统用户权限的安全合规，依据公司信息安全规范中关于用户权限管理的要求，IT团队将开展应用系统用户账号和权限的审核和清理工作。用户权限审核者包括系统的BPO（Business Process Owner）和持有该系统权限用户的直属经理。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">您收到此邮件是因为您的一名或多名下属的应用系统访问权限需要您的审核。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">请点击</span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><u> </u></span><a href=\"https://cms-dev.lenovo.com/\" target=\"_blank\"><span style=\"color: rgb(66, 144, 247); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><u>https://cms-dev.lenovo.com</u></span></a><span style=\"color: rgb(66, 144, 247); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><u> </u></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">进入“直属经理待办任务”页面，遵循权限最小化原则，选择“保留”或“移除”您下属的权限。</span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>操作指引：</strong></span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">1. </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>审核标准</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：</span></p>\n" +
                "<ul>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>保留权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：若用户当前仍需该权限履行工作职责，且权限与其角色匹配。</span></li>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>移除权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：若用户已转岗、离职或权限需调整。</span></li>\n" +
                "</ul>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">2. </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>审核截止时间</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：请于UAR_DUE_DATE 前完成审核并在“直属经理待办任务”页面提交结果。若未按时反馈，</span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>系统或按默认规则移除未审核的权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">。为避免业务影响，请您按时完成权限审核并提交结果。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>本次提交给您审核的权限数据，其范围是截至 UAR_FREEZE_DATE 的系统快照。请您基于此版本的数据进行审核。</strong></span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>常见问题, 请参见 </strong></span><a href=\"https://cms-dev.lenovo.com/\" target=\"_blank\"><span style=\"color: rgb(66, 144, 247); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><u>https://cms-dev.lenovo.com</u></span></a><span style=\"color: rgb(66, 144, 247); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><u> </u></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">或联系</span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>UAR_APP_OWNER</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">。感谢您对公司信息安全及权限审核工作的支持！</span></p>\n" +
                "<p><br></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Hi，UAR_IT_CODE </span></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Background: </strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">To ensure the security and compliance of user permissions in IT application systems, in accordance with the requirements of the company's information security regulations regarding user permission management, the IT team will carry out the review and cleanup of user accounts and permissions in application systems. User permission reviewers include the system's BPO (Business Process Owner) and the direct managers of users holding the system permissions. </span></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">You are receiving this email because the application system access permissions of one or more of your subordinates require your review. </span></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Please click </span><a href=\"https://cms-dev.lenovo.com\" target=\"_blank\"><span style=\"color: rgb(66, 144, 247);\">https://cms-dev.lenovo.com</span></a><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> to enter the \"Direct Manager's Pending Tasks\" page. Following the principle of least privilege, select \"Retain\" or \"Remove\" the permissions of your subordinates. </span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Operation Guidelines: </strong></span></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>1. Review Criteria: </strong></span></p>\n" +
                "<ul>\n" +
                " <li><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Retain Permission:</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> If the user still needs this permission to perform their job duties currently, and the permission matches their role. </span></li>\n" +
                " <li><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Remove Permission: </strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">If the user has transferred positions, left the company, or the permission needs to be adjusted. </span></li>\n" +
                "</ul>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>2. Review Deadline:</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> Please complete the review and submit the results on the \"Direct Manager's Pending Tasks\" page before </span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>UAR_DUE_DATE</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">. If there is no timely feedback, </span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>the system may remove the un-reviewed permissions according to the default rules</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">. To avoid business impacts, please complete the permission review and submit the results on time. </span></p>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>The permission data submitted to you for review is a system snapshot as of</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> </span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>UAR_FREEZE_DATE</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">.</span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> Please review based on the data of this version.</strong></span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>For frequently asked questions, please refer to</strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> </span><a href=\"https://cms-dev.lenovo.com/ \" target=\"_blank\"><span style=\"color: rgb(66, 144, 247); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">https://cms-dev.lenovo.com </span></a><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>or contactUAR_APP_OWNER. </strong></span><span style=\"color: rgb(31, 35, 41); font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Thank you for your support of the company's information security and permission review work!</span></p>";

        // 实际替换值
        String UAR_FREEZE_DATE = "2025-12-25";

        // 核心：替换所有UAR_FREEZE_DATE占位符（精准匹配，避免部分匹配）
        String replacedContent = content.replaceAll(
                "\\bUAR_FREEZE_DATE\\b", // 单词边界匹配，确保只替换独立的占位符
                "2025-12"         // 替换值
        );

        // 输出结果验证（可选）
        //System.out.println("替换后的内容片段：");
        //System.out.println(replacedContent);


        String sd = "0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 0; 1";
        // 1. 分割字符串（按";"分割，同时trim每个元素去除空格）
        // 2. 转换为int数组
        int[] mn = java.util.Arrays.stream(sd.split(";"))
                .map(String::trim)  // 去除每个元素前后的空格（如" 0"→"0"）
                .mapToInt(Integer::parseInt)  // 转换为int类型
                .toArray();  // 转为int数组
        // 验证结果
        //System.out.println("转换后的int数组长度：" + mn.length);
        //System.out.println("数组最后一个元素：" + mn[mn.length - 1]); // 输出1



        String mail = "CEOLSON@motorola.com; JOHNCLARK@motorola.com; WLDW11@motorola.com; acormie@lenovo.com; btoscano@lenovo.com; changlin1@lenovo.com; chuming1@lenovo.com; efilimonov@lenovo.com; fanrong2@lenovo.com; ivanbozev@lenovo.com; kchie@lenovo.com; lacroix@lenovo.com; lidan14@lenovo.com; linlc1@lenovo.com; lizx23@lenovo.com; lzedek@lenovo.com; mieri@lenovo.com; mmoreno@lenovo.com; mzelakievicz@lenovo.com; pmalakar@lenovo.com; ttennant@lenovo.com; vlois@lenovo.com";
        // 核心：按"; "分割（分号+空格），得到邮箱字符串数组
        String[] mailArray = mail.split("; ");
        // 验证结果
        System.out.println("分割后的邮箱数量：" + mailArray.length);
        System.out.println("第一个邮箱：" + mailArray[0]); // CEOLSON@motorola.com
        System.out.println("最后一个邮箱：" + mailArray[mailArray.length - 1]); // vlois@lenovo.com
        // 遍历输出所有邮箱
        for (int i = 0; i < mailArray.length; i++) {
            System.out.println("下标" + i + "：" + mailArray[i]);
        }

        System.out.println("！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！！");

        String content2 = "<div style=\"background-color: #f2f2f2; width: 100%; padding: 40px; margin: 0\">  <div style=\"max-width: 770px; margin: 0 auto; background-color: #fff\">    <img src=\"https://oss2.xcloud.lenovo.com:10443/form-file1/public/2025/12/10/20251210095807/banner%20with%20wording.png?AWSAccessKeyId=JM3UJF3B6E6OL5WYTZ25&Expires=1827539888&Signature=Tu%2BTP%2Fseqk1DRPaya%2Buwnm84zz8%3D\" alt=\"\" width=\"100%\"/>    <div style=\"padding: 20px 40px 40px 40px\"><p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Dear {itCode}</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">,</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>背景：</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">为确保IT应用系统用户权限的安全合规，依据公司信息安全规范中关于用户权限管理的要求，IT团队将开展应用系统用户账号和权限的审核和清理工作。用户权限审核者包括系统的BPO（Business Process Owner）和持有该系统权限用户的直属经理。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">您收到此邮件是因为您的一名或多名下属的应用系统访问权限需要您的审核。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">请点击</span><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> </strong></span><a href=\"https://cms-dev.lenovo.com/\" target=\"_blank\"><span style=\"color: rgb(106, 57, 201);\"><strong>https://cms-dev.lenovo.com/</strong></span></a><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> </strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">进入“直属经理待办任务”页面，遵循权限最小化原则，选择“保留”或“移除”您下属的权限。</span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>操作指引：</strong></span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">1. </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>审核标准</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：</span></p>\n" +
                "<ul>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>保留权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：若用户当前仍需该权限履行工作职责，且权限与其角色匹配。</span></li>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>移除权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：若用户已转岗、离职或权限需调整。</span></li>\n" +
                "</ul>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">2. </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>审核截止时间</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">：请于{cycleEndDate}前完成审核并在“直属经理待办任务”页面提交结果。若未按时反馈，</span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>系统或按默认规则移除未审核的权限</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">。为避免业务影响，请您按时完成权限审核并提交结果。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>本次提交给您审核的权限数据，是{cycleName}的系统快照。请您基于此版本的数据进行审核。</strong></span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>常见问题, 请参见</strong></span><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> </strong></span><a href=\"https://cms-dev.lenovo.com/FAQ\" target=\"_blank\"><span style=\"color: rgb(106, 57, 201);\"><strong>https://cms-dev.lenovo.com/FAQ </strong></span></a><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">或联系 {uarProcessor}。</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">感谢您对公司信息安全及权限审核工作的支持！</span></p>\n" +
                "<p><br></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Dear {itCode}</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">,</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Background:</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> To ensure the security and compliance of user access for IT application systems, in accordance with the requirements for user access management stipulated in the company's information security policies, the IT team will conduct a review and cleanup of user access for the application systems. User access reviewers include the system's BPO (Business Process Owner) and the line managers of users holding access rights to the system.</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">You are receiving this email because one or more of your direct reports' application system access permissions require your review.</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Please click the</span><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> </strong></span><a href=\"https://cms-dev.lenovo.com/\" target=\"_blank\"><span style=\"color: rgb(106, 57, 201);\"><strong>https://cms-dev.lenovo.com/</strong></span></a><span style=\"color: rgb(106, 57, 201);\"><strong> </strong></span><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">to access the \"Line Manager Pending Task\" page. Follow the principle of least privilege to select \"Keep\" or \"Remove\" user permissions.</span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Instructions:</strong></span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>1. Review Criteria:</strong></span></p>\n" +
                "<ul>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Keep:</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> If the user still requires the access to fulfill their job responsibilities, and the access aligns with their role.</span></li>\n" +
                " <li><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Remove:</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> If the user has changed roles, left the company, or if access adjustments are required.</span></li>\n" +
                "</ul>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>2. Review Deadline:</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"> Please complete the review and submit the results on the \"Line Manager Pending Task\" page by {cycleEndDate}. If no feedback is provided by the deadline, the system may remove unreviewed permissions according to default rules. To avoid business disruptions, please complete and submit the access review results on time.</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">The access data provided for your review in this round is a system snapshot of {cycleName}. Please conduct your review based on this version of the data.</span></p>\n" +
                "<hr>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">For </span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong>Frequently Asked Questions</strong></span><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">, please refer to the</span><span style=\"color: rgb(106, 57, 201); font-size: 14px; font-family: &quot;Times New Roman&quot;;\"><strong> </strong></span><a href=\"https://cms-dev.lenovo.com/FAQ\" target=\"_blank\"><span style=\"color: rgb(106, 57, 201);\"><strong>https://cms-dev.lenovo.com/FAQ </strong></span></a><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">or contact the {uarProcessor}.</span></p>\n" +
                "<p><span style=\"font-size: 14px; font-family: &quot;Times New Roman&quot;;\">Thank you for your support of the company's information security and user access review!</span></p></div>  </div></div>";
        Map<String, String> variables = new HashMap<>();
        variables.put("{itCode}", "213");
        variables.put("{uarProcessor}", "234");
        String result = content2;
        for (Map.Entry<String, String> entry : variables.entrySet())
        {
            String variable = entry.getKey();
            String value = entry.getValue();
            // 修复1：使用替换后的字符串进行后续替换，而不是原始字符串
            // 修复2：去掉\\b单词边界符，因为如果变量名和其他字符连在一起（虽然这里没有），但主要是\\b可能导致匹配问题，直接替换文本更稳妥
            // 如果需要精确匹配独立的单词，可以用(?<!\\w)和(?!\\w)来替代\\b，兼容性更好
            String regex = "(?<!\\w)" + Pattern.quote(variable) + "(?!\\w)";
            result = result.replaceAll(regex, value);
        }
        System.out.println("replacedContent2=" + result);

    }
}
