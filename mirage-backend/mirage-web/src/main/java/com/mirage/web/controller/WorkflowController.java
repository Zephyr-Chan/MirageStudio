package com.mirage.web.controller;

import com.mirage.common.R;
import com.mirage.dao.entity.WorkflowTemplate;
import com.mirage.service.WorkflowTemplateService;
import com.mirage.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流模板控制器: 查询模板
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowTemplateService workflowTemplateService;

    /**
     * 查询启用模板列表
     *
     * @param category 类别: STYLE/INPAINT/COLOR/VIDEO (可选)
     */
    @GetMapping
    public R<List<WorkflowTemplate>> list(@RequestParam(required = false) String category) {
        UserContext.requireUserId();
        return R.ok(workflowTemplateService.listEnabled(category));
    }

    @GetMapping("/{id}")
    public R<WorkflowTemplate> get(@PathVariable Long id) {
        UserContext.requireUserId();
        return R.ok(workflowTemplateService.getById(id));
    }
}
