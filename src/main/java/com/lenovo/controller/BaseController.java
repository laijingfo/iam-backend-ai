package com.lenovo.controller;

import com.lenovo.bean.ApiResult;
import com.lenovo.bean.ApiResult.ApiData;
import com.lenovo.bean.ApiResult.ApiPageResultBuilder;
import com.lenovo.bean.ApiResult.ApiResultBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collection;

/**
 * @author : chenhao
 * @date : 2022/10/20
 * @description : 基础controller
 */
public abstract class BaseController {

    protected final Logger logger = LogManager.getLogger(getClass());

    protected ResponseEntity<ApiResult<Void>> ok() {
        return ResponseEntity.ok().body(result());
    }

    protected <D> ResponseEntity<ApiResult<D>> ok(D data) {
        return ResponseEntity.ok().body(result(data));
    }

    protected ResponseEntity<ApiResult<Void>> ok(HttpStatus status) {
        return ResponseEntity.status(status).body(result());
    }

    protected <D> ResponseEntity<ApiResult<D>> ok(D data, HttpStatus status) {
        return ResponseEntity.status(status).body(result(data));
    }

    protected <I> ResponseEntity<ApiResult<ApiData<I>>> okPage(I data) {
        return ResponseEntity.ok().body(results(data));
    }

    protected <I> ResponseEntity<ApiResult<ApiData<I>>> okPage(I data, HttpStatus status) {
        return ResponseEntity.status(status).body(results(data));
    }

    protected <I> ResponseEntity<ApiResult<ApiData<I>>> okPage(Collection<I> collection) {
        return ResponseEntity.ok().body(results(collection));
    }

    protected <I> ResponseEntity<ApiResult<ApiData<I>>> okPage(
            Collection<I> collection, HttpStatus status) {
        return ResponseEntity.status(status).body(results(collection));
    }

//  protected <I> ResponseEntity<ApiResult<ApiData<I>>> okPage(PageInfo<?> pageInfo, List<I> list) {
//    return ResponseEntity.ok().body(results(pageInfo, list));
//  }

//  protected <E extends Exception> ResponseEntity<ApiResult<?>> error(E error) {
//    Assert.notNull(error, "error");
//    return ResponseEntity.status(error.)
//        .body(ApiResultBuilder.newBuilder().error(error.getCode(), error.getMessage()).build());
//  }

    private static <D> ApiResult<D> result() {
        return ApiResultBuilder.<D>newBuilder().build();
    }

    private static <D> ApiResult<D> result(D data) {
        return ApiResultBuilder.<D>newBuilder().data(data).build();
    }

    private static <I> ApiResult<ApiData<I>> results(I data) {
        return ApiPageResultBuilder.<I>newBuilder().pages().dataEntry(data).build();
    }

    private static <I> ApiResult<ApiData<I>> results(Collection<I> collection) {
        return ApiPageResultBuilder.<I>newBuilder().pages().dataEntries(collection).build();
    }

//  private static <I> ApiResult<ApiData<I>> results(PageInfo<?> pageInfo, List<I> list) {
//    Assert.notNull(pageInfo, "pageInfo");
//    if (pageInfo instanceof ScrollPageInfo<?> scrollPageInfo) {
//      return ApiPageResultBuilder.<I>newBuilder()
//          .pages(scrollPageInfo.getScrollId(), (int) pageInfo.getTotal(), pageInfo.isHasNextPage())
//          .dataEntries(list)
//          .build();
//    }
//    return ApiPageResultBuilder.<I>newBuilder()
//        .pages(
//            pageInfo.getPageSize(),
//            (int) pageInfo.getTotal(),
//            pageInfo.getPageNum(),
//            pageInfo.isHasNextPage())
//        .dataEntries(list)
//        .build();
//  }
}
