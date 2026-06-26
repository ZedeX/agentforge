// Validate 12 mermaid flowcharts using jsdom + mermaid npm package.
// Pure ASCII JS to avoid GBK issues. Comments in English.
//
// Usage: node validate-jsdom.mjs
//
// Strategy:
// 1. Extract 12 mermaid code blocks from 3 markdown files (reuse logic from extract-and-build-html.mjs).
// 2. Create a jsdom virtual DOM so mermaid can attach its render target.
// 3. Initialize mermaid with startOnLoad=false, securityLevel='loose'.
// 4. For each graph: call mermaid.parse(code) for syntax check (throws on error).
//    On parse success: call mermaid.render(id, code) to ensure full render works.
// 5. Output results to console and to validate-report.md.

import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { JSDOM } from 'jsdom';

const __dirname = dirname(fileURLToPath(import.meta.url));
const docsDir = join(__dirname, '..');

// ---------- 1. Extract mermaid blocks ----------
const sources = [
  { file: '01-access-and-planning-flow.md', prefix: 'F1-F4', expected: 4 },
  { file: '02-runtime-and-replan-flow.md', prefix: 'F5-F8', expected: 4 },
  { file: '03-quality-and-memory-flow.md', prefix: 'F9-F12', expected: 4 },
];

const MERMAID_RE = /```mermaid\n([\s\S]*?)```/g;

const graphs = [];
let graphId = 1;

