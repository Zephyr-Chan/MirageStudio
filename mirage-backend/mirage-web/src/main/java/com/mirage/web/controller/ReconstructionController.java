package com.mirage.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.R;
import com.mirage.common.dto.ReconTaskDTO;
import com.mirage.dao.entity.ReconstructionTask;
import com.mirage.service.ReconstructionTaskService;
import com.mirage.task.TaskDispatcher;
import com.mirage.web.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 重建任务控制器: 提交重建任务
 */
@Slf4j
@RestController
@RequestMapping("/api/recon")
@RequiredArgsConstructor
public class ReconstructionController {

    private final ReconstructionTaskService reconTaskService;
    private final TaskDispatcher taskDispatcher;

    /**
     * 提交 3DGS 重建任务
     * 流程: 创建任务记录(PENDING) -> 投递 Redis Stream -> 状态置 QUEUED
     */
    @PostMapping
    public R<Map<String, Object>> submit(@Valid @RequestBody ReconTaskDTO dto) {
        Long userId = UserContext.requireUserId();

        // 1. 创建任务记录
        ReconstructionTask task = reconTaskService.create(
                dto.getProjectId(), userId, dto.getSourceAssetIds(), dto.getParamsJson());

        // 2. 投递到 Redis Stream (stream:recon)
        Map<String, String> payload = new HashMap<>();
        payload.put("projectId", String.valueOf(dto.getProjectId()));
        payload.put("sourceAssetIds", dto.getSourceAssetIds().toString());
        payload.put("paramsJson", dto.getParamsJson() == null ? "{}" : dto.getParamsJson());
        taskDispatcher.dispatchRecon(task.getId(), payload);

        // 3. 返回任务信息
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("status", task.getStatus());
        return R.ok("重建任务已提交", result);
    }

    @GetMapping("/{id}")
    public R<ReconstructionTask> get(@PathVariable Long id) {
        UserContext.requireUserId();
        return R.ok(reconTaskService.getById(id));
    }

    @GetMapping
    public R<Page<ReconstructionTask>> list(@RequestParam Long projectId,
                                            @RequestParam(defaultValue = "1") int current,
                                            @RequestParam(defaultValue = "20") int size) {
        UserContext.requireUserId();
        return R.ok(reconTaskService.pageByProject(projectId, current, size));
    }

    /**
     * 取消任务
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        UserContext.requireUserId();
        taskDispatcher.cancelTask(id, "RECON");
        return R.ok();
    }
}
