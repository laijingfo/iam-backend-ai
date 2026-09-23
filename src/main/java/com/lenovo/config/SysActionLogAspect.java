package com.lenovo.config;

import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.lenovo.entity.SysActionLog;
import com.lenovo.security.service.dto.JwtUserDto;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.SysActionLogService;
import com.lenovo.util.HttpUtilsSkpSsl;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

import cz.mallat.uasparser.OnlineUpdater;
import cz.mallat.uasparser.UASparser;
import cz.mallat.uasparser.UserAgentInfo;
import org.springframework.web.multipart.MultipartFile;


/**
 * @Description TODO 系统日志切面
 * @author wangfenglong
 * @date 2026/4/9 14:51
**/
@Aspect
@Component
@Order(10)
@Slf4j
public class SysActionLogAspect
{
    @Autowired
    private SysActionLogService sysActionLogService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> EXCLUDE_URI_SUFFIXES = new HashSet<>(Arrays.asList(
        "pending/distinctapplication",
        "pending/sentcount",
        "pending/failedcount",
        "pending/pendingcount",
        "sent/distinctapplication",
        "failed/distinctapplication",
        "dict/dept",
        "linemanagerreview/getdistinctapplication",
        "itsapplication/getapplicationnames",
        "itsapplication/getapplicationids",
        "bporeview/getdistinctapplication"
    ));

