package com.mirage.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.R;
import com.mirage.common.dto.EffectTaskDTO;
import com.mirage.dao.entity.EffectTask;
import com.mirage.service.EffectTaskService;
import com.mirage.task.TaskDispatcher;
import com.mirage.web.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 特效任务控制器: 提交特效任务
 */
@Slf4j
@RestController
@RequestMapping("/api/effects")
@RequiredArgsConstructor
public class EffectController {

    private final EffectTaskService effectTaskService;
    private final TaskDispatcher taskDispatcher;

    /**
     * 提交特效任务
     * 流程: 创建任务记录(PENDING) -> 投递 Redis Stream (stream:effect) -> 状态置 QUEUED
     */
    @PostMapping
    public R<Map<String, Object>> submit(@Valid @RequestBody EffectTaskDTO dto) {
        Long userId = UserContext.requireUserId();

        // 1. 创建任务记录
        EffectTask task = effectTaskService.create(
                dto.getProjectId(), userId, dto.getSourceSnapshotAssetId(),
                dto.getTemplateId(), dto.getParamsJson());

        // 2. 投递到 Redis Stream (stream:effect)
        Map<String, String> payload = new HashMap<>();
        payload.put("projectId", String.valueOf(dto.getProjectId()));
        if (dto.getSourceSnapshotAssetId() != null) {
            payload.put("sourceSnapshotAssetId", String.valueOf(dto.getSourceSnapshotAssetId()));
        }
        if (dto.getTemplateId() != null) {
            payload.put("templateId", String.valueOf(dto.getTemplateId()));
        }
        payload.put("paramsJson", dto.getParamsJson() == null ? "{}" : dto.getParamsJson());
        taskDispatcher.dispatchEffect(task.getId(), payload);

        // 3. 返回任务信息
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        return R.ok("特效任务已提交", result);
    }

    @GetMapping("/{id}")
    public R<EffectTask> get(@PathVariable Long id) {
        UserContext.requireUserId();
        return R.ok(effectTaskService.getById(id));
    }

    @GetMapping
    public R<Page<EffectTask>> list(@RequestParam Long projectId,
                                    @RequestParam(defaultValue = "1") int current,
                                    @RequestParam(defaultValue = "20") int size) {
        UserContext.requireUserId();
        return R.ok(effectTaskService.pageByProject(projectId, current, size));
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        UserContext.requireUserId();
        taskDispatcher.cancelTask(id, "EFFECT");
        return R.ok();
    }
}
