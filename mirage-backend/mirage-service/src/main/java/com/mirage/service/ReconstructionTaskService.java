package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirage.common.enums.TaskStatus;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.ReconstructionTask;
import com.mirage.dao.mapper.ReconstructionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重建任务服务: CRUD + 投递准备
 *
 * <p>实际投递到 Redis Streams (XADD) 由上层 (web 层调用 TaskDispatcher) 完成,
 * 本服务负责创建任务记录并维护状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconstructionTaskService {

    private final ReconstructionTaskMapper reconTaskMapper;
    private final SnowflakeId snowflakeId;
    private final ObjectMapper objectMapper;

    /**
     * 创建重建任务 (初始状态 PENDING)
     *
     * @param projectId       项目ID
     * @param userId          用户ID
     * @param sourceAssetIds  输入照片资产ID列表
     * @param paramsJson      参数 JSON
     * @return 重建任务实体
     */
    @Transactional(rollbackFor = Exception.class)
    public ReconstructionTask create(Long projectId, Long userId,
                                     List<Long> sourceAssetIds, String paramsJson) {
        ReconstructionTask task = new ReconstructionTask();
        task.setId(snowflakeId.nextId());
        task.setProjectId(projectId);
        task.setUserId(userId);
        try {
            task.setSourceAssetIds(objectMapper.writeValueAsString(sourceAssetIds));
        } catch (Exception e) {
            throw new BusinessException(BizExceptionEnum.PARAM_INVALID, "资产ID序列化失败");
        }
        task.setStatus(TaskStatus.PENDING.name());
        task.setParamsJson(paramsJson);
        task.setProgress(0);
        reconTaskMapper.insert(task);
        log.info("重建任务创建: taskId={}, projectId={}, photoCount={}",
                task.getId(), projectId, sourceAssetIds.size());
        return task;
    }

    /**
     * 更新任务状态 (带状态机校验)
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long taskId, TaskStatus target, String errorMsg) {
        ReconstructionTask task = getById(taskId);
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
        reconTaskMapper.updateById(task);
        log.info("重建任务状态更新: taskId={}, {} -> {}", taskId, current, target);
    }

    /**
     * 更新进度
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long taskId, int progress) {
        ReconstructionTask task = getById(taskId);
        task.setProgress(Math.max(0, Math.min(100, progress)));
        reconTaskMapper.updateById(task);
    }

    /**
     * 标记成功并绑定产出 splat 资产
     */
    @Transactional(rollbackFor = Exception.class)
    public void markSuccess(Long taskId, Long splatAssetId) {
        ReconstructionTask task = getById(taskId);
        task.setStatus(TaskStatus.SUCCESS.name());
        task.setSplatAssetId(splatAssetId);
        task.setProgress(100);
        task.setFinishedAt(LocalDateTime.now());
        reconTaskMapper.updateById(task);
    }

    public ReconstructionTask getById(Long taskId) {
        ReconstructionTask task = reconTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(BizExceptionEnum.TASK_NOT_FOUND);
        }
        return task;
    }

    public Page<ReconstructionTask> pageByProject(Long projectId, int current, int size) {
        return reconTaskMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ReconstructionTask>()
                        .eq(ReconstructionTask::getProjectId, projectId)
                        .orderByDesc(ReconstructionTask::getCreatedAt));
    }
}
