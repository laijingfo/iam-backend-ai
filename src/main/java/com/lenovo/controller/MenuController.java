package com.lenovo.controller;

import com.lenovo.bean.ApiResult;
import com.lenovo.bean.MenuRouterBean;
import com.lenovo.entity.MenuRouter;
import com.lenovo.service.MenuRouterService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/menu")
public class MenuController extends BaseController {
    private final MenuRouterService menuRouterService;

    public MenuController(MenuRouterService menuRouterService) {
        this.menuRouterService = menuRouterService;
    }

    @GetMapping(path = "/available")
    public ResponseEntity available(@RequestParam(required = false) Boolean isMenu, @RequestParam(required = false) boolean defaultPage) {
        return ok(menuRouterService.menus(isMenu, defaultPage));
    }

    @GetMapping("/menuByUserRole")
    public ResponseEntity<ApiResult<List<MenuRouterBean>>> menuByUserRole(@RequestParam String userRole) {
        return ok(menuRouterService.findMenuListByUserRole(userRole));
    }


    @GetMapping(path = "/cleanMenuCache")
    public ResponseEntity cleanMenuCache() {
        menuRouterService.cleanMenuCache();
        return ok();
    }

    @PostMapping
    public ResponseEntity addMenuRouter(@RequestBody MenuRouter menuRouter) {
        return ok(menuRouterService.addMenuRouter(menuRouter));
    }

    @PutMapping
    public ResponseEntity updateMenuRouter(@RequestBody MenuRouter menuRouter) {
        return ok(menuRouterService.updateMenuRouter(menuRouter));
    }

    @PostMapping("/updateMenuRouterSort")
    public ResponseEntity updateMenuRouterSort(@RequestBody List<MenuRouterBean> menuRouter) {
        menuRouterService.updateMenuRouterSort(menuRouter);
        return ok();
    }

    @PutMapping("/updateMenuRouterShow")
    public ResponseEntity updateMenuRouterShow(@RequestParam("ids") Set<Integer> ids) {
        menuRouterService.updateMenuRouterShow(ids);
        return ok();
    }

    @DeleteMapping("/{menuId}")
    public ResponseEntity delMenuRouter(@PathVariable Integer menuId) {
        menuRouterService.delMenuRouter(menuId);
        return ok();
    }

    @PostMapping(value = "/uploadImage", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResult<String>> uploadBannerImage(@RequestParam("file") MultipartFile file) {
        return ok(menuRouterService.uploadImage(file));
    }
}
