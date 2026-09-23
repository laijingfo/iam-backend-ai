package com.lenovo.constant;

/**
 * @author Iklas.Alzhanov
 */
public final class HttpConstants {

    public static final String TOKEN = "token";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CORRELATION_ID = "Correlation-Id";
    public static final String MESSAGE_ID = "Message-Id";
    public static final String USER_ID = "User-Id";
    public static final String USER_ROLES = "User-Roles";
    public static final String MERCHANT_ID = "Merchant-Id";
    public static final String POS_ID = "Pos-Id";
    public static final String POS_ROLE = "Pos-Role";
    public static final String GPS_LOCATION = "X-Gps-Location";
    public static final String DEVICE_ID = "X-Device-Id";
    public static final String DEVICE_TYPE = "X-Device-Type";
    public static final String CARRIER = "X-Carrier";
    public static final String FIELD_ERROR = "error";
    public static final String FIELD_VALUE = "value";
    // Headers used for paging
    public static final String OFFSET_HEADER = "X-Offset";
    public static final String LIMIT_HEADER = "X-Limit";
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    public static final String MENU_KEY = "menuKey";

    private HttpConstants() {
    }
}
