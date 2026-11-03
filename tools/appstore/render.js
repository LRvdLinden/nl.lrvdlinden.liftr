// Renders tools/appstore/_build/jobs.json to PNG (exact pixel sizes).
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const jobs = JSON.parse(fs.readFileSync(path.join(__dirname, '_build/jobs.json')));
  const only = process.argv[2];
  const b = await chromium.launch();
  const pages = {};
  for (const j of jobs) {
    if (only && !j.out.includes(only)) continue;
    const key = `${j.width}x${j.height}@${j.dsf}`;
    if (!pages[key]) pages[key] = await (await b.newContext({ viewport: { width: j.width, height: j.height }, deviceScaleFactor: j.dsf })).newPage();
    const p = pages[key];
    await p.goto('file://' + j.html, { waitUntil: 'load' });
    await p.evaluate(() => document.fonts.ready);
    await p.waitForFunction(() => document.body.dataset.fit === '1', null, { timeout: 5000 }).catch(() => {});
    fs.mkdirSync(path.dirname(j.out), { recursive: true });
    await p.screenshot({ path: j.out });
  }
  await b.close();
  console.log('rendered');
})();
