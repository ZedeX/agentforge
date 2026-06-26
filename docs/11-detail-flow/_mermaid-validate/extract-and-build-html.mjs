// Extract mermaid code blocks from 3 flow docs and build a validation HTML page.
// Pure ASCII JS to avoid GBK issues. Comments in English.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const docsDir = join(__dirname, '..');

const sources = [
  { file: '01-access-and-planning-flow.md', prefix: 'F1-F4', expected: 4 },
  { file: '02-runtime-and-replan-flow.md', prefix: 'F5-F8', expected: 4 },
  { file: '03-quality-and-memory-flow.md', prefix: 'F9-F12', expected: 4 },
];

// Match ```mermaid ... ``` blocks (non-greedy, dotall)
const MERMAID_RE = /```mermaid\n([\s\S]*?)```/g;

const graphs = [];
let graphId = 1;

for (const src of sources) {
  const fullPath = join(docsDir, src.file);
  const content = readFileSync(fullPath, 'utf8');
  let match;
  while ((match = MERMAID_RE.exec(content)) !== null) {
    const code = match[1].trim();
    // Skip the "global style definition" block in doc 03 section 0.3
    // It only contains classDef lines and no nodes/edges
    const hasEdges = /-->/.test(code) || /-\.->/.test(code);
    if (!hasEdges) {
      continue;
    }
    // Extract the first heading-like comment or nearest preceding ## heading
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

// Build HTML validation page.
// Uses mermaid.js from jsdelivr CDN (accessible in CN).
// Each graph rendered in its own container with id, errors captured via window.onerror + mermaid callback.
const htmlParts = [];
htmlParts.push(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Mermaid Syntax Validation - 12 Decision Flowcharts</title>
<style>
  body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; margin: 20px; background: #fafafa; }
  h1 { color: #333; }
  .graph-card { background: #fff; border: 1px solid #ddd; border-radius: 6px; padding: 16px; margin: 16px 0; }
  .graph-card h2 { margin-top: 0; font-size: 16px; color: #007bff; }
  .graph-card .meta { color: #666; font-size: 12px; margin-bottom: 8px; }
  .graph-card .status { font-weight: bold; padding: 4px 8px; border-radius: 4px; display: inline-block; margin-bottom: 8px; }
  .status-pending { background: #fff3cd; color: #856404; }
  .status-ok { background: #d4edda; color: #155724; }
  .status-error { background: #f8d7da; color: #721c24; }
  .error-msg { background: #f8d7da; border-left: 4px solid #dc3545; padding: 8px 12px; margin-top: 8px; font-family: monospace; font-size: 12px; white-space: pre-wrap; color: #721c24; }
  .mermaid { text-align: center; }
  pre.code { background: #f5f5f5; padding: 8px; border-radius: 4px; font-size: 11px; overflow-x: auto; max-height: 200px; overflow-y: auto; }
  #summary { position: sticky; top: 0; background: #fff; padding: 12px; border-bottom: 2px solid #007bff; z-index: 100; margin: -20px -20px 20px -20px; }
</style>
</head>
<body>
<div id="summary">
  <h1>Mermaid Syntax Validation</h1>
  <div id="summary-text">Validating 12 graphs...</div>
</div>
<div id="results"></div>

<script src="https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js"></script>
<script>
  const graphs = ${JSON.stringify(graphs, null, 2)};
  const results = [];
  let pending = graphs.length;

  mermaid.initialize({ startOnLoad: false, theme: 'default', securityLevel: 'loose', flowchart: { useMaxWidth: true } });

  function updateSummary() {
    const ok = results.filter(r => r.ok).length;
    const err = results.filter(r => !r.ok).length;
    document.getElementById('summary-text').innerHTML =
      'Total: ' + graphs.length + ' | <span style="color:#155724">OK: ' + ok + '</span> | <span style="color:#721c24">Error: ' + err + '</span>';
  }

  async function validateOne(g) {
    const card = document.createElement('div');
    card.className = 'graph-card';
    card.innerHTML = '<h2>#' + g.id + ' ' + g.title + '</h2>' +
      '<div class="meta">Source: ' + g.source + ' | Lines: ' + g.lines + '</div>' +
      '<div class="status status-pending" id="status-' + g.id + '">PENDING</div>' +
      '<div class="mermaid" id="mermaid-' + g.id + '"></div>' +
      '<pre class="code">' + g.code.replace(/</g, '&lt;').replace(/>/g, '&gt;') + '</pre>';
    document.getElementById('results').appendChild(card);

    try {
      // Use mermaid.parse to check syntax (throws on error)
      await mermaid.parse(g.code);
      // If parse passes, render it
      const { svg } = await mermaid.render('render-' + g.id, g.code);
      document.getElementById('mermaid-' + g.id).innerHTML = svg;
      document.getElementById('status-' + g.id).className = 'status status-ok';
      document.getElementById('status-' + g.id).textContent = 'OK';
      results.push({ id: g.id, ok: true });
    } catch (e) {
      document.getElementById('status-' + g.id).className = 'status status-error';
      document.getElementById('status-' + g.id).textContent = 'ERROR';
      const errDiv = document.createElement('div');
      errDiv.className = 'error-msg';
      errDiv.textContent = String(e && e.message ? e.message : e);
      card.appendChild(errDiv);
      results.push({ id: g.id, ok: false, error: String(e && e.message ? e.message : e) });
    }
    pending--;
    updateSummary();
    if (pending === 0) {
      console.log('VALIDATION_COMPLETE', JSON.stringify(results));
      window.__validationDone = true;
    }
  }

  // Run sequentially to avoid mermaid render id collision
  (async () => {
    for (const g of graphs) {
      await validateOne(g);
    }
  })();
</script>
</body>
</html>
`);

writeFileSync(join(__dirname, 'validate.html'), htmlParts.join(''));
console.log('Wrote validate.html');
