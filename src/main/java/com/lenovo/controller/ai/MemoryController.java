package com.lenovo.controller.ai;

import com.lenovo.ai.memory.facade.MemoryFacade;
import com.lenovo.ai.memory.model.MemoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryFacade memoryFacade;          // ★ 唯一记忆入口

    @GetMapping("/memory/recall")
    public List<MemoryItem> recall(@RequestParam String userId,
                                   @RequestParam String q,
                                   @RequestParam(defaultValue = "10") int topK) {
        return memoryFacade.recall(userId, q, topK);
    }

    @PostMapping("/memory/save")
    public void save(@RequestParam String userId,
                     @RequestParam String type,
                     @RequestParam String content,
                     @RequestParam(defaultValue = "0.5") double importance) {
        memoryFacade.save(userId, type, content, importance);
    }
}