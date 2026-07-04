package com.mirage.task;

import com.mirage.common.constant.RedisKeyConstants;
import com.mirage.common.dto.TaskStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 任务实时状态存储: Redis Hash 存储任务状态 (task:status:{taskId})
 *
 * <p>key: task:status:{taskId}
 * field: taskType, status, progress, message, errorMsg, updatedAt</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskStatusStore {

    /** 状态过期时间 7 天 (秒) */
    private static final long TTL_SECONDS = 7 * 24 * 3600L;

    private final StringRedisTemplate redisTemplate;

    /**
     * 保存任务状态
     */
    public void save(TaskStatusDTO dto) {
        String key = RedisKeyConstants.taskStatus(dto.getTaskId());
        Map<String, String> fields = new java.util.HashMap<>();
        if (dto.getTaskType() != null) {
            fields.put("taskType", dto.getTaskType());
        }
        if (dto.getStatus() != null) {
            fields.put("status", dto.getStatus());
        }
        fields.put("progress", String.valueOf(dto.getProgress() == null ? 0 : dto.getProgress()));
        if (dto.getMessage() != null) {
            fields.put("message", dto.getMessage());
        }
        if (dto.getErrorMsg() != null) {
            fields.put("errorMsg", dto.getErrorMsg());
        }
        fields.put("updatedAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
        log.debug("任务状态已保存: taskId={}, status={}", dto.getTaskId(), dto.getStatus());
    }

    /**
     * 更新单个字段
     */
    public void updateField(Long taskId, String field, String value) {
        String key = RedisKeyConstants.taskStatus(taskId);
        redisTemplate.opsForHash().put(key, field, value);
        redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    /**
     * 更新进度
     */
    public void updateProgress(Long taskId, int progress, String message) {
        String key = RedisKeyConstants.taskStatus(taskId);
        redisTemplate.opsForHash().put(key, "progress", String.valueOf(progress));
        if (message != null) {
            redisTemplate.opsForHash().put(key, "message", message);
        }
        redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.expire(key, java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    /**
     * 查询任务状态
     */
    public TaskStatusDTO get(Long taskId) {
        String key = RedisKeyConstants.taskStatus(taskId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        return TaskStatusDTO.builder()
                .taskId(String.valueOf(taskId))
                .taskType(getStr(entries, "taskType"))
                .status(getStr(entries, "status"))
                .progress(getInt(entries, "progress"))
                .message(getStr(entries, "message"))
                .errorMsg(getStr(entries, "errorMsg"))
                .updatedAt(getLong(entries, "updatedAt"))
                .build();
    }

    /**
     * 查询任务状态
     */
    public TaskStatusDTO get(String taskId) {
        try {
            return get(Long.parseLong(taskId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 删除任务状态
     */
    public void delete(Long taskId) {
        redisTemplate.delete(RedisKeyConstants.taskStatus(taskId));
    }

    private String getStr(Map<Object, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private Integer getInt(Map<Object, Object> map, String key) {
        String v = getStr(map, key);
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getLong(Map<Object, Object> map, String key) {
        String v = getStr(map, key);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
