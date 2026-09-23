package com.lenovo.controller;

import com.lenovo.entity.Workspace;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController extends BaseController {

    private final WorkspaceService workspaceService;

    @GetMapping("")
    public ResponseEntity query(@RequestParam(required = false) String creator, @RequestParam(required = false) String user, @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        return ok(workspaceService.query(creator, user, page, size));
    }

    @PostMapping("")
    public ResponseEntity create(@RequestBody Workspace workspace) {
        workspace.setCreator(SecurityUtils.getCurrentUsername());

        workspaceService.save(workspace);
        return ok(workspace);
    }

    @PutMapping("/{id}")
    public ResponseEntity update(@RequestBody Workspace workspace, @PathVariable Integer id) {

        if (workspace.getId() == null) {
            workspace.setId(id);
        }
        workspaceService.updateById(workspace);
        return ok(workspace);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity delete(@PathVariable Integer id) {
        workspaceService.delete(id);
        return ok();
    }

}
