# Emulator Audit Fix Plan (Final — Post-Review)

**Created**: 2026-03-01
**Status**: ACTIVE
**Scope**: BUG-01 through BUG-08 from `.claude/logs/emulator-audit-wave1.md`
**Adversarial Review**: Applied — all critical findings incorporated.

---

## Bug Priority Table

| Bug | Priority | Phase | Description |
|-----|----------|-------|-------------|
| BUG-02 | P1 | 1 | 123 button does not switch to symbols layout |
| BUG-03 | P1 | 2 | Dynamic height ignores user preference |
| BUG-06 | P2 | 3 | Hint mode preference not respected |
| BUG-01 | P1 | 4 | Outdated landing page (relabeled from P0 to match ordering) |
| BUG-04 | P2 | 5 | Orphaned suggestion settings |
| BUG-05 | P2 | 6 | Settings page too long/flat |
| BUG-08 | P2 | 7 | Remove ALL "Hacker's Keyboard" references |
| BUG-07 | — | — | **NOT A BUG**: `0` is on Tab long-press. No action. |

> Note: BUG-01 was originally P0/Blocker. Reordered to Phase 4 because P1 keyboard function bugs impact every session while the landing page is seen only once. Priority relabeled P1 to match actual ordering.

---

## Phase 1 — BUG-02: Fix 123 Button Symbol Switch (P1)

### Goal
Determine why `toggleMode(KeyboardMode.Symbols)` has no visible effect and fix it.

### Correct File Paths
- **ToolbarRow**: `app/src/main/java/dev/devkey/keyboard/ui/toolbar/ToolbarRow.kt` (NOT `ui/keyboard/`)
- **DevKeyKeyboard**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
- **ComposeKeyboardViewFactory**: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/ComposeKeyboardViewFactory.kt`

### Wiring Chain (verified correct on paper)
1. `ToolbarRow.kt` ~line 74: `ToolbarButton(label = "123", onClick = { onSymbols() })`
2. `DevKeyKeyboard.kt` line 133: `onSymbols = { toggleMode(KeyboardMode.Symbols) }`
3. `DevKeyKeyboard.kt` lines 107-109: `toggleMode()` updates `keyboardMode` mutableStateOf
4. `DevKeyKeyboard.kt` ~line 237: layout conditional reads `keyboardMode`

### Candidate Root Causes (revised per review)

**Candidate C — Stale IMELifecycleOwner (MOST LIKELY)**
The static `lifecycleOwner` in `ComposeKeyboardViewFactory` persists across `onCreateInputView()` recreations. Stale lifecycle can corrupt Compose state holders, making `mutableStateOf` updates not trigger recomposition.

**IMPORTANT**: The fix must NOT break the API 36 `ViewTreeLifecycleOwner` defect pattern. `installLifecycleOwner()` sets the lifecycle owner on the window decor view. The `create()` method sets it on the ComposeView. These must use the SAME owner instance. Fix: always create fresh in `create()`, AND update `installLifecycleOwner()` to use the same fresh instance.

**Candidate D — Zero-height ToolbarRow (NEW, missed by original plan)**
If `DevKeyTheme.toolbarHeight` evaluates to `0.dp`, the toolbar row has zero height and all touch targets are non-hittable. Verify by adding a temporary border.

**Candidate B — Snapshot isolation / recomposition boundary**
Less likely since `keyboardMode` is a `var by remember { mutableStateOf(...) }` which Compose tracks. But extracting the layout derivation to a local `val activeLayout = when(keyboardMode) { ... }` before the `KeyboardView(...)` call is a safe improvement.

**Candidate A — IME touch interception (LEAST LIKELY)**
The `ToolbarRow` uses standard `clickable` modifiers. `detectTapGestures` in `KeyboardView` is scoped to its own bounds and shouldn't intercept toolbar clicks.

> **Review note**: Do NOT set `isFocusableInTouchMode = false` on ComposeView — this risks breaking focus event dispatch in the IME context.

### Diagnostic Steps

**Step 1**: Add `Log.d("DevKey/Mode", ...)` in `toggleMode()` and before `KeyboardView(...)` call. Deploy and check logcat.

**Step 2**: Add temporary `Modifier.border(1.dp, Color.Red)` to the ToolbarRow to confirm it has non-zero height.

**Step 3**: Based on logs:
- If `toggleMode` never called → touch not reaching Compose → investigate ComposeView hosting
- If `toggleMode` called but layout doesn't change → Candidate B/C → apply fixes below

### Fixes

**Fix B** (apply regardless — safe improvement):
```kotlin
// DevKeyKeyboard.kt, before KeyboardView call
val activeLayout = when (keyboardMode) {
    is KeyboardMode.Symbols -> SymbolsLayout.layout
    else -> QwertyLayout.getLayout(compactMode.value)
}
KeyboardView(
    layout = activeLayout,
    ...
)
```

**Fix C** (apply if diagnosis confirms):
In `ComposeKeyboardViewFactory.kt`, always create a fresh `IMELifecycleOwner` in `create()`, clear the old `ViewModelStore` before replacing:
```kotlin
@JvmStatic
fun create(context: Context, actionListener: ...): View {
    lifecycleOwner?.viewModelStore?.clear()  // prevent ViewModel leaks
    val owner = IMELifecycleOwner()
    owner.performRestore(null)
    owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
    owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    lifecycleOwner = owner
    // installLifecycleOwner() uses this same static field, so decor view and ComposeView share the owner
    ...
}
```

### Verification
1. Tap "123" → symbols layout appears (@, #, $, _, etc.)
2. Tap "ABC" → QWERTY returns
3. Toggle back and forth 3 times rapidly
4. **Regression**: Verify Clipboard, Voice, Macro modes still work (all use `toggleMode`)
5. Remove diagnostic logging after fix confirmed

### Files Modified
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/ComposeKeyboardViewFactory.kt`

