package com.mirage.task;

import com.mirage.common.dto.TaskStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 推送器: STOMP 推送任务状态到前端
 *
 * <p>推送目的地:
 * /topic/task/{taskId}   单任务状态更新
 * /topic/user/{userId}/tasks  用户维度任务列表更新</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPusher {

    private final SimpMessagingTemplate messagingTemplate;

    /** 单任务状态更新目的地前缀 */
    private static final String TASK_TOPIC_PREFIX = "/topic/task/";

    /** 全局任务更新目的地 (前端统一订阅此 topic) */
    private static final String GLOBAL_TASK_TOPIC = "/topic/tasks";

    /** 用户任务更新目的地前缀 */
    private static final String USER_TASK_TOPIC_PREFIX = "/topic/user/";

    /**
     * 推送任务状态变更
     *
     * @param taskId    任务ID
     * @param taskType  任务类型
     * @param status    状态
     * @param progress  进度 0-100
     * @param message   提示信息
     */
    public void pushTaskStatus(String taskId, String taskType, String status,
                               int progress, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("taskType", taskType);
        payload.put("status", status);
        payload.put("progress", progress);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());

        // 推送到 per-task topic 和全局 topic
        messagingTemplate.convertAndSend(TASK_TOPIC_PREFIX + taskId, payload);
        messagingTemplate.convertAndSend(GLOBAL_TASK_TOPIC, payload);
        log.debug("WebSocket 推送任务状态: taskId={}, status={}, progress={}%",
                taskId, status, progress);
    }

    /**
     * 推送完整任务状态 DTO
     */
    public void pushTaskStatusDTO(TaskStatusDTO dto) {
        messagingTemplate.convertAndSend(TASK_TOPIC_PREFIX + dto.getTaskId(), dto);
        messagingTemplate.convertAndSend(GLOBAL_TASK_TOPIC, dto);
        log.debug("WebSocket 推送任务状态: taskId={}, status={}", dto.getTaskId(), dto.getStatus());
    }

    /**
     * 推送进度更新
     */
    public void pushProgress(String taskId, int progress, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("progress", progress);
        payload.put("message", message);
        payload.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend(TASK_TOPIC_PREFIX + taskId, payload);
        messagingTemplate.convertAndSend(GLOBAL_TASK_TOPIC, payload);
    }

    /**
     * 推送用户维度的任务通知
     */
    public void pushUserTaskUpdate(Long userId, TaskStatusDTO dto) {
        messagingTemplate.convertAndSend(USER_TASK_TOPIC_PREFIX + userId + "/tasks", dto);
    }
}
