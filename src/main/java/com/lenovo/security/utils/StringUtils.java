package com.lenovo.security.utils;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lenovo.constant.ElAdminConstant;
import com.lenovo.entity.AutoMailSendBpoTemp;
import com.lenovo.entity.UserAccessReviewTemp;
import com.lenovo.util.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.ip2region.core.Ip2regionSearcher;
import net.dreamlu.mica.ip2region.core.IpInfo;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

//import nl.basjes.parse.useragent.UserAgent;
//import nl.basjes.parse.useragent.UserAgentAnalyzer;

/**
 * 字符串工具类, 继承org.apache.commons.lang3.StringUtils类
 */
@Slf4j
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    private static final char SEPARATOR = '_';
    private static final String UNKNOWN = "unknown";

//
//    private static final UserAgentAnalyzer USER_AGENT_ANALYZER = UserAgentAnalyzer
//            .newBuilder()
//            .hideMatcherLoadStats()
//            .withCache(10000)
//            .withField(UserAgent.AGENT_NAME_VERSION)
//            .build();



    /**
     * @Description TODO 根据bpoBandEdFlag去获取当前Bpo可以发的邮件(应用在:UarMailTemplateController中)
     * @author wangfenglong
     * @date 2025/12/10 18:49
    **/
    public static String getebaySendMail(UserAccessReviewTemp bpo)
    {
        if(bpo.getBpoBandEdFlag().contains("1"))
        {
            if(bpo.getBpoBandEdFlag().contains(";"))
            {
                //包含;表示bpo邮件有多个
                List<Integer> allIndexes = new ArrayList<>();
                List<String> mail = new ArrayList<>();//允许发邮件的bpo邮件
                Map<String,String> mailMap = new HashMap<>();//key对应邮箱，value对应bpo
                try
                {
                    int[] mn = Arrays.stream(bpo.getBpoBandEdFlag().split(";"))
                            .map(String::trim)
                            .filter(element -> !element.isEmpty()) // 过滤空元素
                            .mapToInt(Integer::parseInt)
                            .toArray();
                    // 找所有1
                    for (int i = 0; i < mn.length; i++)
                    {
                        if (mn[i] == 1)
                        {
                            allIndexes.add(i);
                        }
                    }

                    //将下标等于1的都排除掉
                    String[] mailArray = bpo.getBpoEmail().split("; ");
                    String[] bpoArray = bpo.getBpo().split("; ");
                    Set<Integer> deleteIndexSet = new HashSet<>(allIndexes); // 性能优化
                    for (int i = 0; i < mailArray.length; i++)
                    {
                        if (!deleteIndexSet.contains(i))
                        {
                            mail.add(mailArray[i]);
                            mailMap.put(mailArray[i], bpoArray[i]);
                        }
                    }

                    // 过滤null、空、空白字符串
                    List<String> filteredMail = mail.stream().filter(str -> str != null && !str.isBlank()).collect(Collectors.toList());
                    String mailStr = String.join(";", filteredMail);
                    return mailStr;
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    log.info("待发送页面--发送邮件实际逻辑:校验失败:"+e.getMessage());
                    throw new RuntimeException(e.getMessage());
                }
            }
            else
            {
                //包含1但是不包含;表示：当条bpo不发邮件
                return null;
            }
        }
        else
        {
            //不包含1表示：邮件均可发
            return bpo.getBpoEmail();
        }
    }

    /**
     * @Description TODO 定时任务发送邮件需要校验BPO有多个邮箱
     * 说明：2026-3-3修改：目前的方法是校验bpoBandEdFlag和appBpoBandEdFlag来配对bpo的邮箱，现在不用校验了，直接使用getebaySendMailToBpoNotCheckBpoBandEdFlag()方法
     * @author wangfenglong
     * @date 2025/12/30 15:43
    **/
    //public static Map<String,String> getebaySendMailToBpo(AutoMailSendBpoTemp bpo)
    public static Map<String,String> getebaySendMailToBpo(Object obj)
    {
        //包含;表示bpo邮件有多个
        List<Integer> allIndexes = new ArrayList<>();
        List<String> mail = new ArrayList<>();//允许发邮件的bpo邮件
        Map<String,String> mailMap = new HashMap<>();//key对应Bpo，value对应邮箱
        try
        {
            String bpoBandEdFlag = getFieldValue(obj, "bpoBandEdFlag");
            String bpoEmail = getFieldValue(obj, "bpoEmail");
            String bpoStr = getFieldValue(obj, "bpo");
            String appBpoBandEdFlag = getFieldValue(obj, "appBpoBandEdFlag");

            int[] mn = StringUtils.isNotBlank(bpoBandEdFlag)
                    ? Arrays.stream(StringUtils.split(bpoBandEdFlag, ";")).map(String::trim).filter(str -> !str.isEmpty()).mapToInt(Integer::parseInt).toArray()
                    : Arrays.stream(StringUtils.split(appBpoBandEdFlag, ";")).map(String::trim).filter(str -> !str.isEmpty()).mapToInt(Integer::parseInt).toArray();

            /*int[] mn = Arrays.stream(StringUtils.split(bpoBandEdFlag,";"))
                    .map(String::trim)
                    .filter(element -> !element.isEmpty()) // 过滤空元素
                    .mapToInt(Integer::parseInt)
                    .toArray();*/
            // 找所有1
            for (int i = 0; i < mn.length; i++)
            {
                if (mn[i] == 1)
                {
                    allIndexes.add(i);
                }
            }

            //将下标等于1的都排除掉
            //String[] mailArray = bpo.getBpoEmail().split("; ");
            //String[] bpoArray = bpo.getBpo().split("; ");

            String[] mailArray = Arrays.stream(StringUtils.split(bpoEmail, ";")).map(String::trim).filter(StringUtils::isNotBlank).toArray(String[]::new);
            String[] bpoArray = Arrays.stream(StringUtils.split(bpoStr, ";")).map(String::trim).filter(StringUtils::isNotBlank).toArray(String[]::new);

            Set<Integer> deleteIndexSet = new HashSet<>(allIndexes); // 性能优化
            int maxLen = Math.min(mailArray.length, bpoArray.length);
            //for (int i = 0; i < mailArray.length; i++)
            for (int i = 0; i < maxLen; i++)
            {
                if (!deleteIndexSet.contains(i))
                {
                    mail.add(mailArray[i]);
                    mailMap.put(bpoArray[i],mailArray[i]);
                }
            }

            // 过滤null、空、空白字符串
            //List<String> filteredMail = mail.stream().filter(str -> str != null && !str.isBlank()).collect(Collectors.toList());
            //String mailStr = String.join(";", filteredMail);
            return mailMap;
        }
        catch (Exception e)
        {
            log.error("正常发送邮件_拆分BPO多邮箱失败的原始数据是：{}",obj.toString());
            log.error("正常发送邮件_拆分BPO多邮箱失败:",e);
            throw new RuntimeException(e);
        }

    }

    /**
     * @Description TODO 校验多个bpo的情况(不需要校验BpoBandEdFlag,直接bpo+@lenovo.com)
     * @author wangfenglong
     * @date 2026/3/3 10:27
    **/
    public static Map<String,String> getebaySendMailToBpoNotCheckBpoBandEdFlag(Object obj)
    {
        //包含;表示bpo邮件有多个
        Map<String,String> mailMap = new HashMap<>();//key对应Bpo，value对应邮箱
        try
        {
            String bpoStr = getFieldValue(obj, "bpo");
            if(StringUtils.isBlank(bpoStr))
            {
                return Collections.emptyMap();
            }
            return Arrays.stream(StringUtils.split(bpoStr, ";"))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .collect(Collectors.toMap(bpo -> bpo, bpo -> bpo + "@lenovo.com"));
        }
        catch (Exception e)
        {
            log.error("正常发送邮件_拆分BPO多邮箱失败的原始数据是：{},异常原因是:{}",obj.toString(),e.getMessage(),e);
            throw new RuntimeException(e);
        }

    }

    /**
     * 【反射工具方法】从任意对象中获取指定名称的字符串字段值
     * 支持：字段私有/公有，有getter方法 或 直接字段
     * @param obj 任意对象
     * @param fieldName 字段名
     * @return 字段值，为空则返回空字符串
     */
    private static String getFieldValue(Object obj, String fieldName)
    {
        if (obj == null || StringUtils.isBlank(fieldName))
        {
            return "";
        }
        // 优先通过getter方法获取（推荐，符合JavaBean规范）
        try
        {
            String getterName = "get" + fieldName.substring(0,1).toUpperCase() + fieldName.substring(1);
            return (String) obj.getClass().getMethod(getterName).invoke(obj);
        }
        catch (Exception e)
        {
            // getter方法获取失败，直接获取字段值
            try
            {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true); // 暴力访问私有字段
                Object value = field.get(obj);
                return value == null ? "" : value.toString();
            }
            catch (Exception ex)
            {
                return "";
            }
        }
    }



    /**
     * 驼峰命名法工具
     *
     * @return toCamelCase(" hello_world ") == "helloWorld"
     * toCapitalizeCamelCase("hello_world") == "HelloWorld"
     * toUnderScoreCase("helloWorld") = "hello_world"
     */
    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        }

        s = s.toLowerCase();

        StringBuilder sb = new StringBuilder(s.length());
        boolean upperCase = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == SEPARATOR) {
                upperCase = true;
            } else if (upperCase) {
                sb.append(Character.toUpperCase(c));
                upperCase = false;
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * 驼峰命名法工具
     *
     * @return toCamelCase(" hello_world ") == "helloWorld"
     * toCapitalizeCamelCase("hello_world") == "HelloWorld"
     * toUnderScoreCase("helloWorld") = "hello_world"
     */
    public static String toCapitalizeCamelCase(String s) {
        if (s == null) {
            return null;
        }
        s = toCamelCase(s);
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * 驼峰命名法工具
     *
     * @return toCamelCase(" hello_world ") == "helloWorld"
     * toCapitalizeCamelCase("hello_world") == "HelloWorld"
     * toUnderScoreCase("helloWorld") = "hello_world"
     */
    static String toUnderScoreCase(String s) {
        if (s == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        boolean upperCase = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            boolean nextUpperCase = true;

            if (i < (s.length() - 1)) {
                nextUpperCase = Character.isUpperCase(s.charAt(i + 1));
            }

            if ((i > 0) && Character.isUpperCase(c)) {
                if (!upperCase || !nextUpperCase) {
                    sb.append(SEPARATOR);
                }
                upperCase = true;
            } else {
                upperCase = false;
            }

            sb.append(Character.toLowerCase(c));
        }

        return sb.toString();
    }

    /**
     * 获取ip地址
     */
    public static String getIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        String comma = ",";
        String localhost = "127.0.0.1";
        if (ip.contains(comma)) {
            ip = ip.split(",")[0];
        }
        if (localhost.equals(ip)) {
            // 获取本机真正的ip地址
            try {
                ip = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                log.error(e.getMessage(), e);
            }
        }
        return ip;
    }

    /**
     * 根据ip获取详细地址
     */
    public static String getCityInfo(String ip) {
        if (true) {
            return getLocalCityInfo(ip);
        } else {
            return getHttpCityInfo(ip);
        }
    }

    /**
     * 根据ip获取详细地址
     */
    public static String getHttpCityInfo(String ip) {
        String api = String.format(ElAdminConstant.Url.IP_URL, ip);
        JSONObject object = JSONUtil.parseObj(HttpUtil.get(api));
        return object.get("addr", String.class);
    }

    /**
     * 根据ip获取详细地址
     */
    public static String getLocalCityInfo(String ip) {
        // 延迟获取，避免可选组件异常导致整个 StringUtils 类初始化失败。
        Ip2regionSearcher ipSearcher = SpringContextHolder.getBean(Ip2regionSearcher.class);
        IpInfo ipInfo = ipSearcher.memorySearch(ip);
        if (ipInfo != null) {
            return ipInfo.getAddress();
        }
        return null;

    }

    public static String getBrowser(HttpServletRequest request) {
//        UserAgent.ImmutableUserAgent userAgent = USER_AGENT_ANALYZER.parse(request.getHeader("User-Agent"));
//        return userAgent.get(UserAgent.AGENT_NAME_VERSION).getValue();
        return "";
    }

    /**
     * 获得当天是周几
     */
    public static String getWeekDay() {
        String[] weekDays = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
        if (w < 0) {
            w = 0;
        }
        return weekDays[w];
    }

    /**
     * 获取当前机器的IP
     *
     * @return /
     */
    public static String getLocalIp() {
        try {
            InetAddress candidateAddress = null;
            // 遍历所有的网络接口
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements(); ) {
                NetworkInterface anInterface = interfaces.nextElement();
                // 在所有的接口下再遍历IP
                for (Enumeration<InetAddress> inetAddresses = anInterface.getInetAddresses(); inetAddresses.hasMoreElements(); ) {
                    InetAddress inetAddr = inetAddresses.nextElement();
                    // 排除loopback类型地址
                    if (!inetAddr.isLoopbackAddress()) {
                        if (inetAddr.isSiteLocalAddress()) {
                            // 如果是site-local地址，就是它了
                            return inetAddr.getHostAddress();
                        } else if (candidateAddress == null) {
                            // site-local类型的地址未被发现，先记录候选地址
                            candidateAddress = inetAddr;
                        }
                    }
                }
            }
            if (candidateAddress != null) {
                return candidateAddress.getHostAddress();
            }
            // 如果没有发现 non-loopback地址.只能用最次选的方案
            InetAddress jdkSuppliedAddress = InetAddress.getLocalHost();
            if (jdkSuppliedAddress == null) {
                return "";
            }
            return jdkSuppliedAddress.getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }
}
