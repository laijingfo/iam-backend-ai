package com.lenovo.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author : chenhao
 * @date : 2022/10/20
 * @description : 封装的基础result
 */
public class ApiResult<D> implements Serializable {

    private static final long serialVersionUID = -7479993950225353699L;
    /**
     * 数据
     */
    private D data;

    /**
     * 错误信息
     */
    private ApiError error;

    public ApiError getError() {
        return error;
    }

    public void setError(ApiError error) {
        this.error = error;
    }

    public D getData() {
        return data;
    }

    public void setData(D data) {
        this.data = data;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue", "WeakerAccess"})
    public static final class ApiResultBuilder<D> {

        private final ApiResult<D> apiResult = new ApiResult<>();

        public static <D> ApiResultBuilder<D> newBuilder() {
            return new ApiResultBuilder<>();
        }

        public ApiResultBuilder<D> error(String errorCode, String message) {
            if (this.apiResult.error == null) {
                this.apiResult.error = new ApiError();
            }
            this.apiResult.error.code = errorCode;
            this.apiResult.error.message = message;
            return this;
        }

        public ApiResultBuilder<D> stacktrace(String stacktrace) {
            if (this.apiResult.error == null) {
                this.apiResult.error = new ApiError();
            }
            this.apiResult.error.stacktrace = stacktrace;
            return this;
        }

        public ApiResultBuilder<D> data(D data) {
            if (this.apiResult.data == null) {
                this.apiResult.data = data;
            }
            return this;
        }

        public ApiResult<D> build() {
            return this.apiResult;
        }
    }

    public static final class ApiPageResultBuilder<I> {

        private final ApiResult<ApiData<I>> apiResult = new ApiResult<>();

        public static <I> ApiPageResultBuilder<I> newBuilder() {
            return new ApiPageResultBuilder<>();
        }

        public ApiPageResultBuilder<I> error(String errorCode, String message) {
            if (this.apiResult.error == null) {
                this.apiResult.error = new ApiError();
            }
            this.apiResult.error.code = errorCode;
            this.apiResult.error.message = message;
            return this;
        }

        public ApiPageResultBuilder<I> addErrorVariable(String variable) {
            this.apiResult.error.variables.add(variable);
            return this;
        }

        public ApiPageResultBuilder<I> pages() {
            return pages(20, 10, 0);
        }

        public ApiPageResultBuilder<I> pages(Integer itemsPerPage, boolean hasMore) {
            if (this.apiResult.data == null) {
                this.apiResult.data = new ApiData<>();
            }
            ApiData<I> apiData = this.apiResult.data;
            if (apiData.items == null) {
                apiData.items = new ArrayList<>();
            }

            apiData.itemsPerPage = itemsPerPage;
            apiData.hasMore = hasMore;
            return this;
        }

        @SuppressWarnings("SameParameterValue")
        public ApiPageResultBuilder<I> pages(Integer itemsPerPage, Integer itemCount, Integer page) {
            return pages(itemsPerPage, itemCount, page, false);
        }

        public ApiPageResultBuilder<I> pages(
                Integer itemsPerPage, Integer itemCount, Integer page, boolean hasMore) {
            if (this.apiResult.data == null) {
                this.apiResult.data = new ApiData<>();
            }
            ApiData<I> apiData = this.apiResult.data;
            if (apiData.items == null) {
                apiData.items = new ArrayList<>();
            }

            apiData.itemsPerPage = itemsPerPage;
            apiData.itemCount = itemCount;
            apiData.page = page;
            apiData.hasMore = hasMore;
            return this;
        }

        public ApiPageResultBuilder<I> pages(String scrollId, Integer itemCount, boolean hasMore) {
            if (this.apiResult.data == null) {
                this.apiResult.data = new ApiData<>();
            }
            ApiData<I> apiData = this.apiResult.data;
            if (apiData.items == null) {
                apiData.items = new ArrayList<>();
            }

            apiData.scrollId = scrollId;
            apiData.itemCount = itemCount;
            apiData.hasMore = hasMore;
            return this;
        }

        public ApiPageResultBuilder<I> dataEntries(Collection<I> entries) {
            if (this.apiResult.data == null) {
                this.apiResult.data = new ApiData<>();
            }
            ApiData<I> apiData = this.apiResult.data;
            apiData.addDataEntries(entries);
            return this;
        }

        public ApiPageResultBuilder<I> dataEntry(I entry) {
            if (this.apiResult.data == null) {
                this.apiResult.data = new ApiData<>();
            }
            ApiData<I> apiData = this.apiResult.data;
            apiData.addDataEntry(entry);
            return this;
        }

        public ApiResult<ApiData<I>> build() {
            return this.apiResult;
        }
    }

    /**
     * 分页信息
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class ApiData<I> {

        /**
         * 滚动id
         */
        private String scrollId;

        /**
         * 每页大小
         */
        private Integer itemsPerPage;

        /**
         * 数据总数
         */
        private Integer itemCount;

        /**
         * 页码
         */
        private Integer page;

        /**
         * 数据
         */
        private Collection<I> items;

        /**
         * 是否还有更多
         */
        private boolean hasMore = false;

        public String getScrollId() {
            return scrollId;
        }

        public void setScrollId(String scrollId) {
            this.scrollId = scrollId;
        }

        public Integer getItemsPerPage() {
            return itemsPerPage;
        }

        public void setItemsPerPage(Integer itemsPerPage) {
            this.itemsPerPage = itemsPerPage;
        }

        public Integer getItemCount() {
            return itemCount;
        }

        public void setItemCount(Integer itemCount) {
            this.itemCount = itemCount;
        }

        public Integer getPage() {
            return page;
        }

        public void setPage(Integer page) {
            this.page = page;
        }

        public Collection<I> getItems() {
            return items;
        }

        public void setItems(Collection<I> items) {
            this.items = items;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }

        public void addDataEntry(I data) {
            this.items.add(data);
        }

        public void addDataEntries(Collection<I> collection) {
            this.items.addAll(collection);
        }
    }

    /**
     * 错误信息
     */
    public static class ApiError {

        /**
         * 错误代码
         */
        private String code;

        /**
         * 错误信息
         */
        private String message;

        /**
         * 错误数据栈信息
         */
        private String stacktrace;

        /**
         * 错误源
         */
        private List<String> variables;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getStacktrace() {
            return stacktrace;
        }

        public void setStacktrace(String stacktrace) {
            this.stacktrace = stacktrace;
        }

        public List<String> getVariables() {
            return variables;
        }

        public void setVariables(List<String> variables) {
            this.variables = variables;
        }
    }
}