---

## Phase 2 — BUG-03: Wire Dynamic Height from SharedPreferences (P1)

### Goal
Replace hardcoded `screenHeightDp * 0.40` with user preference value.

### Default Value Reconciliation
**Single source of truth** — add to `SettingsRepository.kt` companion:
```kotlin
const val DEFAULT_HEIGHT_PORTRAIT = 40   // percent
const val DEFAULT_HEIGHT_LANDSCAPE = 55  // percent
```
Update `SettingsScreen.kt` to use these constants instead of its current hardcoded 50/40.

### Step 1 — Modify KeyboardView signature
File: `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardView.kt`

Add `heightPercent: Float = 0.40f` parameter. Replace hardcoded calculation:
```kotlin
val keyAreaHeight = (screenHeightDp * heightPercent).dp - DevKeyTheme.toolbarHeight
```

**CRITICAL — add height clamping guard** (review finding: negative height crashes Compose):
```kotlin
val rawHeight = (screenHeightDp * heightPercent).dp - DevKeyTheme.toolbarHeight
val keyAreaHeight = rawHeight.coerceAtLeast(48.dp)
```

### Step 2 — Observe height in DevKeyKeyboard.kt
Follow existing `compactMode` pattern. Use `remember(isLandscape)` so orientation changes re-read the correct key:
```kotlin
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val heightKey = if (isLandscape) SettingsRepository.KEY_HEIGHT_LANDSCAPE else SettingsRepository.KEY_HEIGHT_PORTRAIT
val heightDefault = if (isLandscape) SettingsRepository.DEFAULT_HEIGHT_LANDSCAPE else SettingsRepository.DEFAULT_HEIGHT_PORTRAIT

val keyboardHeightPercent = remember(isLandscape) {
    mutableStateOf(prefs.getInt(heightKey, heightDefault) / 100f)
}
DisposableEffect(prefs, isLandscape) {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == heightKey) {
            keyboardHeightPercent.value = prefs.getInt(heightKey, heightDefault) / 100f
        }
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
```

### Step 3 — Pass to KeyboardView
```kotlin
KeyboardView(
    ...
    heightPercent = keyboardHeightPercent.value,
    ...
)
```

### Step 4 — Verify height pref is stored as Int
Check `SettingsScreen.kt`'s slider implementation to confirm it calls `settingsRepository.setInt()` (not `setFloat`). If it uses `setFloat`, the `getInt()` call in DevKeyKeyboard will return 0 → keyboard collapses. This MUST be verified before implementation.

### Verification
1. Change Portrait Height slider to 60% → keyboard visibly taller
2. Change to 25% → keyboard visibly shorter
3. Rotate to landscape → keyboard uses landscape default (55%)
4. Set height to minimum (15%) → keyboard renders correctly, not negative/crashed
5. Settings slider shows value matching actual keyboard height

