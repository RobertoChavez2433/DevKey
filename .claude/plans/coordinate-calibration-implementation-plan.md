# Coordinate Calibration Implementation Plan

**Design**: `docs/plans/2026-03-04-coordinate-calibration-design.md`
**Status**: Ready for implementation

## Steps

### Step 1: Update `key-coordinates.md` with calibrated Y values

**File**: `.claude/logs/key-coordinates.md`

1. Replace all FULL mode row Y centers with calibrated values:
   - Number: 1599 → 1475
   - QWERTY: 1745 → 1605
   - Home: 1899 → 1775
   - Z: 2052 → 1925
   - Space: 2206 → 2090
   - Utility: 2343 → 2225
2. Update all ADB command examples in the file that use old Y values
3. Update bash array sections with new Y values
4. Add header note: "These are reference values for emulator-5554 (1080x2400). For runtime-accurate coordinates, use DevKeyMap logcat or .claude/test-flows/calibration.json"
5. Mark COMPACT and COMPACT_DEV sections as "uncalibrated — values are calculated, not verified"
6. Update text field tap target to (540, 1356)

**Verify**: Grep file for old Y values to confirm none remain.

### Step 2: Add `dumpToLogcatWhenReady()` to KeyMapGenerator

**File**: `app/src/main/java/dev/devkey/keyboard/debug/KeyMapGenerator.kt`

1. Add a new suspend function `dumpToLogcatWhenReady(context, keyboardView, layoutMode)`:
   ```kotlin
   suspend fun dumpToLogcatWhenReady(context: Context, keyboardView: View, layoutMode: LayoutMode) {
       // Poll until view is laid out and has valid dimensions, max 2s
       var waited = 0L
       while (waited < 2000L && (!keyboardView.isLaidOut || keyboardView.height == 0)) {
           kotlinx.coroutines.delay(100L)
           waited += 100L
       }
       dumpToLogcat(context, keyboardView, layoutMode)
   }
   ```
2. No changes to existing methods

**Verify**: Unit test not needed (suspend + View interaction). Will verify via logcat in Step 5.

### Step 3: Fix LaunchedEffect in DevKeyKeyboard.kt

**File**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`

1. Change line ~101: `LaunchedEffect(Unit)` → `LaunchedEffect(layoutMode)`
2. Replace the body:
   ```kotlin
   LaunchedEffect(layoutMode) {
       if (KeyMapGenerator.isDebugBuild(context)) {
           KeyMapGenerator.dumpToLogcatWhenReady(context, currentView, layoutMode)
       }
   }
   ```
3. Remove the old `kotlinx.coroutines.delay(500L)` line (replaced by polling in `dumpToLogcatWhenReady`)

**Verify**: Build succeeds. Logcat shows DevKeyMap output when keyboard opens.

### Step 4: Add broadcast receiver in LatinIME.kt

**File**: `app/src/main/java/dev/devkey/keyboard/LatinIME.kt`

1. Add a private BroadcastReceiver field:
   ```kotlin
   private var keyMapDumpReceiver: BroadcastReceiver? = null
   ```
2. In `onCreate()`, after initialization, register the receiver (debug builds only):
   ```kotlin
   if (KeyMapGenerator.isDebugBuild(this)) {
       keyMapDumpReceiver = object : BroadcastReceiver() {
           override fun onReceive(context: Context, intent: Intent) {
               val view = mComposeKeyboardView ?: return
               val mode = keyboardModeManager?.currentLayoutMode ?: LayoutMode.FULL
               KeyMapGenerator.dumpToLogcat(context, view, mode)
           }
       }
       registerReceiver(
           keyMapDumpReceiver,
           IntentFilter("dev.devkey.keyboard.DUMP_KEY_MAP"),
           Context.RECEIVER_NOT_EXPORTED
       )
   }
   ```
3. In `onDestroy()`, unregister:
   ```kotlin
   keyMapDumpReceiver?.let { unregisterReceiver(it) }
   ```
4. Need to verify what field names hold the current ComposeView and layout mode in LatinIME — read the file to confirm before implementing.

**Verify**: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP` → logcat shows DevKeyMap output.

### Step 5: Update `ime-setup` flow in registry.md

**File**: `.claude/test-flows/registry.md`

1. Replace step 3 with the 3-tier calibration cascade:
   - 3a: Broadcast + read DevKeyMap (fast path)
   - 3b: Read calibration.json cache
   - 3c: Y-scan probe (slow path, saves to calibration.json)
2. Update the verify section to mention calibration source
3. Increase timeout from 15s to 30s (Y-scan can take ~10s)

### Step 6: Update test skill docs

**Files**:
- `.claude/skills/test/SKILL.md` — Update "Key Coordinate System" section, remove hardcoded -153px offset, add calibration.json to reference docs table
- `.claude/skills/test/references/adb-commands.md` — Add broadcast command for DevKeyMap dump, document calibration cascade

### Step 7: Add calibration.json to .gitignore

**File**: `.gitignore`

Add `.claude/test-flows/calibration.json` (device-specific runtime data).

### Step 8: Build and verify

1. `./gradlew assembleDebug` — confirm build passes
2. Install on emulator, open text field
3. Check `adb logcat -s DevKeyMap:D` — should show key coordinates
4. Test broadcast: `adb shell am broadcast -a dev.devkey.keyboard.DUMP_KEY_MAP`
5. Switch layout mode and verify re-dump
6. Run `/test --smoke` to verify calibration cascade works end-to-end

## Dependency Order

Steps 1, 6, 7 are independent (docs/config only).
Steps 2 → 3 → 4 are sequential (each builds on the prior).
Step 5 depends on understanding the broadcast action from Step 4.
Step 8 depends on all prior steps.

Parallel execution: Steps 1 + 7 can run in parallel with Steps 2-4. Step 5 + 6 can run after 4.
