// WHY: Centralizes ADB subprocess dispatch so the HTTP layer stays thin.
// NOTE: Uses execFile (not exec) to avoid shell interpretation of coordinate args.
// IMPORTANT: Serial pinning — if ADB_SERIAL env var is set, every command gets `-s <serial>`.
//            Tests running against multiple devices must set this before starting the driver.

const { execFile } = require('child_process');
const { promisify } = require('util');
const execFileAsync = promisify(execFile);

function adbArgs(extra) {
    const base = [];
    const serial = process.env.ADB_SERIAL;
    if (serial) {
        base.push('-s', serial);
    }
    return base.concat(extra);
}

async function run(args, timeoutMs = 5000) {
    try {
        const { stdout, stderr } = await execFileAsync('adb', adbArgs(args), {
            timeout: timeoutMs,
            maxBuffer: 4 * 1024 * 1024, // 4 MB for logcat dumps
        });
        return { ok: true, stdout, stderr };
    } catch (err) {
        return { ok: false, error: String(err.message), stderr: err.stderr || '' };
    }
}

async function tap(x, y) {
    return run(['shell', 'input', 'tap', String(x), String(y)]);
}

async function swipe(x1, y1, x2, y2, durationMs = 300) {
    return run([
        'shell', 'input', 'swipe',
        String(x1), String(y1), String(x2), String(y2), String(durationMs)
    ]);
}

async function broadcast(action, extras = {}) {
    // WHY: Phase 2.1 and 2.4 add ENABLE_DEBUG_SERVER and SET_LAYOUT_MODE broadcasts.
    //      The driver is the single issuer so tests don't shell out to adb directly.
    const args = ['shell', 'am', 'broadcast', '-a', action];
    for (const [k, v] of Object.entries(extras)) {
        if (typeof v === 'boolean') {
            args.push('--ez', k, String(v));
        } else if (typeof v === 'number' && Number.isInteger(v)) {
            args.push('--ei', k, String(v));
        } else {
            args.push('--es', k, String(v));
        }
    }
    return run(args);
}

async function dumpLogcat(tags) {
    // WHY: Dual-source strategy — driver scrapes legacy Log.d tags that don't flow through DevKeyLogger.
    //      Primary call site: 2.3 flows polling DevKeyPress/DevKeyMode/DevKeyMap/DevKeyBridge.
    // NOTE: We do `-d` (dump and exit), not streaming. Merging streaming logcat into the driver
    //       event queue is a Phase 4 follow-up; for Phase 2 the test runner drives the polling cadence.
    const args = ['logcat', '-d'];
    for (const tag of tags) {
        args.push('-s', `${tag}:*`);
    }
    return run(args);
}

async function clearLogcat() {
    return run(['logcat', '-c']);
}

async function screencap(outputPath) {
    // IMPORTANT: Git Bash path mangling workaround from .claude/skills/test/references/adb-commands.md.
    //            We write directly to the provided host path via exec-out, no /sdcard round-trip.
    // NOTE: `execFile` does NOT invoke a shell, so `>` redirection cannot be used here.
    //       Use `spawn` + `fs.createWriteStream` to pipe stdout directly to the output file.
    const { spawn } = require('child_process');
    const fs = require('fs');
    return new Promise((resolve) => {
        const proc = spawn('adb', adbArgs(['exec-out', 'screencap', '-p']));
        const out = fs.createWriteStream(outputPath);
        proc.stdout.pipe(out);
        proc.on('close', (code) => resolve({ ok: code === 0 }));
        proc.on('error', (err) => resolve({ ok: false, error: String(err.message) }));
    });
}

async function imeSet(component = 'dev.devkey.keyboard/.LatinIME') {
    return run(['shell', 'ime', 'set', component]);
}

async function forceStop(pkg = 'dev.devkey.keyboard') {
    return run(['shell', 'am', 'force-stop', pkg]);
}

async function pidof(pkg = 'dev.devkey.keyboard') {
    const result = await run(['shell', 'pidof', pkg]);
    if (!result.ok) return null;
    const pid = result.stdout.trim();
    return pid || null;
}

module.exports = {
    run, tap, swipe, broadcast, dumpLogcat, clearLogcat,
    screencap, imeSet, forceStop, pidof
};