for (const src of sources) {
  const fullPath = join(docsDir, src.file);
  const content = readFileSync(fullPath, 'utf8');
  let match;
  while ((match = MERMAID_RE.exec(content)) !== null) {
    const code = match[1].trim();
    // Skip the "global style definition" block in doc 03 section 0.3 (only classDef, no edges)
    const hasEdges = /-->/.test(code) || /-\.->/.test(code);
    if (!hasEdges) continue;
    // Find the nearest preceding "## N. Fxx ..." heading as the title
    const beforeBlock = content.substring(0, match.index);
    const headingMatch = beforeBlock.match(/##\s+(\d+)\.\s+(F\d+[^\n]*)\n/g);
    let title = `Graph ${graphId}`;
    if (headingMatch && headingMatch.length > 0) {
      title = headingMatch[headingMatch.length - 1].replace(/^##\s+\d+\.\s+/, '').trim();
    }
    graphs.push({ id: graphId, title, code, source: src.file, lines: code.split('\n').length });
    graphId++;
  }
}

console.log(`Extracted ${graphs.length} mermaid graphs (expected 12).`);
for (const g of graphs) {
  console.log(`  #${g.id} [${g.source}] ${g.title} (${g.lines} lines)`);
}
if (graphs.length !== 12) {
  console.error('ERROR: expected 12 graphs, got ' + graphs.length);
  process.exit(1);
}

// ---------- 2. Setup jsdom ----------
const dom = new JSDOM(`<!DOCTYPE html><html><head></head><body><div id="root"></div></body></html>`, {
  url: 'http://localhost/',
  pretendToBeVisual: true,
  resources: 'usable',
});

const { window } = dom;
// Attach globals that mermaid expects.
// Node.js 21+ exposes a read-only global `navigator`, so we must use defineProperty.
function setGlobal(name, value) {
  try {
    Object.defineProperty(global, name, { value, writable: true, configurable: true });
  } catch {
    // Fallback: if defineProperty fails, the global already exists; leave it.
  }
}
setGlobal('window', window);
setGlobal('document', window.document);
setGlobal('navigator', window.navigator || globalThis.navigator || { userAgent: 'node.js' });
setGlobal('DOMParser', window.DOMParser);
setGlobal('XMLSerializer', window.XMLSerializer);
setGlobal('SVGElement', window.SVGElement);
setGlobal('btoa', (s) => Buffer.from(s, 'binary').toString('base64'));
setGlobal('atob', (s) => Buffer.from(s, 'base64').toString('binary'));

// ---------- 3. Import mermaid ----------
const mermaid = await import('mermaid');

// ---------- 4. Validate each graph ----------
// NOTE: mermaid.render() requires SVG text metrics (getBBox) which jsdom does not implement.
// Therefore we use mermaid.parse() only — parse is the authoritative syntax check.
// (Render-only issues like visual collisions are out of scope for syntax validation.)
async function validateOne(g) {
  try {
    // mermaid.parse: pure syntax check, throws on parse error.
    // Returns the parsed AST on success (we don't need it).
    await mermaid.default.parse(g.code);
    return { ok: true, error: null };
  } catch (parseErr) {
    // Try to extract a friendly line/column + snippet.
    const msg = String(parseErr && parseErr.message ? parseErr.message : parseErr);
    return { ok: false, stage: 'parse', error: msg };
  }
}

console.log('\nValidating 12 graphs...\n');
const results = [];
for (const g of graphs) {
  const r = await validateOne(g);
  results.push({ ...g, ...r });
  const status = r.ok ? 'OK' : 'FAIL(' + (r.stage || '?') + ')';
  console.log(`  #${g.id} ${status.padEnd(12)} ${g.title}`);
  if (!r.ok) {
    // Print first 500 chars of error message
    const err = r.error || '';
    console.log('       ' + err.substring(0, 500).replace(/\n/g, '\n       '));
  }
}

// ---------- 5. Write report ----------
const okCount = results.filter(r => r.ok).length;
const failCount = results.length - okCount;
const ts = new Date().toISOString().replace('T', ' ').substring(0, 19);

let md = `# Mermaid 渲染校验报告\n\n`;
md += `> 校验时间：${ts} (Asia/Shanghai)\n`;
md += `> 校验工具：jsdom + mermaid@10.9.1 (Node.js)\n`;
md += `> 校验对象：3 个流程图文档中的 12 张决策流程图（F1-F12）\n\n`;
md += `## 摘要\n\n- 总数：${results.length}\n- 通过：${okCount}\n- 失败：${failCount}\n\n`;

if (failCount > 0) {
  md += `## 失败列表\n\n`;
  for (const r of results.filter(r => !r.ok)) {
    md += `### #${r.id} ${r.title} — ${r.stage} 阶段失败\n\n`;
    md += `- 源文件：\`${r.source}\`\n`;
    md += `- 行数：${r.lines}\n`;
    md += `- 错误：\n\n\`\`\`\n${r.error}\n\`\`\`\n\n`;
  }
}

md += `## 全部结果明细\n\n`;
md += `| # | 标题 | 源文件 | 行数 | 状态 | 说明 |\n`;
md += `|---|---|---|---|---|---|\n`;
for (const r of results) {
  const status = r.ok ? 'OK' : 'FAIL(' + (r.stage || '?') + ')';
  const note = r.ok ? 'parse OK (语法通过)' : r.error.substring(0, 120).replace(/\|/g, '\\|').replace(/\n/g, ' ');
  md += `| ${r.id} | ${r.title.replace(/\|/g, '\\|')} | ${r.source} | ${r.lines} | ${status} | ${note} |\n`;
}

md += `\n## 校验方法说明\n\n`;
md += `1. 用正则 \\\`\\\`\\\`mermaid ... \\\`\\\`\\\` 从 3 个文档提取 12 个 mermaid 代码块（过滤掉仅含 classDef 的全局样式块）。\n`;
md += `2. 用 jsdom 创建虚拟 DOM，挂载 mermaid 所需的 window/document/navigator 等全局对象。\n`;
md += `3. 调用 \`mermaid.parse(code)\` 做纯语法检查（失败抛异常，能捕获语法/节点声明/边定义等问题）。\n`;
md += `4. **不调用 \`mermaid.render()\`**：render 需要 SVG \`getBBox\` 文本测量 API，jsdom 不实现该 API，会抛出 \`text.getBBox is not a function\` 工具限制错误（非真实语法问题）。parse 已是 mermaid 官方推荐的语法校验入口，足够覆盖本阶段校验目标。\n`;
md += `5. 输出 Markdown 报告 \`validate-report.md\`，列出每张图的状态与失败原因。\n`;

writeFileSync(join(__dirname, 'validate-report.md'), md);
console.log(`\nReport written to validate-report.md`);
console.log(`Summary: ${okCount} OK / ${failCount} FAIL`);

// Exit with code 1 if any failure (useful for CI / future pre-commit)
if (failCount > 0) {
  process.exit(1);
}
