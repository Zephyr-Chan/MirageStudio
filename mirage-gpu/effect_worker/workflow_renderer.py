"""ComfyUI 工作流模板渲染器。

使用 Jinja2 将 ComfyUI API Format 的工作流模板 JSON 渲染为
可提交到 ComfyUI /prompt 接口的最终工作流。

支持的占位符：
  {{prompt}}         : 正向提示词文本
  {{seed}}           : 随机种子（整数）
  {{input_image}}    : 输入图像文件名（已上传到 ComfyUI input 目录）
  {{cn_strength}}    : ControlNet 强度（浮点数）
  {{negative_prompt}}: 反向提示词（可选）
  {{steps}}          : 采样步数（可选）
  {{cfg}}            : CFG scale（可选）

模板文件为标准 ComfyUI API Format JSON（从 ComfyUI 界面 "Save(API Format)" 导出），
其中需要动态填充的字段值用 Jinja2 占位符标记。
"""

import json
import logging
from pathlib import Path
from typing import Any, Dict, Optional

from jinja2 import Environment, FileSystemLoader, StrictUndefined

from common.config import Config

logger = logging.getLogger(__name__)

# 默认模板目录
_DEFAULT_TEMPLATE_DIR = Path(__file__).parent / "templates"


class WorkflowRenderer:
    """ComfyUI 工作流模板渲染器。"""

    def __init__(
        self,
        template_dir: Optional[Path] = None,
        config: Optional[Config] = None,
    ) -> None:
        self.config = config or Config.from_env()
        self.template_dir = template_dir or _DEFAULT_TEMPLATE_DIR

        # Jinja2 环境：从 JSON 文件加载模板，按 JSON 文本处理（不自动转义）
        self.env = Environment(
            loader=FileSystemLoader(str(self.template_dir)),
            undefined=StrictUndefined,  # 占位符未提供时报错，避免静默错误
            autoescape=False,           # JSON 不需要 HTML 转义
            keep_trailing_newline=True,
        )

    # ------------------------------------------------------------------ 渲染
    def render(
        self,
        template_name: str,
        prompt: str,
        seed: int,
        input_image: Optional[str] = None,
        cn_strength: Optional[float] = None,
        negative_prompt: str = "",
        steps: int = 20,
        cfg: float = 7.0,
        **extra_vars: Any,
    ) -> Dict[str, Any]:
        """渲染工作流模板，返回 ComfyUI API Format dict。

        Parameters
        ----------
        template_name : str
            模板文件名（如 ``cyberpunk_style.json``）。
        prompt : str
            正向提示词。
        seed : int
            随机种子。
        input_image : str, optional
            输入图像文件名（已上传到 ComfyUI）。
        cn_strength : float, optional
            ControlNet 强度，未指定时使用配置默认值。
        negative_prompt : str
            反向提示词。
        steps : int
            采样步数。
        cfg : float
            CFG scale。
        **extra_vars
            额外的 Jinja2 变量，会合并到模板上下文中。

        Returns
        -------
        dict
            渲染后的 ComfyUI API Format 工作流。
        """
        if cn_strength is None:
            cn_strength = self.config.default_cn_strength

        # 构建模板上下文
        context = {
            "prompt": prompt,
            "seed": seed,
            "input_image": input_image or "",
            "cn_strength": cn_strength,
            "negative_prompt": negative_prompt,
            "steps": steps,
            "cfg": cfg,
        }
        context.update(extra_vars)

        logger.info(
            "渲染工作流模板: %s prompt=%s... seed=%d cn_strength=%.2f",
            template_name,
            prompt[:30],
            seed,
            cn_strength,
        )

        # 加载并渲染模板
        template = self.env.get_template(template_name)
        rendered_str = template.render(**context)

        # 解析为 dict
        try:
            workflow = json.loads(rendered_str)
        except json.JSONDecodeError as e:
            logger.error("渲染后的工作流 JSON 解析失败: %s", e)
            logger.debug("渲染结果: %s", rendered_str[:500])
            raise

        logger.info("工作流渲染成功: %s 节点数=%d", template_name, len(workflow))
        return workflow

    # ------------------------------------------------------------------ 列出模板
    def list_templates(self) -> list:
        """列出所有可用的工作流模板文件名。"""
        templates = []
        for p in self.template_dir.glob("*.json"):
            templates.append(p.name)
        templates.sort()
        return templates

    # ------------------------------------------------------------------ 从字符串渲染
    def render_string(
        self,
        template_str: str,
        prompt: str,
        seed: int,
        input_image: Optional[str] = None,
        cn_strength: Optional[float] = None,
        **extra_vars: Any,
    ) -> Dict[str, Any]:
        """从模板字符串渲染（适用于动态模板，非文件）。

        Parameters
        ----------
        template_str : str
            Jinja2 模板字符串（ComfyUI API Format JSON 含占位符）。
        """
        if cn_strength is None:
            cn_strength = self.config.default_cn_strength

        context = {
            "prompt": prompt,
            "seed": seed,
            "input_image": input_image or "",
            "cn_strength": cn_strength,
        }
        context.update(extra_vars)

        template = self.env.from_string(template_str)
        rendered_str = template.render(**context)
        return json.loads(rendered_str)
