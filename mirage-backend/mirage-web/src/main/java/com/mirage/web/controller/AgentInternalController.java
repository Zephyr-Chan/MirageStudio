package com.mirage.web.controller;

import com.mirage.common.R;
import com.mirage.common.dto.TaskStatusDTO;
import com.mirage.common.enums.TaskStatus;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.AgentRun;
import com.mirage.dao.entity.AgentStep;
import com.mirage.dao.mapper.AgentRunMapper;
import com.mirage.dao.mapper.AgentStepMapper;
import com.mirage.service.EffectTaskService;
import com.mirage.service.ReconstructionTaskService;
import com.mirage.task.TaskStatusStore;
import com.mirage.task.WebSocketPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 内部控制器: 供 Python Agent 回调 (更新任务状态 / 记录 Agent 运行步骤)
 *
 * <p>该接口不经过 JWT 鉴权 (在 SecurityConfig 中放行 /api/internal/**),
 * 生产环境建议增加 IP 白名单或共享密钥签名校验。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/internal/agent")
@RequiredArgsConstructor
public class AgentInternalController {

    private final ReconstructionTaskService reconTaskService;
    private final EffectTaskService effectTaskService;
    private final TaskStatusStore taskStatusStore;
    private final WebSocketPusher webSocketPusher;
    private final AgentRunMapper agentRunMapper;
    private final AgentStepMapper agentStepMapper;
    private final SnowflakeId snowflakeId;

    /**
     * 更新任务状态 (Agent 执行完成后回调)
     *
     * <p>请求体: { taskType, taskId, status, progress, message, errorMsg, resultAssetId }</p>
     */
    @PostMapping("/task-status")
    public R<Void> updateTaskStatus(@RequestBody Map<String, Object> body) {
        String taskType = (String) body.get("taskType");
        Long taskId = toLong(body.get("taskId"));
        String status = (String) body.get("status");
        int progress = toInt(body.get("progress"), 0);
        String message = (String) body.get("message");
        String errorMsg = (String) body.get("errorMsg");
        Long resultAssetId = toLong(body.get("resultAssetId"));

        TaskStatus target = TaskStatus.valueOf(status);
        if ("RECON".equals(taskType)) {
            if (target == TaskStatus.SUCCESS && resultAssetId != null) {
                reconTaskService.markSuccess(taskId, resultAssetId);
            } else {
                reconTaskService.updateStatus(taskId, target, errorMsg);
            }
        } else if ("EFFECT".equals(taskType)) {
            if (target == TaskStatus.SUCCESS && resultAssetId != null) {
                effectTaskService.markSuccess(taskId, resultAssetId);
            } else {
                effectTaskService.updateStatus(taskId, target, errorMsg);
            }
        }

        // 更新 Redis 实时状态
        taskStatusStore.save(TaskStatusDTO.builder()
                .taskId(String.valueOf(taskId))
                .taskType(taskType)
                .status(status)
                .progress(progress)
                .message(message)
                .errorMsg(errorMsg)
                .updatedAt(System.currentTimeMillis())
                .build());

        // WebSocket 推送
        webSocketPusher.pushTaskStatus(String.valueOf(taskId), taskType, status, progress, message);
        log.info("Agent 回调更新任务状态: taskType={}, taskId={}, status={}", taskType, taskId, status);
        return R.ok();
    }

    /**
     * 更新任务进度
     */
    @PostMapping("/task-progress")
    public R<Void> updateProgress(@RequestBody Map<String, Object> body) {
        Long taskId = toLong(body.get("taskId"));
        String taskType = (String) body.get("taskType");
        int progress = toInt(body.get("progress"), 0);
        String message = (String) body.get("message");

        taskStatusStore.updateProgress(taskId, progress, message);
        webSocketPusher.pushProgress(String.valueOf(taskId), progress, message);
        return R.ok();
    }

    /**
     * 创建 Agent 运行记录
     */
    @PostMapping("/runs")
    public R<AgentRun> createAgentRun(@RequestBody Map<String, Object> body) {
        AgentRun run = new AgentRun();
        run.setId(snowflakeId.nextId());
        run.setProjectId(toLong(body.get("projectId")));
        run.setUserId(toLong(body.get("userId")));
        run.setGoalText((String) body.get("goalText"));
        run.setPlanJson((String) body.get("planJson"));
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setTotalTokens(0);
        run.setTotalSteps(0);
        agentRunMapper.insert(run);
        log.info("Agent 运行记录创建: runId={}", run.getId());
        return R.ok(run);
    }

    /**
     * 完成 Agent 运行记录
     */
    @PostMapping("/runs/{runId}/finish")
    public R<Void> finishAgentRun(@PathVariable Long runId, @RequestBody Map<String, Object> body) {
        AgentRun run = agentRunMapper.selectById(runId);
        if (run != null) {
            run.setStatus((String) body.getOrDefault("status", "SUCCESS"));
            run.setResultSummary((String) body.get("resultSummary"));
            run.setTotalTokens(toInt(body.get("totalTokens"), run.getTotalTokens()));
            run.setTotalSteps(toInt(body.get("totalSteps"), run.getTotalSteps()));
            run.setFinishedAt(LocalDateTime.now());
            agentRunMapper.updateById(run);
        }
        return R.ok();
    }

    /**
     * 记录 Agent 步骤
     */
    @PostMapping("/steps")
    public R<AgentStep> recordStep(@RequestBody Map<String, Object> body) {
        AgentStep step = new AgentStep();
        step.setId(snowflakeId.nextId());
        step.setAgentRunId(toLong(body.get("agentRunId")));
        step.setStepIndex(toInt(body.get("stepIndex"), 0));
        step.setThought((String) body.get("thought"));
        step.setAction((String) body.get("action"));
        step.setActionInput((String) body.get("actionInput"));
        step.setObservation((String) body.get("observation"));
        step.setStatus((String) body.getOrDefault("status", "SUCCESS"));
        agentStepMapper.insert(step);
        return R.ok(step);
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        return Long.parseLong(o.toString());
    }

    private int toInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
