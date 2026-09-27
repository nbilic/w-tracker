// Waits for the GitHub Actions build of a tag and reports the published release.
// Usage: node scripts/wait-release.js v0.5.0   (exit code 0 = released with an APK)
const tag = process.argv[2];
if (!tag) { console.error('usage: node scripts/wait-release.js <tag>'); process.exit(2); }
const API = 'https://api.github.com/repos/nbilic/w-tracker';
const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  const deadline = Date.now() + 20 * 60 * 1000;
  let run;
  while (Date.now() < deadline) {
    try {
      const res = await (await fetch(`${API}/actions/runs?event=push&per_page=10`)).json();
      run = (res.workflow_runs || []).find(r => r.head_branch === tag);
      if (run && run.status === 'completed') break;
    } catch (e) { /* network blip: keep waiting */ }
    await sleep(20000);
  }
  if (!run || run.status !== 'completed') { console.log(`${tag}: timed out waiting for the build`); process.exit(1); }
  if (run.conclusion !== 'success') { console.log(`${tag}: build ${run.conclusion} — ${run.html_url}`); process.exit(1); }
  const rel = await (await fetch(`${API}/releases/tags/${tag}`)).json();
  const apk = (rel.assets || []).find(a => a.name.endsWith('.apk'));
  if (!apk) { console.log(`${tag}: build passed but the release has no APK — ${run.html_url}`); process.exit(1); }
  console.log(`${tag}: released ${apk.name} (${(apk.size / 1048576).toFixed(1)} MB) — ${rel.html_url}`);
})();
