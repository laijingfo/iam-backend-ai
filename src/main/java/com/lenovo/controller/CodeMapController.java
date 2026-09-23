package com.lenovo.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lenovo.entity.CodeMap;
import com.lenovo.service.CodeMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/code")
@RequiredArgsConstructor
public class CodeMapController extends BaseController {

    private final CodeMapService codeMapService;

    @GetMapping("/types")
    public ResponseEntity types() {
        return ok(codeMapService.query().select("type").groupBy("type").list().stream().map(CodeMap::getType).collect(Collectors.toList()));
    }

    @GetMapping("/all")
    public ResponseEntity all() {
        List<CodeMap> list = codeMapService.query().list();
        List<String> types = list.stream().map(CodeMap::getType).distinct().collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();

        result.put("types", types);
        result.put("data", list);
        return ok(result);
    }

    @GetMapping("")
    public ResponseEntity query(String type) {
        return ok(codeMapService.query().eq("type", type).list());
    }

    @PostMapping("")
    public ResponseEntity save(@RequestBody CodeMap codeMap) {
        codeMapService.save(codeMap);
        return ok(codeMap);
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable Integer id, @RequestBody CodeMap codeMap) {
        codeMap.setId(id);
        codeMapService.updateById(codeMap);
        return ok(codeMap);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable Integer id) {
        codeMapService.removeById(id);
        return ok();
    }
}
