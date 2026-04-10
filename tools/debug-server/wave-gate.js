// WHY: Replaces sleep-based test synchronization with event-observed gating.
// FROM SPEC: §6 Phase 2 item 2.2 — "gates wave progression on observed state rather than sleeps"
// NOTE: Dep-free on purpose — existing debug server has zero npm deps.
//       All matching happens in-memory over the already-buffered logs array.

/**
 * Waits for a log entry matching (category, event, match) to appear in the provided
 * `logs` array. Returns a Promise that resolves with the matching entry or rejects on timeout.
 *
 * @param {Array} logs — the shared log buffer (POST /log appends to this)
 * @param {string} category — e.g. "DevKey/VOX"
 * @param {string|null} event — the `message` field on the entry (e.g. "state_transition")
 * @param {object} matchData — subset of the `data` map that must appear on the entry
 * @param {number} timeoutMs
 */
function waitFor(logs, category, event, matchData, timeoutMs) {
    return new Promise((resolve, reject) => {
        // WHY: Scan from index 0 — NOT from `logs.length` at call time.
        //      Callers follow the pattern: clear_logs() → perform_action() → wait_for().
        //      The action runs BEFORE wait_for is called, so any events it produced are
        //      already in the buffer with indices < logs.length. Starting at logs.length
        //      would skip them and guarantee a timeout.
        //      Callers who want "only events AFTER this point" should call /clear first.
        const deadline = Date.now() + timeoutMs;

        const check = () => {
            for (let i = 0; i < logs.length; i++) {
                const entry = logs[i];
                if (entry.category !== category) continue;
                if (event && entry.message !== event) continue;
                if (matchData && !matchesData(entry.data, matchData)) continue;
                return resolve(entry);
            }
            if (Date.now() >= deadline) {
                return reject(new Error(
                    `wait_for timeout after ${timeoutMs}ms — ` +
                    `category=${category} event=${event || '*'} ` +
                    `match=${JSON.stringify(matchData || {})}`
                ));
            }
            setTimeout(check, 50);
        };
        check();
    });
}

/**
 * Returns true if every key in `needle` appears in `haystack` with equal stringified value.
 * DevKeyLogger stringifies all data values before sending, so comparison is string-based.
 */
function matchesData(haystack, needle) {
    if (!haystack || typeof haystack !== 'object') return false;
    for (const [k, v] of Object.entries(needle)) {
        if (String(haystack[k]) !== String(v)) return false;
    }
    return true;
}

// --- Wave state tracker ---

const waves = new Map(); // waveId -> { startIdx, startedAt, status, flows: [] }

function startWave(logs, waveId) {
    waves.set(waveId, {
        startIdx: logs.length,
        startedAt: Date.now(),
        status: 'running',
        flows: []
    });
    return waves.get(waveId);
}

function getWave(waveId) {
    return waves.get(waveId) || null;
}

function finishWave(waveId, status) {
    const w = waves.get(waveId);
    if (w) {
        w.status = status;
        w.finishedAt = Date.now();
    }
}

function allWaves() {
    return Array.from(waves.entries()).map(([id, w]) => ({ id, ...w }));
}

module.exports = { waitFor, matchesData, startWave, getWave, finishWave, allWaves };
