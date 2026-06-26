# Mermaid 渲染校验报告

> 校验时间：2026-06-26 18:42:10 (Asia/Shanghai)
> 校验工具：jsdom + mermaid@10.9.1 (Node.js)
> 校验对象：3 个流程图文档中的 12 张决策流程图（F1-F12）

## 摘要

- 总数：12
- 通过：12
- 失败：0

## 全部结果明细

| # | 标题 | 源文件 | 行数 | 状态 | 说明 |
|---|---|---|---|---|---|
| 1 | F1 接入网关请求处理流程 | 01-access-and-planning-flow.md | 66 | OK | parse OK (语法通过) |
| 2 | F2 意图识别与复杂度判定决策树 | 01-access-and-planning-flow.md | 63 | OK | parse OK (语法通过) |
| 3 | F3 任务规划与 DAG 生成流程 | 01-access-and-planning-flow.md | 83 | OK | parse OK (语法通过) |
| 4 | F4 子任务分发与并行调度流程 | 01-access-and-planning-flow.md | 76 | OK | parse OK (语法通过) |
| 5 | F5 动态重规划决策流程 | 02-runtime-and-replan-flow.md | 67 | OK | parse OK (语法通过) |
| 6 | F6 ReAct 循环详细决策流程 | 02-runtime-and-replan-flow.md | 78 | OK | parse OK (语法通过) |
| 7 | F7 Token 水位压缩决策流程 | 02-runtime-and-replan-flow.md | 57 | OK | parse OK (语法通过) |
| 8 | F8 工具选择与调用决策流程 | 02-runtime-and-replan-flow.md | 100 | OK | parse OK (语法通过) |
| 9 | F9 三级质量校验决策流程 | 03-quality-and-memory-flow.md | 54 | OK | parse OK (语法通过) |
| 10 | F10 幻觉治理六层联动流程 | 03-quality-and-memory-flow.md | 92 | OK | parse OK (语法通过) |
| 11 | F11 漂移监测与纠偏决策流程 | 03-quality-and-memory-flow.md | 84 | OK | parse OK (语法通过) |
| 12 | F12 长期记忆写入与召回决策流程 | 03-quality-and-memory-flow.md | 102 | OK | parse OK (语法通过) |

## 校验方法说明

1. 用正则 \`\`\`mermaid ... \`\`\` 从 3 个文档提取 12 个 mermaid 代码块（过滤掉仅含 classDef 的全局样式块）。
2. 用 jsdom 创建虚拟 DOM，挂载 mermaid 所需的 window/document/navigator 等全局对象。
3. 调用 `mermaid.parse(code)` 做纯语法检查（失败抛异常，能捕获语法/节点声明/边定义等问题）。
4. **不调用 `mermaid.render()`**：render 需要 SVG `getBBox` 文本测量 API，jsdom 不实现该 API，会抛出 `text.getBBox is not a function` 工具限制错误（非真实语法问题）。parse 已是 mermaid 官方推荐的语法校验入口，足够覆盖本阶段校验目标。
5. 输出 Markdown 报告 `validate-report.md`，列出每张图的状态与失败原因。
