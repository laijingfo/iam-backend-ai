package com.lenovo.notify;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ResourceBundle;

/**
 * @author : chenhao
 * @date : 2023/2/20
 * @description :
 */
public class PropertiesUtils {
    private static final Logger logger = LoggerFactory.getLogger(PropertiesUtils.class);

    private static final String BUNDLE_NAME = "application";

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);


    public static String getValue(String key) {

        //参数校验
        if (StringUtils.isBlank(key)) {
            logger.warn("an unknown anomaly occurred !");
        }

        String value = null;
        try {
            value = BUNDLE.getString(StringUtils.trim(key));
        } catch (Exception e) {
            logger.warn(String.format("value not existed: %s", key), e);
        }

        return StringUtils.isBlank(value) ? StringUtils.EMPTY : value;
    }
}
