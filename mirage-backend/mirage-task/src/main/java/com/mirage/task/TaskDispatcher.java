package com.mirage.task;

import com.mirage.common.constant.RedisKeyConstants;
import com.mirage.common.dto.TaskStatusDTO;
import com.mirage.common.enums.TaskStatus;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务投递器: 投递任务到 Redis Streams (XADD) + 状态更新
 *
 * <p>任务流转: 创建(PENDING) -> 投递(XADD) -> 状态置 QUEUED</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatcher {

    private final StringRedisTemplate redisTemplate;
    private final TaskStatusStore taskStatusStore;
    private final WebSocketPusher webSocketPusher;

    /**
     * 投递重建任务到 stream:recon
     *
     * @param taskId  任务ID
     * @param payload 任务负载字段
     * @return Redis Stream 消息ID
     */
    public String dispatchRecon(Long taskId, Map<String, String> payload) {
        return doDispatch(RedisKeyConstants.STREAM_RECON, "RECON", taskId, payload);
    }

    /**
     * 投递特效任务到 stream:effect
     */
    public String dispatchEffect(Long taskId, Map<String, String> payload) {
        return doDispatch(RedisKeyConstants.STREAM_EFFECT, "EFFECT", taskId, payload);
    }

    /**
     * 投递渲染任务到 stream:render
     */
    public String dispatchRender(Long taskId, Map<String, String> payload) {
        return doDispatch(RedisKeyConstants.STREAM_RENDER, "RENDER", taskId, payload);
    }

    /**
     * 投递微调任务到 stream:finetune
     */
    public String dispatchFinetune(Long taskId, Map<String, String> payload) {
        return doDispatch(RedisKeyConstants.STREAM_FINETUNE, "FINETUNE", taskId, payload);
    }

    /**
     * 投递 Agent 任务到 stream:agent
     */
    public String dispatchAgent(Long agentRunId, Map<String, String> payload) {
        return doDispatch(RedisKeyConstants.STREAM_AGENT, "AGENT", agentRunId, payload);
    }

    private String doDispatch(String streamKey, String taskType, Long taskId, Map<String, String> payload) {
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put("taskType", taskType);
            fields.put("taskId", String.valueOf(taskId));
            fields.put("dispatchTime", String.valueOf(System.currentTimeMillis()));
            if (payload != null) {
                fields.putAll(payload);
            }

            // XADD stream:* * field1 value1 ...
            String messageId = redisTemplate.opsForStream()
                    .add(streamKey, fields)
                    .getValue();

            // 更新 Redis 实时状态为 QUEUED
            taskStatusStore.save(TaskStatusDTO.builder()
                    .taskId(String.valueOf(taskId))
                    .taskType(taskType)
                    .status(TaskStatus.QUEUED.name())
                    .progress(0)
                    .message("任务已投递, 等待执行")
                    .updatedAt(System.currentTimeMillis())
                    .build());

            // WebSocket 推送状态变更
            webSocketPusher.pushTaskStatus(String.valueOf(taskId), taskType,
                    TaskStatus.QUEUED.name(), 0, "任务已投递");

            log.info("任务投递成功: stream={}, taskType={}, taskId={}, msgId={}",
                    streamKey, taskType, taskId, messageId);
            return messageId;
        } catch (Exception e) {
            log.error("任务投递失败: taskType={}, taskId={}", taskType, taskId, e);
            throw new BusinessException(BizExceptionEnum.TASK_DISPATCH_FAIL,
                    "任务投递失败: " + e.getMessage());
        }
    }

    /**
     * 取消任务: 从 Stream 中无法删除已投递消息, 仅更新状态为 CANCELLED
     */
    public void cancelTask(Long taskId, String taskType) {
        taskStatusStore.save(TaskStatusDTO.builder()
                .taskId(String.valueOf(taskId))
                .taskType(taskType)
                .status(TaskStatus.CANCELLED.name())
                .progress(0)
                .message("任务已取消")
                .updatedAt(System.currentTimeMillis())
                .build());
        webSocketPusher.pushTaskStatus(String.valueOf(taskId), taskType,
                TaskStatus.CANCELLED.name(), 0, "任务已取消");
        log.info("任务已取消: taskId={}, taskType={}", taskId, taskType);
    }
}
