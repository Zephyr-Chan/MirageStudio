package com.mirage.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.R;
import com.mirage.common.dto.ProjectDTO;
import com.mirage.dao.entity.Project;
import com.mirage.service.ProjectService;
import com.mirage.web.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目控制器: CRUD
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public R<Project> create(@Valid @RequestBody ProjectDTO dto) {
        Long userId = UserContext.requireUserId();
        Project project = projectService.create(userId, dto.getName(),
                dto.getDescription(), dto.getCoverAssetId());
        return R.ok(project);
    }

    @PutMapping("/{id}")
    public R<Project> update(@PathVariable Long id, @RequestBody ProjectDTO dto) {
        Long userId = UserContext.requireUserId();
        Project project = projectService.update(id, userId, dto.getName(),
                dto.getDescription(), dto.getCoverAssetId());
        return R.ok(project);
    }

    @GetMapping("/{id}")
    public R<Project> get(@PathVariable Long id) {
        UserContext.requireUserId();
        return R.ok(projectService.getById(id));
    }

    @GetMapping
    public R<Page<Project>> list(@RequestParam(defaultValue = "1") int current,
                                 @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.requireUserId();
        return R.ok(projectService.pageByUser(userId, current, size));
    }

    @PutMapping("/{id}/archive")
    public R<Void> archive(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        projectService.archive(id, userId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        projectService.delete(id, userId);
        return R.ok();
    }

    @GetMapping("/count")
    public R<Map<String, Object>> count() {
        Long userId = UserContext.requireUserId();
        Page<Project> page = projectService.pageByUser(userId, 1, 1);
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        return R.ok(result);
    }
}
