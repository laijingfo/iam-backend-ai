package com.lenovo.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseUtil {


    public static List<String> splitApplication(String application) {
        String[] appName = application.split("_");
        String appId;
        String app;
        if (appName.length > 1) {
            appId = appName[0];
            app = String.join("_", Arrays.copyOfRange(appName, 1, appName.length));
        } else {
            appId = application;
            app = application;
        }
        List<String> r = new ArrayList<>();
        r.add(appId);
        r.add(app);
        return r;
    }

    public static Double strToDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
