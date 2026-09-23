package com.lenovo.security.rest;

import com.lenovo.security.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 *
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/online")
public class OnlineController {

    private final OnlineUserService onlineUserService;

    @GetMapping
    public ResponseEntity<Object> queryOnlineUser(String filter) {
        return new ResponseEntity<>(onlineUserService.getAll(filter), HttpStatus.OK);
    }


    @DeleteMapping
    public ResponseEntity<Object> deleteOnlineUser(@RequestBody Set<String> keys) throws Exception {
        for (String key : keys) {
            // 解密Key
//            key = EncryptUtils.desDecrypt(key);
            onlineUserService.kickOut(key);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
    @DeleteMapping("/kickOutALl")
    public ResponseEntity<Object> kickOutALl() throws Exception {
            onlineUserService.kickOutALl();
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
