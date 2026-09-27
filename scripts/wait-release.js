// Waits until a tag's release APK is published, then reports it.
// Usage: node scripts/wait-release.js v0.5.0   (exit code 0 = released with an APK)
//
// Polls the release download URL rather than the GitHub API: unauthenticated API calls are limited to
// 60 an hour per IP (shared with the phone's update check), and polling would burn through that.
// The API is only asked once, at the end, to explain a missing release.
const tag = process.argv[2];
if (!tag) { console.error('usage: node scripts/wait-release.js <tag>'); process.exit(2); }
const REPO = 'nbilic/w-tracker';
const APK_URL = `https://github.com/${REPO}/releases/download/${tag}/workout-tracker-${tag}.apk`;
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function apkPublished() {
  try {
    const res = await fetch(APK_URL, { method: 'HEAD', redirect: 'manual' });
    return res.status === 302 || res.status === 200;
  } catch (e) { return false; } // network blip: try again
}

async function explain() {
  try {
    const res = await fetch(`https://api.github.com/repos/${REPO}/actions/runs?event=push&per_page=10`);
    const body = await res.json();
    if (!body.workflow_runs) return `GitHub API unavailable (${body.message || res.status})`;
    const run = body.workflow_runs.find(r => r.head_branch === tag);
    if (!run) return 'no CI run found for this tag';
    return `CI run is ${run.status}${run.conclusion ? ' / ' + run.conclusion : ''} — ${run.html_url}`;
  } catch (e) { return 'could not reach GitHub: ' + e.message; }
}

(async () => {
  const deadline = Date.now() + 25 * 60 * 1000;
  while (Date.now() < deadline) {
    if (await apkPublished()) {
      console.log(`${tag}: released — https://github.com/${REPO}/releases/tag/${tag}`);
      return;
    }
    await sleep(30000);
  }
  console.log(`${tag}: no APK after 25 minutes; ${await explain()}`);
  process.exit(1);
})();
