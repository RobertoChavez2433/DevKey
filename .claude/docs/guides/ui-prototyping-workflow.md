# UI Prototyping Workflow

Rapid visual design iteration using MCP-powered HTML mockups for keyboard layout design.

## Overview

Instead of building Android layouts to see if a keyboard design looks right, we use standalone HTML/CSS mockups served via the html-sync MCP server with real-time hot reload.

| Server | Package | Purpose |
|--------|---------|---------|
| **html-sync** | `mcp-html-sync-server` | Create/update HTML pages with real-time hot reload via WebSocket |

## When to Use This

- Designing keyboard layouts (Full, One-Handed, Floating, Split, Power Mode)
- Iterating on key sizing, spacing, colors, typography
- Comparing multiple layout options side-by-side
- Testing theme designs (Material You, custom themes)
- Getting user approval before writing Kotlin/Compose code
- Any time the user says "show me a mockup" or "let's prototype"

## The Iteration Loop

```
1. Claude calls html-sync create_page → returns URL + page ID
2. User opens URL in browser (auto-refreshes on changes)
3. User gives feedback ("make keys larger", "change modifier color", "try split layout")
4. Claude calls html-sync update_page → browser auto-refreshes
5. Repeat 3-4 until approved
6. Claude writes production Kotlin/Compose code matching the approved design
```

## Keyboard Mockup Boilerplate

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>[Layout Name] Keyboard Mockup</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      max-width: 412px;
      margin: 0 auto;
      min-height: 100vh;
      background: #121212;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: flex;
      flex-direction: column;
      justify-content: flex-end;
    }

    /* Simulated app content area */
    .app-content {
      flex: 1;
      background: #1e1e1e;
      padding: 16px;
      color: #e0e0e0;
    }

    /* Keyboard container */
    .keyboard {
      background: #2b2b2b;
      padding: 4px;
      border-top: 1px solid #404040;
    }

    /* Prediction bar */
    .prediction-bar {
      display: flex;
      gap: 4px;
      padding: 6px 4px;
      background: #333;
      border-radius: 8px 8px 0 0;
    }
    .prediction {
      flex: 1;
      text-align: center;
      padding: 8px 4px;
      color: #e0e0e0;
      font-size: 14px;
      border-radius: 6px;
      cursor: pointer;
    }
    .prediction:hover { background: #444; }

    /* Keyboard row */
    .key-row {
      display: flex;
      gap: 3px;
      padding: 2px 0;
      justify-content: center;
    }

    /* Individual key */
    .key {
      display: flex;
      align-items: center;
      justify-content: center;
      min-width: 32px;
      height: 42px;
      background: #404040;
      color: #e0e0e0;
      border-radius: 6px;
      font-size: 16px;
      cursor: pointer;
      flex: 1;
      max-width: 38px;
      user-select: none;
    }
    .key:active { background: #606060; }

    /* Special keys */
    .key.modifier { background: #505050; color: #90CAF9; font-size: 11px; }
    .key.space { flex: 4; max-width: none; }
    .key.enter { background: #1565C0; color: white; }
    .key.backspace { flex: 1.5; max-width: 56px; }
    .key.shift { flex: 1.5; max-width: 56px; }
    .key.fn { font-size: 10px; background: #3a3a3a; color: #80CBC4; }
    .key.number { background: #3a3a3a; }

    /* Toolbar */
    .toolbar {
      display: flex;
      gap: 8px;
      padding: 4px 8px;
      align-items: center;
    }
    .toolbar-btn {
      width: 28px;
      height: 28px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #aaa;
      font-size: 16px;
      cursor: pointer;
      border-radius: 4px;
    }
    .toolbar-btn:hover { background: #444; }
  </style>
</head>
<body>
  <div class="app-content">
    <p>Simulated app content area</p>
  </div>

  <div class="keyboard">
    <!-- Toolbar + Predictions -->
    <div class="prediction-bar">
      <span class="prediction">the</span>
      <span class="prediction">to</span>
      <span class="prediction">that</span>
    </div>

    <!-- Number row (togglable) -->
    <div class="key-row">
      <div class="key number">1</div>
      <div class="key number">2</div>
      <div class="key number">3</div>
      <div class="key number">4</div>
      <div class="key number">5</div>
      <div class="key number">6</div>
      <div class="key number">7</div>
      <div class="key number">8</div>
      <div class="key number">9</div>
      <div class="key number">0</div>
    </div>

    <!-- QWERTY rows -->
    <div class="key-row">
      <div class="key">Q</div><div class="key">W</div><div class="key">E</div>
      <div class="key">R</div><div class="key">T</div><div class="key">Y</div>
      <div class="key">U</div><div class="key">I</div><div class="key">O</div>
      <div class="key">P</div>
    </div>
    <div class="key-row">
      <div class="key">A</div><div class="key">S</div><div class="key">D</div>
      <div class="key">F</div><div class="key">G</div><div class="key">H</div>
      <div class="key">J</div><div class="key">K</div><div class="key">L</div>
    </div>
    <div class="key-row">
      <div class="key shift modifier">Shift</div>
      <div class="key">Z</div><div class="key">X</div><div class="key">C</div>
      <div class="key">V</div><div class="key">B</div><div class="key">N</div>
      <div class="key">M</div>
      <div class="key backspace modifier">Del</div>
    </div>

    <!-- Bottom row with modifiers -->
    <div class="key-row">
      <div class="key modifier">Ctrl</div>
      <div class="key modifier">Alt</div>
      <div class="key modifier">🌐</div>
      <div class="key space"> </div>
      <div class="key modifier">←</div>
      <div class="key modifier">→</div>
      <div class="key enter">↵</div>
    </div>
  </div>
</body>
</html>
```

## Design Dimensions

Simulate Android keyboard at these sizes:

| Layout | Body max-width | Keyboard height |
|--------|---------------|-----------------|
| Phone portrait | 412px | ~260px (4 rows + bar) |
| Phone landscape | 915px | ~200px (compact) |
| Tablet portrait | 768px | ~320px (larger keys) |
| Tablet landscape | 1024px | ~280px |

## Key Size Guidelines

| Key Type | Min Width | Height | Notes |
|----------|-----------|--------|-------|
| Letter key | 32px | 42px | Standard tap target |
| Modifier (Ctrl/Alt) | 42px | 42px | Slightly wider for labels |
| Space bar | 160px+ | 42px | Flex 4 |
| Backspace/Enter | 50px+ | 42px | Flex 1.5 |
| Function key (F1-F12) | 28px | 36px | Compact in Power Mode |

## MCP Tool Reference

### html-sync Tools

| Tool | Use For |
|------|---------|
| `create_page` | Create a new keyboard mockup page. Returns URL + page ID. |
| `update_page` | Update an existing page by ID. All connected browsers auto-refresh. |

## Tips

- Always constrain mockups to `max-width: 412px` for phone fidelity
- Use dark theme by default (keyboards are typically dark)
- Include realistic prediction text, not placeholder
- When comparing layouts, create separate pages for side-by-side viewing
- Test both portrait and landscape orientations
- Include the modifier row for all DevKey layouts (this is the differentiator)