### Files Modified
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardView.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
- `app/src/main/java/dev/devkey/keyboard/data/repository/SettingsRepository.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsScreen.kt`

---

## Phase 3 — BUG-06: Hint Mode Preference (P2)

### Goal
Make hint text visibility respect the `pref_hint_mode` setting.

### Hint Mode Values (from SettingsScreen.kt)
- `"0"` = Hidden
- `"1"` = Visible (dim)
- `"2"` = Visible (bright)

### Default Value (CORRECTED per review)
Use `"0"` as default — matching `SettingsScreen.kt`'s existing default. The original plan used `"1"` which would create a mismatch where fresh installs show hints while Settings UI says "Hidden".

### Implementation
Thread `showHints: Boolean` and `hintBright: Boolean` through the composable chain:

1. **DevKeyKeyboard.kt**: Observe `KEY_HINT_MODE` (same pattern as compactMode), compute `showHints` and `hintBright`
2. **KeyboardView.kt**: Add `showHints: Boolean = false, hintBright: Boolean = false` parameters, pass to KeyRow
3. **KeyRow.kt**: Add same parameters, pass to KeyView
4. **KeyView.kt**: Change hint rendering condition:
```kotlin
if (showHints && key.longPressLabel != null && !(ctrlHeld && (key.type == KeyType.LETTER || key.type == KeyType.NUMBER))) {
    Text(
        text = key.longPressLabel,
        color = if (hintBright) DevKeyTheme.keyText.copy(alpha = 0.7f) else DevKeyTheme.keyHint,
        ...
    )
}
```

### Dependency on Phase 2
Both phases modify `KeyboardView.kt` and `KeyRow.kt` signatures. **Must be implemented sequentially, not in parallel**, to avoid merge conflicts. Phase 3 adds its parameters to the signatures Phase 2 already modified.

### Verification
1. Set Hint Mode to "Hidden" → no hint text visible
2. Set to "Visible (dim)" → hints appear in dark gray (#666666)
3. Set to "Visible (bright)" → hints appear brighter
4. Changes apply without restarting keyboard
5. Ctrl mode still hides hints on letter/number keys regardless of setting

### Files Modified
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/DevKeyKeyboard.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyboardView.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyRow.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/keyboard/KeyView.kt`

---

## Phase 4 — BUG-01: Landing Page Redesign (P1)

### Goal
Replace old Hacker's Keyboard setup wizard with a modern Compose-based DevKey welcome screen.

### New File
`app/src/main/java/dev/devkey/keyboard/ui/welcome/DevKeyWelcomeActivity.kt`

### Design
- Extends `ComponentActivity`, uses `setContent` with `darkColorScheme()`
- Shows DevKey branding: title "DevKey", subtitle "Power-user keyboard for Android"
- 3 setup steps with live completion status:
  1. **Enable DevKey** → `Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)`
  2. **Set as Default** → `InputMethodManager.showInputMethodPicker()`
  3. **Languages (Optional)** → `Intent(this, InputLanguageSelection::class.java)`
- Bottom: Settings button → `DevKeySettingsActivity`
- Step detection via `InputMethodManager.getEnabledInputMethodList()` and `Settings.Secure.DEFAULT_INPUT_METHOD`
- Refreshes on `ON_RESUME` via `LifecycleEventObserver`

### API 33+ Concerns (from review)
- `showInputMethodPicker()` may silently no-op from a non-IME activity on API 33+. **Mitigation**: If it fails, fall back to opening `Settings.ACTION_INPUT_METHOD_SETTINGS` and showing a toast "Select DevKey from the list".
- `Settings.Secure.DEFAULT_INPUT_METHOD` may require `READ_SECURE_SETTINGS` on some API levels. **Mitigation**: Wrap in try/catch, if permission denied, show step 2 as "unknown" rather than crashing.

### Manifest Changes
`app/src/main/AndroidManifest.xml`:
1. Remove LAUNCHER intent from `Main`
2. Add `DevKeyWelcomeActivity` as new LAUNCHER
3. Keep `Main` declared with `android:exported="false"` (needed for build — still referenced)

```xml
<!-- Legacy — kept for backward compat, no longer launchable -->
<activity android:name="Main" android:label="DevKey" android:exported="false" />

<activity
    android:name=".ui.welcome.DevKeyWelcomeActivity"
    android:label="DevKey"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
