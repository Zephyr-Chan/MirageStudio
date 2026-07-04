package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.dao.entity.WorkflowTemplate;
import com.mirage.dao.mapper.WorkflowTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流模板服务: 模板查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTemplateService {

    private final WorkflowTemplateMapper workflowTemplateMapper;

    /**
     * 根据 ID 查询模板
     */
    public WorkflowTemplate getById(Long id) {
        WorkflowTemplate template = workflowTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(BizExceptionEnum.RESOURCE_NOT_FOUND, "工作流模板不存在");
        }
        return template;
    }

    /**
     * 查询启用模板列表
     *
     * @param category 类别: STYLE/INPAINT/COLOR/VIDEO, 为空则查全部
     */
    public List<WorkflowTemplate> listEnabled(String category) {
        LambdaQueryWrapper<WorkflowTemplate> wrapper = new LambdaQueryWrapper<WorkflowTemplate>()
                .eq(WorkflowTemplate::getEnabled, 1)
                .orderByDesc(WorkflowTemplate::getCreatedAt);
        if (category != null && !category.isEmpty()) {
            wrapper.eq(WorkflowTemplate::getCategory, category);
        }
        return workflowTemplateMapper.selectList(wrapper);
    }
}
