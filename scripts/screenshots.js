// Takes the README screenshots (docs/screenshots) with realistic sample data, in phone emulation.
// Usage: npm run screenshots   (uses your installed Chrome; set CHROME_PATH if it's elsewhere)
const puppeteer = require('puppeteer-core');
const path = require('path');
const fs = require('fs');

const ROOT = path.join(__dirname, '..');
const APP = 'file:///' + path.join(ROOT, 'index.html').split(path.sep).join('/');
const OUT = path.join(ROOT, 'docs', 'screenshots');
fs.mkdirSync(OUT, { recursive: true });

// ---- Sample data: ~5 weeks of Workout A / B, 3x a week, with steady progression ----
const A = [ // name, id, equipment, start kg, step, reps, sets
  ['Squat (Barbell)', 'squat-bb', 'barbell', 80, 2.5, 8, 4],
  ['Bench Press (Barbell)', 'bench-press-bb', 'barbell', 60, 2.5, 10, 3],
  ['Overhead Press (Barbell)', 'ohp-bb', 'barbell', 40, 2.5, 8, 4],
  ['Incline Bench (Barbell)', 'incline-bench-bb', 'barbell', 50, 2.5, 10, 3],
  ['Lat Pulldown', 'lat-pulldown', 'cable', 55, 1, 10, 3],
  ['Bicep Curl (Dumbbell)', 'bicep-curl-db', 'dumbbell', 12, 1, 10, 3],
  ['Leg Press', 'leg-press', 'machine', 140, 1, 10, 3],
];
const B = [
  ['Deadlift', 'deadlift', 'barbell', 110, 2.5, 8, 4],
  ['Barbell Row', 'barbell-row', 'barbell', 60, 2.5, 10, 3],
  ['Lat Pulldown', 'lat-pulldown', 'cable', 55, 1, 10, 3],
  ['Machine Row', 'machine-row', 'machine', 50, 1, 10, 3],
  ['Bicep Curl (Dumbbell)', 'bicep-curl-db', 'dumbbell', 12, 1, 10, 3],
  ['Leg Extension', 'leg-extension', 'machine', 45, 1, 10, 3],
];
function buildSessions() {
  const sessions = [];
  const today = new Date('2026-09-27T12:00:00');
  const count = { A: 0, B: 0 };
  let id = 1;
  for (let d = 36; d >= 1; d--) {
    const date = new Date(today.getTime() - d * 86400000);
    const dow = date.getDay(); // Mon, Wed, Fri
    if (![1, 3, 5].includes(dow)) continue;
    const kind = sessions.length % 2 === 0 ? 'A' : 'B';
    const plan = kind === 'A' ? A : B;
    const n = count[kind]++;
    const exercises = plan.map(([name, exerciseId, equipment, w0, step, reps, sets], k) => {
      const weight = +(w0 + step * Math.floor(n / 2)).toFixed(1);
      const hit = n % 2 === 1; // every other session you hit the target, so the weight moves every 2
      return { name, exerciseId, equipment, targetSets: sets, targetReps: String(reps), step,
        sets: Array.from({ length: sets }, (_, i) => ({ weight, reps: hit ? reps : Math.max(reps - 2, reps - i), completed: true })) };
    });
    const totalVolume = exercises.reduce((t, e) => t + e.sets.reduce((s, x) => s + x.weight * x.reps, 0), 0);
    sessions.push({ id: id++, date: date.toISOString().slice(0, 10), routineId: kind === 'A' ? 'default-a' : 'default-b',
      routineName: kind === 'A' ? 'Workout A' : 'Workout B', type: kind, exercises,
      totalDuration: 55 * 60 + (n % 3) * 240, totalVolume, notes: n === 3 && kind === 'A' ? 'Squat felt fast today.' : '' });
  }
  return sessions;
}
const DATA = {
  sessions: buildSessions(),
  settings: { defaultRestTime: 90, unit: 'kg', hapticsEnabled: true, lastBackupAt: Date.parse('2026-09-20') },
  personalRecords: {},
  routines: [['default-a', 'Workout A', A], ['default-b', 'Workout B', B]].map(([id, name, plan], i) => ({
    id, name, colorIndex: i, exercises: plan.map(([n, exerciseId, equipment, , , reps, sets]) => ({ exerciseId, name: n, equipment, sets, reps: String(reps) })),
  })),
  programs: [{ id: 'prog-fullbody', name: 'Full Body', routineIds: ['default-a', 'default-b'] }],
};
DATA.settings.activeProgramId = 'prog-fullbody';

const shots = [
  ['today', async p => { await p.evaluate(() => showScreen('home')); }],
  ['workout', async p => {
    await p.evaluate(async () => {
      await startWorkoutById(getNextRoutineId());
      currentSession.startedAt = Date.now() - 14 * 60000;
      toggleSet(0, 0);
      renderWorkout();
      restStartedAt = Date.now() - 38000; // show the rest timer part-way through
      if (currentSession) currentSession.restStartedAt = restStartedAt;
      renderWorkout();
    });
  }],
  ['history', async p => {
    await p.evaluate(() => {
      showScreen('history');
      const first = document.querySelector('#history-screen .hist-head'); if (first) first.click();
    });
  }],
  ['progress', async p => { await p.evaluate(() => showScreen('progress')); }],
  ['bestiary', async p => { await p.evaluate(() => { showScreen('exercises'); openExerciseEditModal('bench-press-bb'); }); }],
  ['guide', async p => { await p.evaluate(() => openGuide()); }],
];

(async () => {
  const browser = await puppeteer.launch({
    executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    headless: true, args: ['--disable-gpu'],
  });
  for (const [name, act] of shots) {
    const page = await browser.newPage();
    await page.emulate({
      viewport: { width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
      userAgent: 'Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36',
    });
    // Frozen clock so dates in the shots are stable; seed data before the app script runs.
    await page.evaluateOnNewDocument((data) => {
      const fixed = Date.parse('2026-09-27T18:30:00');
      const RealDate = Date; const offset = fixed - RealDate.now();
      // eslint-disable-next-line no-global-assign
      Date = class extends RealDate { constructor(...a) { super(...(a.length ? a : [RealDate.now() + offset])); } static now() { return RealDate.now() + offset; } };
      localStorage.clear();
      localStorage.setItem('w-onboarded', '1');
      localStorage.setItem('workoutData', JSON.stringify(data));
    }, DATA);
    await page.goto(APP, { waitUntil: 'load' });
    await page.evaluate(() => { rebuildAllPRs(); saveData(appData); });
    await page.addStyleTag({ content: '*, *::before, *::after { animation: none !important; transition: none !important; }' });
    await act(page);
    await new Promise(r => setTimeout(r, 400));
    await page.screenshot({ path: path.join(OUT, name + '.png') });
    console.log('shot', name);
    await page.close();
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
