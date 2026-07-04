package com.mirage.integration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ComfyUI 工作流渲染器: Jinja2 风格占位符替换
 *
 * <p>支持占位符语法: {{prompt}}, {{seed}}, {{input_image}}, {{cn_strength}} 等。
 * 将模板 JSON 中的占位符替换为实际参数值, 生成可提交给 ComfyUI /prompt 的 API 格式 JSON。</p>
 */
public class ComfyWorkflowRenderer {

    /** 匹配 {{ key }} 形式的占位符 (允许前后空格) */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * 渲染工作流模板
     *
     * @param templateJson 含占位符的工作流模板 JSON 字符串
     * @param params       参数键值对 (key 不含花括号)
     * @return 渲染后的工作流 JSON 字符串
     */
    public String render(String templateJson, java.util.Map<String, Object> params) {
        if (templateJson == null || templateJson.isEmpty()) {
            return templateJson;
        }
        if (params == null || params.isEmpty()) {
            return templateJson;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateJson);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params.get(key);
            String replacement = toComfyValue(value);
            // 转义 $ 与 \ 避免 replaceAll 解析
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 将参数值转换为 ComfyUI 接受的格式:
     * - 字符串: 保持原样 (若模板中占位符带引号则作为字符串值, 否则原样输出)
     * - 数字/布尔: 原样输出
     * - null: 输出空字符串
     *
     * <p>注意: ComfyUI API JSON 中字符串值需要带引号。本渲染器采用 "原样替换" 策略,
     * 调用方应在模板中为字符串参数保留引号, 例如 "seed": "{{seed}}" 中 seed 传入数字,
     * 而 "prompt": "{{prompt}}" 中 prompt 传入字符串内容 (引号由模板提供)。</p>
     */
    private String toComfyValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    /**
     * 渲染并解析为 JSON 对象 (用于校验渲染结果是否合法)
     *
     * @param templateJson 模板
     * @param params       参数
     * @return 渲染后的 JSON 字符串
     * @throws IllegalArgumentException 渲染结果非合法 JSON 时抛出
     */
    public String renderAndValidate(String templateJson, java.util.Map<String, Object> params) {
        String rendered = render(templateJson, params);
        try {
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(rendered);
        } catch (Exception e) {
            throw new IllegalArgumentException("工作流渲染结果非合法 JSON: " + e.getMessage(), e);
        }
        return rendered;
    }
}