</activity>
```

### Verification
1. Uninstall app, reinstall, launch → DevKeyWelcomeActivity appears
2. Step 1: Enable → Android settings opens → enable DevKey → back → checkmark
3. Step 2: Set Default → picker appears (or fallback toast) → select DevKey → checkmark
4. Step 3: Languages → InputLanguageSelection opens → back → returns to welcome
5. Settings button → DevKeySettingsActivity opens
6. **No "Hacker's Keyboard" text anywhere on the welcome screen**

### Files Created/Modified
- `app/src/main/java/dev/devkey/keyboard/ui/welcome/DevKeyWelcomeActivity.kt` (NEW)
- `app/src/main/AndroidManifest.xml`

---

## Phase 5 — BUG-04: Clarify Orphaned Suggestion Settings (P2)

### Goal
Add explanatory subtitle to the "Prediction & Autocorrect" category header. Do NOT remove settings — they still feed `LatinIME.java`.

### Changes

**SettingsComponents.kt**: Add `subtitle: String? = null` parameter to `SettingsCategory`:
```kotlin
@Composable
fun SettingsCategory(title: String, subtitle: String? = null) {
    Column(...) {
        Text(text = title, color = DevKeyTheme.settingsCategoryColor, ...)  // Use settingsCategoryColor, NOT accentColor
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 11.sp, color = DevKeyTheme.keyHint, ...)
        }
    }
}
```

> **Review correction**: Use `DevKeyTheme.settingsCategoryColor` — `accentColor` does not exist and will fail compilation.

**SettingsScreen.kt**: Add subtitle to prediction category:
```kotlin
SettingsCategory(
    title = "Prediction & Autocorrect",
    subtitle = "Controls the legacy suggestion engine. Suggestion bar is not shown in current UI."
)
```

### Migration Note for Phase 6
When Phase 6 extracts settings into sub-screens, this subtitle change MUST be preserved in the extracted `PredictionSettingsScreen` composable. Phase 6 implementer must verify this.

### Files Modified
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsComponents.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsScreen.kt`

---

## Phase 6 — BUG-05: Hierarchical Settings Navigation (P2)

### Goal
Replace the flat 57-item scroll with a two-level navigation: category tiles → sub-screens.

### Scope Note
This is the largest and riskiest phase. **Consider deferring to a separate session** if Phases 1-5 consume significant time. The flat list is functional, just not polished.

### Architecture
Extend `SettingsNav` enum in `DevKeySettingsActivity.kt` with one entry per category (10 new values). Create:
- `SettingsCategoryScreen.kt` (NEW) — Top-level grid/list of category tiles
- `SettingsSubScreens.kt` (NEW) — Individual category composables extracted from `SettingsScreen.kt`

Use `git mv` (not create-new + delete-old) when renaming to preserve git history.

### Back Navigation
Each sub-screen takes `onBack: () -> Unit`. Activity's back handler sets `currentNav = SettingsNav.MAIN`. Sub-sub-screens (MACRO_MANAGER, COMMAND_APPS) go back to their parent category, not MAIN.

### Rollback Plan
Commit Phase 5 changes BEFORE starting Phase 6. If Phase 6 breaks, `git revert` restores functional settings.

### Files Created/Modified
- `app/src/main/java/dev/devkey/keyboard/ui/settings/DevKeySettingsActivity.kt`
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsCategoryScreen.kt` (NEW)
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsSubScreens.kt` (NEW)
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsScreen.kt` (DELETE after migration)

---

## Phase 7 — Remove ALL "Hacker's Keyboard" References (P2)

### Goal
Remove every single reference to "Hacker's Keyboard" from the codebase. Attribution will be in a separate document (e.g., CREDITS.md or NOTICE), NOT in the app UI.

### Comprehensive Grep Required
```bash
grep -ri "hacker" --include="*.kt" --include="*.java" --include="*.xml" --include="*.md" app/ .claude/
```

### Known Locations

**1. `strings.xml` (English) — `main_body` HTML**
File: `app/src/main/res/values/strings.xml`, lines 627-662
- Replace ALL "Hacker's Keyboard" with "DevKey"
- Remove GitHub link to `klausw/hackerskeyboard`
- Remove "completion dictionary packages" Play Store reference

**2. `SettingsScreen.kt` (or `SettingsSubScreens.kt` after Phase 6) — About section**
Remove "Built on Hacker's Keyboard" entry entirely. Replace with:
```kotlin
item(key = "version") {
    ButtonSetting(
        title = "Version",
        description = versionName
    ) { /* no-op */ }
}
```

**3. Russian strings (and any other locale overrides)**
Grep all `app/src/main/res/values-*/strings.xml` for "Hacker". Replace all occurrences with "DevKey".

**4. `_defects.md` and project docs**
These reference "Hacker's Keyboard" as historical context. These are internal development docs, not user-facing — leave as-is.

**5. `auto-version.xml`**
File: `app/src/main/res/values/auto-version.xml`
Change from "custom" to match `build.gradle.kts` versionName or remove in favor of reading from `packageManager`.

### Verification
1. Run `grep -ri "hacker" app/` — zero results in source/resources
2. Launch app → no "Hacker's Keyboard" visible anywhere
3. Open Settings → About → no "Hacker's Keyboard"
4. Build succeeds with no warnings about missing string references

### Files Modified
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-*/strings.xml` (all locale overrides)
- `app/src/main/java/dev/devkey/keyboard/ui/settings/SettingsScreen.kt`
- `app/src/main/res/values/auto-version.xml`

