// Validate 12 mermaid graphs by launching Chrome via puppeteer-core.
// Pure ASCII JS. English comments only.
import puppeteer from 'puppeteer-core';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { writeFileSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';

const __dirname = dirname(fileURLToPath(import.meta.url));
const htmlPath = join(__dirname, 'validate.html');
const htmlUrl = 'file:///' + htmlPath.replace(/\\/g, '/');

const CHROME_PATH = 'D:\\_program\\Chrome\\App\\chrome.exe';

const REPORT_PATH = join(__dirname, 'validation-report.json');
const LOG_PATH = join(__dirname, 'validation.log');

const log = (msg) => {
  console.log(msg);
};

async function main() {
  log('Launching Chrome at: ' + CHROME_PATH);
  const tmpUserData = join(tmpdir(), 'mermaid-validate-' + process.pid + '-' + Date.now());
  mkdirSync(tmpUserData, { recursive: true });
  const browser = await puppeteer.launch({
    executablePath: CHROME_PATH,
    headless: true,
    dumpio: true,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      '--user-data-dir=' + tmpUserData,
    ],
  });

  try {
    const page = await browser.newPage();
    const consoleLines = [];
    page.on('console', (msg) => {
      consoleLines.push('[' + msg.type() + '] ' + msg.text());
    });
    page.on('pageerror', (err) => {
      consoleLines.push('[pageerror] ' + err.message);
    });

    log('Opening: ' + htmlUrl);
    await page.goto(htmlUrl, { waitUntil: 'networkidle0', timeout: 60000 });

    // Wait for window.__validationDone === true (max 60s)
    log('Waiting for validation to complete...');
    await page.waitForFunction(() => window.__validationDone === true, { timeout: 60000 });

    // Extract results from the page
    const results = await page.evaluate(() => {
      const cards = document.querySelectorAll('.graph-card');
      const out = [];
      cards.forEach((card) => {
        const h2 = card.querySelector('h2');
        const status = card.querySelector('.status');
        const errDiv = card.querySelector('.error-msg');
        out.push({
          title: h2 ? h2.textContent : '',
          status: status ? status.textContent : '',
          error: errDiv ? errDiv.textContent : null,
        });
      });
      return out;
    });

    log('\n=== Validation Results ===');
    let okCount = 0, errCount = 0;
    for (const r of results) {
      const flag = r.status === 'OK' ? 'OK  ' : 'FAIL';
      if (r.status === 'OK') okCount++; else errCount++;
      log(flag + ' | ' + r.title);
      if (r.error) {
        log('     Error: ' + r.error.split('\n').slice(0, 3).join('\n     '));
      }
    }
    log('\nSummary: ' + okCount + ' OK, ' + errCount + ' FAIL, total ' + results.length);

    // Save full report
    writeFileSync(REPORT_PATH, JSON.stringify({ results, consoleLines }, null, 2));
    writeFileSync(LOG_PATH, consoleLines.join('\n'));
    log('Full report saved to: ' + REPORT_PATH);
    log('Console log saved to: ' + LOG_PATH);

    if (errCount > 0) {
      process.exitCode = 1;
    }
  } finally {
    await browser.close();
  }
}

main().catch((e) => {
  log('FATAL: ' + (e && e.message ? e.message : e));
  process.exit(2);
});
