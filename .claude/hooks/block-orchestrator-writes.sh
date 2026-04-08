#!/usr/bin/env bash
# PreToolUse hook: block write/edit operations for orchestrator agents.
# Reads JSON from stdin, outputs decision to stdout.
# Always exits 0 (hook framework requirement).
#
# Blocks:
#   - Edit, Write, NotebookEdit, MultiEdit tool calls
#   - Bash commands containing: sed -i, awk -i, > (redirect), >>, tee, rm, mv, cp

set -euo pipefail

input="$(cat)"

# Extract tool name using grep -oP (no jq required — Windows Git Bash compatible)
tool_name=""
tool_name="$(printf '%s' "$input" | grep -oP '"tool_name"\s*:\s*"\K[^"]+' | head -1)" || true

# --- Block write/edit tools directly ---
case "$tool_name" in
  Edit|Write|NotebookEdit|MultiEdit)
    printf '{"decision":"block","reason":"Orchestrator agents may not use %s. Sub-agents perform all writes."}\n' "$tool_name"
    exit 0
    ;;
esac

# --- For Bash tool, inspect the command string ---
if [ "$tool_name" = "Bash" ]; then
  command_str=""
  command_str="$(printf '%s' "$input" | grep -oP '"command"\s*:\s*"\K[^"]+' | head -1)" || true

  # Check for sed -i (in-place edit)
  if printf '%s' "$command_str" | grep -qP 'sed\s+-[a-zA-Z]*i'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use sed -i (in-place file edit)."}\n'
    exit 0
  fi

  # Check for awk -i (in-place edit)
  if printf '%s' "$command_str" | grep -qP 'awk\s+-[a-zA-Z]*i'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use awk -i (in-place file edit)."}\n'
    exit 0
  fi

  # Check for output redirects (> or >>)
  # Match > or >> not preceded by < (to avoid matching <<EOF heredocs incorrectly)
  if printf '%s' "$command_str" | grep -qP '(?<!<)>{1,2}(?!>)'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use shell output redirection (> or >>)."}\n'
    exit 0
  fi

  # Check for tee
  if printf '%s' "$command_str" | grep -qP '\btee\b'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use tee (file write via pipe)."}\n'
    exit 0
  fi

  # Check for rm (file removal)
  if printf '%s' "$command_str" | grep -qP '\brm\b'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use rm (file removal)."}\n'
    exit 0
  fi

  # Check for mv (file move/rename)
  if printf '%s' "$command_str" | grep -qP '\bmv\b'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use mv (file move)."}\n'
    exit 0
  fi

  # Check for cp (file copy)
  if printf '%s' "$command_str" | grep -qP '\bcp\b'; then
    printf '{"decision":"block","reason":"Orchestrator agents may not use cp (file copy)."}\n'
    exit 0
  fi
fi

# Default: allow
printf '{"decision":"allow"}\n'
exit 0
