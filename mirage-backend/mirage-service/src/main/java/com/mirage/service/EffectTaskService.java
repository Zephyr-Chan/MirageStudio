package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.enums.TaskStatus;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.EffectTask;
import com.mirage.dao.mapper.EffectTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 特效任务服务: CRUD + 投递准备
 *
 * <p>实际投递到 Redis Streams (XADD) 由上层 (web 层调用 TaskDispatcher) 完成,
 * 本服务负责创建任务记录并维护状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectTaskService {

    private final EffectTaskMapper effectTaskMapper;
    private final SnowflakeId snowflakeId;

    /**
     * 创建特效任务 (初始状态 PENDING)
     */
    @Transactional(rollbackFor = Exception.class)
    public EffectTask create(Long projectId, Long userId, Long sourceSnapshotAssetId,
                             Long templateId, String paramsJson) {
        EffectTask task = new EffectTask();
        task.setId(snowflakeId.nextId());
        task.setProjectId(projectId);
        task.setUserId(userId);
        task.setSourceSnapshotAssetId(sourceSnapshotAssetId);
        task.setTemplateId(templateId);
        task.setParamsJson(paramsJson);
        task.setStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        effectTaskMapper.insert(task);
        log.info("特效任务创建: taskId={}, projectId={}, templateId={}",
                task.getId(), projectId, templateId);
        return task;
    }

    /**
     * 更新任务状态 (带状态机校验)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long taskId, TaskStatus target, String errorMsg) {
        EffectTask task = getById(taskId);
        TaskStatus current = TaskStatus.valueOf(task.getStatus());
        if (!current.canTransitTo(target)) {
            throw new BusinessException(BizExceptionEnum.TASK_STATE_INVALID,
                    "状态不允许从 " + current + " 流转到 " + target);
        }
        task.setStatus(target.name());
        if (target == TaskStatus.RUNNING) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (target.isTerminal()) {
            task.setFinishedAt(LocalDateTime.now());
            if (errorMsg != null) {
                task.setErrorMsg(errorMsg);
            }
        }
        effectTaskMapper.updateById(task);
        log.info("特效任务状态更新: taskId={}, {} -> {}", taskId, current, target);
    }

    /**
     * 更新进度
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long taskId, int progress) {
        EffectTask task = getById(taskId);
        task.setProgress(Math.max(0, Math.min(100, progress)));
        effectTaskMapper.updateById(task);
    }

    /**
     * 绑定 ComfyUI prompt_id
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindComfyuiPromptId(Long taskId, String promptId) {
        EffectTask task = getById(taskId);
        task.setComfyuiPromptId(promptId);
        effectTaskMapper.updateById(task);
    }

    /**
     * 标记成功并绑定产出资产
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long taskId, Long resultAssetId) {
        EffectTask task = getById(taskId);
        task.setStatus(TaskStatus.SUCCESS.name());
        task.setResultAssetId(resultAssetId);
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        effectTaskMapper.updateById(task);
    }

    public EffectTask getById(Long taskId) {
        EffectTask task = effectTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(BizExceptionEnum.TASK_NOT_FOUND);
        }
        return task;
    }

    public Page<EffectTask> pageByProject(Long projectId, int current, int size) {
        return effectTaskMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<EffectTask>()
                        .eq(EffectTask::getProjectId, projectId)
                        .orderByDesc(EffectTask::getCreatedAt));
    }
}