---

## Implementation Order & Dependencies

```
Phase 1 (BUG-02: 123 button)       — No dependencies. Start here.
Phase 2 (BUG-03: Dynamic height)   — Independent of Phase 1. Can parallel.
Phase 3 (BUG-06: Hint mode)        — SEQUENTIAL after Phase 2 (shared file signatures).
Phase 4 (BUG-01: Landing page)     — Independent. Can parallel with 1-3.
Phase 5 (BUG-04: Orphaned labels)  — Independent. Small change.
Phase 6 (BUG-05: Settings nav)     — After Phase 5 (must preserve subtitle). DEFER if time-constrained.
Phase 7 (BUG-08: Remove branding)  — After Phase 4 (landing page). Last phase.
```

### Parallel-Safe Groups (for /implement agents)
- **Group A**: Phase 1 + Phase 4 (no file overlap)
- **Group B**: Phase 2 → Phase 3 (sequential, shared files)
- **Group C**: Phase 5 → Phase 6 → Phase 7 (sequential, shared files)

---

## Architectural Notes (from review)

### Preference Reading Pattern
Phases 2-3 add raw `SharedPreferences` listeners in `DevKeyKeyboard.kt`, bypassing `SettingsRepository`. This is pragmatic (DevKeyKeyboard doesn't have a SettingsRepository instance) but not ideal. **Future improvement**: pass `SettingsRepository` to `DevKeyKeyboard` via `ComposeKeyboardViewFactory.create()` and use Flow-based observation with `collectAsState()`.

### Multiple DisposableEffect Blocks
After Phases 2-3, `DevKeyKeyboard.kt` will have 3-4 `DisposableEffect` blocks for preference listening. This is deliberate — each independently registers/unregisters. A future consolidation into a single listener that dispatches to multiple state holders would be cleaner.

### Phase 6 Navigation Pattern
The plan extends the `SettingsNav` enum rather than introducing Compose Navigation (`NavHost`). This keeps complexity down for now but should be migrated to proper Compose Navigation in a future session.

---

## Review Findings Incorporated

| # | Finding | Resolution |
|---|---------|------------|
| 1 | ToolbarRow path wrong | **Fixed**: Correct path `ui/toolbar/` used throughout |
| 2 | Candidate C contradicts API 36 defect | **Fixed**: Clear old ViewModelStore, keep shared static owner |
| 3 | Hint mode default mismatch | **Fixed**: Use `"0"` consistently |
| 4 | `DevKeyTheme.accentColor` doesn't exist | **Fixed**: Use `settingsCategoryColor` |
| 5 | No height clamping | **Fixed**: Added `.coerceAtLeast(48.dp)` |
| 6 | BUG-07 silently omitted | **Fixed**: Explicitly marked NOT A BUG in table |
| 7 | Phase 5→6 subtitle loss | **Fixed**: Migration note added |
| 8 | `isFocusableInTouchMode = false` risky | **Fixed**: Removed from plan |
| 9 | `showInputMethodPicker()` API 33+ | **Fixed**: Fallback added |
| 10 | Candidate likelihood ranking wrong | **Fixed**: C marked most likely |
| 11 | Missing Candidate D (zero toolbar height) | **Fixed**: Added as diagnostic step |
