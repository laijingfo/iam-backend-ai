package com.lenovo.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class JsonToListTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    List<String> parameter, JdbcType jdbcType) {
        // 不需要插入
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex)
            throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private List<String> parse(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // 兼容非 JSON 格式（如 {a,b,c} PostgreSQL 数组字符串）
            return Arrays.asList(json.replaceAll("[{}\"]", "").split(","));
        }
    }
}