    static
    {
        // 配置ObjectMapper容错规则，核心：序列化失败不抛异常，字段返回null
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false); // 空bean不报错
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // 忽略未知字段
        OBJECT_MAPPER.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, true);
        // 处理循环引用：直接忽略循环引用的字段（或设为null）
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        OBJECT_MAPPER.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
    }

    // 初始化 User-Agent 解析器（全局单例，避免重复创建）
    static UASparser uasParser = null;
    static
    {
        try
        {
            uasParser = new UASparser(OnlineUpdater.getVendoredInputStream());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    @Pointcut("(execution(* com.lenovo.controller..*.*(..)) " +
            "&& !execution(* com.lenovo.security.rest.OnlineController..*.*(..)) " +
            "&& !execution(* com.lenovo.controller.PingController.*(..))) " +
            "|| execution(* com.lenovo.security.rest.AuthorizationController.auth(..))"
    )
    public void logPointCut() {}


    @Around("logPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable
    {
        long beginTime = System.currentTimeMillis();// 开始时间（统计耗时）
        SysActionLog actionLog = new SysActionLog();
        // 1 添加日志保存标记，默认需要保存
        boolean needSaveLog = true;
        // 1.1 提前定义request变量，避免重复获取
        HttpServletRequest request = null;

        try
        {
            ServletRequestAttributes attributes = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes());
            if (attributes != null)
            {
                request = attributes.getRequest();
            }

            if(request != null)
            {
                String uri = request.getRequestURI();
                String lowerUri = uri.toLowerCase();
                /*if (uri.contains("export") || uri.endsWith(".xlsx"))
                {
                    needSaveLog = false;
                    return point.proceed();
                }*/
                // 匹配排除列表时，设置不保存日志，并直接放行
                if (EXCLUDE_URI_SUFFIXES.stream().anyMatch(lowerUri::endsWith))
                {
                    needSaveLog = false;
                    return point.proceed();
                }
            }

            // 2 收集请求基础信息
            collectRequestInfo(point, actionLog,request);
            // 2.1 校验用户itcode
            String username = actionLog.getUsername();
            if (username == null || "anonymous".equals(username)) {needSaveLog = false;}
            // 3 执行目标方法（控制器方法）
            Object result = point.proceed();
            // 4 方法执行成功后补充日志信息,1-成功，0-失败
            actionLog.setStatus(1);
            return result;
        }
        catch (Exception e)
        {
            actionLog.setStatus(0);
            log.error("切面打印日志异常:method exception：", e);
            e.printStackTrace();
            throw e;
        }
        finally
        {
            // 6 计算耗时，设置操作时间，异步保存日志
            if (needSaveLog)
            {
                long costTime = System.currentTimeMillis() - beginTime;
                actionLog.setTime(costTime);// 操作耗时（毫秒）
                actionLog.setCreateDate(LocalDateTime.now()); // 操作时间
                saveActionLogAsync(actionLog);
            }
        }
    }

    /**
     * 收集请求基础信息（请求URI、IP、参数、方法名、操作用户等）
     */
    private void collectRequestInfo(ProceedingJoinPoint joinPoint, SysActionLog actionLog,HttpServletRequest request)
    {
        if(Objects.isNull(request))
        {
            log.info("切面打印日志异常:Unable to obtain the request context");//无法获取上下文
            actionLog.setOperation("Unable to obtain the request context:request is null");
            return;
        }

        // 获取目标方法信息（通过方法签名）
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 填充日志字段
        actionLog.setRequestUri(request.getRequestURL().toString()); // 请求URL
        actionLog.setIp(getClientIp(request)); // 客户端IP
        actionLog.setMethod(method.getDeclaringClass().getName() + "." + method.getName()); // 全类名+方法名
        actionLog.setParams(getRequestParams(joinPoint,request)); // 请求参数
        actionLog.setUserAgent(request.getHeader("User-Agent")); // 客户端浏览器/设备信息
        actionLog.setOperation(getOperationDesc(method)); // 操作描述
        actionLog.setUsername(getLoginUsername()); // 操作用户名
        actionLog.setTenantId(getCurrentTenantId(request)); // 租户ID
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request)
    {
        /*String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
        {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
        {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip))
        {
            ip = request.getRemoteAddr();
        }
        // 处理多IP场景（x-forwarded-for可能返回多个IP，取第一个）
        return ip != null ? ip.split(",")[0].trim() : "unknown";*/
        return StringUtils.getIp(request);
    }

    /**
     * 获取请求参数
     */
    private String getRequestParams(ProceedingJoinPoint joinPoint,HttpServletRequest request)
    {
        /* 老版本
        try
        {
            Object[] args = joinPoint.getArgs();
            // 无参数时返回标准空JSON
            if (Objects.isNull(args) || args.length == 0)
            {
                return "{}";
            }

            // 存储处理后的参数（按顺序）
            List<Object> processedArgs = new ArrayList<>();
            for (Object arg : args)
            {
                // 跳过请求/响应对象（无序列化意义）
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse)
                {
                    continue;
                }
                // 处理文件上传参数（友好提示，避免序列化失败）
                if (arg instanceof MultipartFile)
                {
                    MultipartFile file = (MultipartFile) arg;
                    // 记录文件关键信息，而非整个文件对象
                    Map<String, String> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", file.getOriginalFilename());
                    fileInfo.put("fileSize", file.getSize() + " bytes");
                    fileInfo.put("contentType", file.getContentType());
                    processedArgs.add(fileInfo);
                }
                else
                {
                    // 其他参数直接加入（后续统一序列化）
                    processedArgs.add(arg);
                }
            }

            // 处理后无有效参数，返回空JSON
            if (processedArgs.isEmpty())
            {
                return "{}";
            }

            // 序列化为标准JSON字符串（支持复杂对象、集合、自定义POJO）
            return OBJECT_MAPPER.writeValueAsString(processedArgs);
        }
        catch (JsonProcessingException e)
        {
            // JSON序列化失败（如循环引用、不支持的类型）
            log.info("切面打印日志异常:请求参数JSON序列化失败", e);
            return "[切面打印日志异常:参数解析失败：JSON序列化异常]";
        }
        catch (Exception e)
        {
            // 其他未知异常
            log.info("切面打印日志异常:获取请求参数失败", e);
            return "[切面打印日志异常:参数解析失败：未知异常]";
        }*/

        Map<String, String> params = new LinkedHashMap<>();
        try
        {
            // 1.获取key=val 格式参数
            Map<String, String[]> parameterMap = request.getParameterMap();
            parameterMap.forEach((key, values) -> params.put(key, StringUtils.join(values, ",")));

            // 2.获取 @PathVariable路径参数
            MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
            Annotation[][] parameterAnnotations = methodSignature.getMethod().getParameterAnnotations();
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = methodSignature.getParameterNames();
            for (int i = 0; i < parameterNames.length; i++)
            {
                String name = parameterNames[i];
                Object value = args[i];
                for (Annotation annotation : parameterAnnotations[i])
                {
                    if (annotation instanceof PathVariable)
                    {
                        params.put(name, String.valueOf(value));
                    }
                }
            }
            return MapUtil.isNotEmpty(params) ? params.toString() : "{}";
        }
        catch (Exception e)
        {
            log.info("切面打印日志异常:获取请求参数失败:", e);
            return "[切面打印日志异常:参数解析失败：未知异常:]" + e.getMessage();
        }
    }

    /**
     * 获取操作描述
     */
    private String getOperationDesc(Method method)
    {
        //检查方法是否有自定义日志注解
        LogOperation logAnnotation = method.getAnnotation(LogOperation.class);
        //返回注解中配置的操作描述,无注解时用方法名作为操作描述
        return Objects.nonNull(logAnnotation) ? logAnnotation.value() : method.getName();
    }

    /**
     * 获取当前登录用户名
     */
    private String getLoginUsername()
    {
        // 项目实现：
        // 1. 若用Spring Security：SecurityContextHolder.getContext().getAuthentication().getName()
        // 2. 若用JWT：从请求头获取Token，解析用户名
        // 3. 若用Session：request.getSession().getAttribute("username")
        //LoginUserContext.getLoginUser();
        try
        {
            UserDetails UserDetailsMsg = SecurityUtils.getCurrentUser();
            JwtUserDto jwtUserDto =(JwtUserDto)UserDetailsMsg;
            String userId = jwtUserDto.getUser().getId();
            String userName = jwtUserDto.getUser().getUserName();
            return Objects.nonNull(userName) ? userName : "anonymous";
        }
        catch (Exception e)
        {
            log.info("切面打印日志异常:无法获取请当前用户信息：", e);
            return null;
        }
    }

    /**
     * 获取当前租户ID
     */
    private String getCurrentTenantId(HttpServletRequest request)
    {
        //项目实现：如从ThreadLocal中获取租户上下文
        //return Objects.nonNull(SecurityUtils.getCurrentUserType()) ? SecurityUtils.getCurrentUserType() : "1L";
        //return "1L";
        return StringUtils.getCityInfo(StringUtils.getIp(request));
    }

    /**
     * 保存操作日志
     */
    @Async
    public void saveActionLogAsync(SysActionLog actionLog)
    {
        try
        {
            sysActionLogService.addSysActionLog(actionLog);
        }
        catch (Exception e)
        {
            log.info("切面打印日志异常:Asynchronous saving of operation logs failed：", e);
        }
    }

    @AfterThrowing(value = "logPointCut()")
    public void doAfterThrowing()
    {
        log.info("log record==Error capture in cross-section：Don't worry, everything is under control.🫵🤓");
    }


    /**
     * @Description TODO 以后领导有要求就使用这个版本,目前先使用上面的版本
     * 获取请求参数（处理普通参数、JSON参数、文件上传等场景）
     * @author wangfenglong
     * @date 2025/12/24 16:11
    **/
    private String getRequestParams领导有要求的版本(ProceedingJoinPoint joinPoint)
    {
        // 最终返回的JSON字符串，默认空JSON
        String resultJson = "{}";
        try
        {
            Object[] args = joinPoint.getArgs();
            // 无参数时直接返回空JSON
            if (Objects.isNull(args) || args.length == 0)
            {
                return resultJson;
            }

            // 存储处理后的参数（按顺序，无法序列化的参数设为null）
            List<Object> processedArgs = new ArrayList<>();
            for (Object arg : args)
            {
                // 跳过请求/响应对象（无序列化意义，直接设为null）
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse)
                {
                    processedArgs.add(null);
                    continue;
                }

                // 处理文件上传参数：记录关键信息，避免序列化整个文件对象
                if (arg instanceof MultipartFile)
                {
                    MultipartFile file = (MultipartFile) arg;
                    Map<String, String> fileInfo = new HashMap<>();
                    try {
                        fileInfo.put("fileName", file.getOriginalFilename());
                        fileInfo.put("fileSize", file.getSize() + " bytes");
                        fileInfo.put("contentType", file.getContentType());
                    } catch (Exception e) {
                        // 文件参数获取失败也设为null
                        fileInfo = null;
                    }
                    processedArgs.add(fileInfo);
                    continue;
                }

                // 处理普通参数：单独序列化，失败则设为null
                try
                {
                    // 先尝试序列化，验证是否可序列化（不直接写JSON，保留对象结构）
                    OBJECT_MAPPER.writeValueAsString(arg); // 验证序列化可行性
                    processedArgs.add(arg); // 可序列化则保留原对象
                }
                catch (Exception e)
                {
                    // 任何序列化异常（循环引用、不支持的类型等），该参数设为null
                    log.warn("切面打印日志异常:单个参数序列化失败，已设为null", e);
                    processedArgs.add(null);
                }
            }

            // 将处理后的参数列表序列化为JSON（此时已无序列化异常）
            resultJson = OBJECT_MAPPER.writeValueAsString(processedArgs);
        }
        catch (Exception e)
        {
            // 兜底捕获所有异常，确保方法不抛异常，返回空JSON
            log.error("切面打印日志异常:请求参数序列化整体失败，返回空JSON", e);
            resultJson = "{}";
        }
        return resultJson;
    }

    /**
     * 敏感信息脱敏（如手机号、身份证号、密码等）后期可能会需要，先写好放这
     */
    private String desensitizeSensitiveParam(String params)
    {
        if (Objects.isNull(params)) {
            return "";
        }
        // 示例：手机号脱敏（138****1234）
        params = params.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        // 示例：身份证号脱敏（110101********1234）
        params = params.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
        // 示例：密码脱敏
        params = params.replaceAll("\"password\":\"[^\"]+\"", "\"password\":\"******\"");
        return params;
    }

    //记录log-----------------------------------------------------------------------------------------------------
    /*SysActionLog sysLog = saveSysLog(point, beginTime);
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest request = attributes.getRequest();
    request.setAttribute("sysLog", sysLog);
    //执行方法
    Object result = point.proceed();
    //执行时长(毫秒)
    long time = System.currentTimeMillis() - beginTime;
    //保存日志
    SysActionLog sysActionLog = saveSysLog(point, time);
    //保存系统日志
    try
    {
        sysActionLogService.addSysActionLog(sysActionLog);
    }
    catch (Exception e)
    {
        e.printStackTrace();
        log.info("记录日志失败=="+e.getMessage());
    }
    return result;*/
    private SysActionLog saveSysLog(ProceedingJoinPoint joinPoint, long time)
    {
        SysActionLog sysLog = new SysActionLog();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        /*ApiOperation ann = method.getAnnotation(ApiOperation.class);
        if (ann != null) {
            //注解上的描述
            sysLog.setOperation(ann.value());
        }*/
        sysLog.setOperation("123");
        //请求的方法名
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        sysLog.setMethod(className + "." + methodName + "()");
        //请求的参数
        Object[] args = joinPoint.getArgs();
        try
        {
            String params = JSON.toJSONString(args[0]);
            String s="login";
            if (! s.contains(methodName))
            {
                sysLog.setParams(params);
            }
        }
        catch (Exception e)
        {
            log.info("参数为空");
        }

        //获取request
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        //设置IP地址
        sysLog.setIp(HttpUtilsSkpSsl.getIpAddr(request));
        //用户名
        /*if (null != UserContext.getUser()) {
            String username = UserContext.getUser().getUsername();
            sysLog.setUserName(username);
            sysLog.setTenantId(UserContext.getUser().getTenantId());
        }*/
        sysLog.setUsername("1233333");
        sysLog.setTenantId("11111111");
        sysLog.setTime(time);
        sysLog.setCreateDate(LocalDateTime.now());
        sysLog.setStatus(1);
        sysLog.setRequestUri(request.getRequestURL().toString());

        // ========== 新增：解析 User-Agent 提取浏览器名称和版本 ==========
        String userAgentStr = request.getHeader("User-Agent");
        if (Objects.nonNull(userAgentStr) && !userAgentStr.isEmpty())
        {
            try
            {
                UserAgentInfo userAgentInfo = SysActionLogAspect.uasParser.parse(userAgentStr);
                /*System.out.println("操作系统家族：" + userAgentInfo.getOsFamily());
                System.out.println("操作系统详细名称：" + userAgentInfo.getOsName());
                System.out.println("浏览器名称和版本:" + userAgentInfo.getUaName());
                System.out.println("类型：" + userAgentInfo.getType());
                System.out.println("浏览器名称：" + userAgentInfo.getUaFamily());
                System.out.println("浏览器版本：" + userAgentInfo.getBrowserVersionInfo());
                System.out.println("设备类型：" + userAgentInfo.getDeviceType());*/

                if (null != userAgentInfo.getBrowserVersionInfo() && null != userAgentInfo.getUaFamily())
                {
                    String info = userAgentInfo.getBrowserVersionInfo() + "/" + userAgentInfo.getUaFamily();
                    sysLog.setUserAgent(info);
                }
                else
                {
                    sysLog.setUserAgent("没有获取到User-Agent");
                }
            }
            catch (Exception e)
            {
                log.error("切面类---解析 User-Agent 失败，userAgent: {}", userAgentStr, e);
            }
        }
        else
        {
            sysLog.setUserAgent("Unknown");// 无 User-Agent 时，设为默认值
        }
        request.setAttribute("sysLog", sysLog);
        return sysLog;
    }
    //记录log-----------------------------------------------------------------------------------------------------


    /*@After("logPointCut()")
    public void afterVerify()
    {
        UserContext.remove();
        System.out.println("after切面结束1");
    }
    @AfterReturning(value = "logPointCut()")
    public void doAfterReturning() {
        //long takeTime = System.currentTimeMillis() - (long) threadInfo.getOrDefault(START_TIME, System.currentTimeMillis());
        System.out.println("切面结束1");
        UserContext.remove();
        //log.info("{}接口结束调用:耗时={}ms,result={}", takeTime);
    }*/

}
