package com.lenovo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    /**
     * 新的分页插件,一缓和二缓遵循mybatis的规则,需要设置 MybatisConfiguration#useDeprecatedExecutor = false 避免缓存出现问题(该属性会在旧插件移除后一同移除)
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * 分页插件加固版。
     * <p>
     * MyBatis-Plus 执行分页查询时会先跑一次 COUNT，这条 COUNT SQL 是把原 SQL 交给 JSqlParser
     * 重新生成的；而 JSqlParser 对部分 PostgreSQL 方言并不友好（例如正则 {@code ~* '\y(...)\y'}、
     * {@code '%'||?||'%'} 拼接、SQL 里出现连续空行等），可能出现
     * <ul>
     *   <li>解析失败：MP 走兜底，参数不会丢；</li>
     *   <li><b>静默截断</b>：JSqlParser 不报错，但生成的 COUNT SQL 丢掉了 WHERE 后面的部分内容，
     *       于是 COUNT SQL 里的 {@code ?} 个数少于 MyBatis 的参数映射个数，
     *       绑定时 PostgreSQL 直接抛
     *       {@code PSQLException: The column index is out of range: N, number of columns: M}。</li>
     * </ul>
     * 这里做一次兜底校验：一旦重写后的 COUNT SQL 占位符个数与原 SQL 不一致，就退回
     * {@code SELECT COUNT(*) FROM (原SQL) TOTAL}，保证参数个数永远对得上。
     */
    static class SafePaginationInnerInterceptor extends PaginationInnerInterceptor {

        SafePaginationInnerInterceptor(DbType dbType) {
            super(dbType);
        }

        @Override
        public String autoCountSql(IPage<?> page, String sql) {
            String countSql = super.autoCountSql(page, sql);
            if (countPlaceholder(countSql) != countPlaceholder(sql)) {
                // JSqlParser 重写后占位符个数变了（通常是方言解析问题导致 SQL 被截断），
                // 退回低阶 COUNT SQL：把原 SQL 整体包一层，占位符一个不动，绝对不会错位。
                return lowLevelCountSql(sql);
            }
            return countSql;
        }

        private static int countPlaceholder(String sql) {
            if (sql == null) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < sql.length(); i++) {
                if (sql.charAt(i) == '?') {
                    count++;
                }
            }
            return count;
        }
    }
}
