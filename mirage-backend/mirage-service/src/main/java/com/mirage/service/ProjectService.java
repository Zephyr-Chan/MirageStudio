package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.Project;
import com.mirage.dao.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目服务: CRUD
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final SnowflakeId snowflakeId;

    @Transactional(rollbackFor = Exception.class)
    public Project create(Long userId, String name, String description, Long coverAssetId) {
        Project project = new Project();
        project.setId(snowflakeId.nextId());
        project.setUserId(userId);
        project.setName(name);
        project.setDescription(description);
        project.setCoverAssetId(coverAssetId);
        project.setStatus("ACTIVE");
        projectMapper.insert(project);
        log.info("项目创建成功: projectId={}, userId={}", project.getId(), userId);
        return project;
    }

    @Transactional(rollbackFor = Exception.class)
    public Project update(Long projectId, Long userId, String name, String description, Long coverAssetId) {
        Project project = getOwnedProject(projectId, userId);
        if (name != null) {
            project.setName(name);
        }
        if (description != null) {
            project.setDescription(description);
        }
        if (coverAssetId != null) {
            project.setCoverAssetId(coverAssetId);
        }
        projectMapper.updateById(project);
        return project;
    }

    public Project getOwnedProject(Long projectId, Long userId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(BizExceptionEnum.PROJECT_NOT_FOUND);
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(BizExceptionEnum.FORBIDDEN);
        }
        return project;
    }

    public Project getById(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(BizExceptionEnum.PROJECT_NOT_FOUND);
        }
        return project;
    }

    public Page<Project> pageByUser(Long userId, int current, int size) {
        return projectMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getUserId, userId)
                        .orderByDesc(Project::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long projectId, Long userId) {
        Project project = getOwnedProject(projectId, userId);
        project.setStatus("ARCHIVED");
        projectMapper.updateById(project);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long projectId, Long userId) {
        Project project = getOwnedProject(projectId, userId);
        project.setStatus("DELETED");
        projectMapper.updateById(project);
        // 逻辑删除
        projectMapper.deleteById(projectId);
    }
}
