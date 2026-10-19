#!/usr/bin/env bash
# test-hook.sh <hook-path> — G1-hook suite for model-mesh-hook.sh (DESIGN §9 G1-hook, plus the hook-level parts of
# G2-4/G2-5/G2-6, G3, G3b-by-override, G3d and G4). Every case runs the given hook with fabricated stdin under
# `env -i` and a scratch HOME / CLAUDE_CONFIG_DIR / TMPDIR / cwd, so nothing in the caller's environment or
# real config leaks in and nothing real is touched. Each case also checks the write fence: the only paths the
# hook may create or change are ${TMPDIR}/model-mesh/, the MODEL_MESH_HOOK_LOG file and (v2.5, SubagentStop
# only — asserted by the usage cases) $MODEL_MESH_HOME/journal/usage.log.
# The suite runs twice: parser=python3, then parser=jq via the MM_TEST_NO_PYTHON seam (skipped if jq is absent);
# the no-parser seam (MM_TEST_NO_PYTHON=1 MM_TEST_NO_JQ=1) has its own cases.
# Prints PASS/FAIL per case and a summary; exits 0 only if every case passes (2 on a usage/setup error).
# Optional: MM_TEST_SHELL=dash (or any sh) runs the hook under that shell instead of its shebang.
# bash 3.2 compatible; no arrays under set -u.

set -u
HOOK=${1:-}
if [ -z "$HOOK" ] || [ ! -f "$HOOK" ]; then
  echo "usage: test-hook.sh <hook-path>" >&2
  exit 2
fi
case $HOOK in /*) ;; *) HOOK="$PWD/$HOOK" ;; esac

tbase=${TMPDIR:-/tmp}
tbase=${tbase%/}
ROOT=$(mktemp -d "$tbase/mm-test-hook.XXXXXX") || { echo "test-hook: mktemp failed" >&2; exit 2; }
case $ROOT in "$tbase"/mm-test-hook.* | /tmp/mm-test-hook.* | /var/folders/*/mm-test-hook.*) ;;
  *) echo "test-hook: refusing unexpected temp path $ROOT" >&2; exit 2 ;;
esac
cleanup() { chmod -R u+rwx "$ROOT" 2>/dev/null; rm -rf -- "$ROOT"; }
trap cleanup EXIT

TPATH=/usr/bin:/bin:/usr/sbin:/sbin
for tool in python3 jq; do
  p=$(command -v "$tool" 2>/dev/null) || continue
  d=${p%/*}
  case ":$TPATH:" in *":$d:"*) ;; *) TPATH="$TPATH:$d" ;; esac
done
HAVE_PY=0; command -v python3 >/dev/null 2>&1 && HAVE_PY=1
HAVE_JQ=0; command -v jq >/dev/null 2>&1 && jq -n 1 >/dev/null 2>&1 && HAVE_JQ=1

if [ -n "${MM_TEST_SHELL:-}" ]; then RUNNER=$MM_TEST_SHELL
elif [ -x "$HOOK" ]; then RUNNER=''
else RUNNER=sh
fi

W="$ROOT/w"
PASS=0
FAIL=0
SKIP=0
MODE=py
PMODE=MM_TEST_PARSER_NOTE=py
OUT=''
RC=0
FENCE=ok

# ------------------------------------------------------------------ fixtures
reset() { # reset [tier-file content | none]  — content is written verbatim with printf %b
  chmod -R u+rwx "$W" 2>/dev/null
  rm -rf -- "$W"
  mkdir -p "$W/home" "$W/cfg" "$W/proj/.claude" "$W/tmp" "$W/mmhome" || { echo "test-hook: fixture failed" >&2; exit 2; }
  : >"$W/transcript.jsonl"
  case ${1-standard} in
    none) ;;
    *) printf '%b' "${1-standard}" >"$W/cfg/model-mesh-tier" ;;
  esac
}
fence() {
  find "$W" \( -path "$W/tmp/model-mesh" -o -path "$W/hook.log" -o -path "$W/mmhome/journal" \) -prune -o -print 2>/dev/null | LC_ALL=C sort
  find "$W" \( -path "$W/tmp/model-mesh" -o -path "$W/hook.log" -o -path "$W/mmhome/journal" \) -prune -o -type f -exec cksum {} + 2>/dev/null | LC_ALL=C sort
}
# run <stdin-json> [VAR=value …] — runs the hook (or $HOOK_OVERRIDE) with a clean environment
run() {
  printf '%s' "$1" >"$ROOT/stdin"
  shift
  local before after h
  h=${HOOK_OVERRIDE:-$HOOK}
  before=$(fence)
  # shellcheck disable=SC2086
  OUT=$(cd "$W/proj" && env -i PATH="$TPATH" HOME="$W/home" TMPDIR="$W/tmp" CLAUDE_CONFIG_DIR="$W/cfg" \
    MODEL_MESH_TEST=1 MM_TEST_MANAGED_SETTINGS="$W/managed.json" MODEL_MESH_HOME="$W/mmhome" \
    MODEL_MESH_HOOK_LOG="$W/hook.log" $PMODE "$@" $RUNNER "$h" <"$ROOT/stdin" 2>"$ROOT/stderr")
  RC=$?
  after=$(fence)
  if [ "$before" = "$after" ]; then FENCE=ok; else FENCE=broken; fi
}
pre() { # pre <tool_input json> [tool_name]
  printf '{"session_id":"sess-1","prompt_id":"p-1","transcript_path":"%s","cwd":"%s","permission_mode":"default","hook_event_name":"PreToolUse","tool_name":"%s","tool_input":%s,"tool_use_id":"toolu_01"}' \
    "$W/transcript.jsonl" "$W/proj" "${2:-Agent}" "$1"
}
agent() { # agent [model-json-value] — mesh-lookup spawn, model omitted when no arg
  if [ $# -eq 0 ]; then pre '{"subagent_type":"mesh-lookup","description":"d","prompt":"p"}'
  else pre "{\"subagent_type\":\"mesh-lookup\",\"description\":\"d\",\"prompt\":\"p\",\"model\":$1}"
  fi
}
switch() { # switch <to_model json value or ''> [event]
  local ev=${2:-PreModelSwitch}
  if [ -n "$1" ]; then
    printf '{"session_id":"sess-1","transcript_path":"%s","cwd":"%s","hook_event_name":"%s","from_model":"claude-sonnet-5-5","to_model":%s,"source":"user"}' \
      "$W/transcript.jsonl" "$W/proj" "$ev" "$1"
  else
    printf '{"session_id":"sess-1","transcript_path":"%s","cwd":"%s","hook_event_name":"%s","from_model":"claude-sonnet-5-5","source":"user"}' \
      "$W/transcript.jsonl" "$W/proj" "$ev"
  fi
}
start() { # start <sid> [model json value] [source]
  if [ -n "${2:-}" ]; then
    printf '{"session_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"SessionStart","source":"%s","model":%s}' \
      "$1" "$W/transcript.jsonl" "$W/proj" "${3:-startup}" "$2"
  else
    printf '{"session_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"SessionStart","source":"%s"}' \
      "$1" "$W/transcript.jsonl" "$W/proj" "${3:-startup}"
  fi
}
substop() { # substop <agent_transcript_path or ''> — a SubagentStop input (fields per the hooks docs, 2026-10-04)
  if [ -n "$1" ]; then
    printf '{"session_id":"sess-1","transcript_path":"%s","cwd":"%s","permission_mode":"default","hook_event_name":"SubagentStop","agent_id":"a1","agent_type":"mesh-lookup","agent_transcript_path":"%s","stop_hook_active":false,"last_assistant_message":"done"}' \
      "$W/transcript.jsonl" "$W/proj" "$1"
  else
    printf '{"session_id":"sess-1","transcript_path":"%s","cwd":"%s","hook_event_name":"SubagentStop","agent_id":"a1","agent_type":"mesh-lookup"}' \
      "$W/transcript.jsonl" "$W/proj"
  fi
}
ULOG() { cat "$W/mmhome/journal/usage.log" 2>/dev/null; }
ulog_last() { # ulog_last <substring>… — usage.log has exactly one line and it contains every substring
  local l s
  [ "$RC" = 0 ] && [ -z "$OUT" ] || return 1
  [ "$(ULOG | wc -l | tr -d ' ')" = 1 ] || return 1
  l=$(ULOG)
  for s in "$@"; do case $l in *"$s"*) ;; *) return 1 ;; esac; done
  return 0
}
no_ulog() { [ ! -e "$W/mmhome/journal/usage.log" ]; }
prompt() { # prompt <sid> [prompt_id]
  if [ -n "${2:-}" ]; then
    printf '{"session_id":"%s","prompt_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"UserPromptSubmit","prompt":"do the thing"}' \
      "$1" "$2" "$W/transcript.jsonl" "$W/proj"
  else
    printf '{"session_id":"%s","transcript_path":"%s","cwd":"%s","hook_event_name":"UserPromptSubmit","prompt":"do the thing"}' \
      "$1" "$W/transcript.jsonl" "$W/proj"
  fi
}

# ------------------------------------------------------------------ assertions
json_ok() { # the output is one line of valid JSON whose reason/context has no newline
  [ "$HAVE_PY" = 1 ] || return 0
  printf '%s' "$OUT" | python3 -c '
import json, sys
d = json.load(sys.stdin)
h = d["hookSpecificOutput"]
r = h.get("permissionDecisionReason", h.get("additionalContext"))
assert isinstance(r, str) and "\n" not in r
' 2>/dev/null
}
one_line() { case $OUT in *"
"*) return 1 ;; esac; return 0; }
deny_tool() {
  [ "$RC" = 0 ] && one_line && json_ok &&
    case $OUT in '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"model-mesh: '*) true ;; *) false ;; esac
}
deny_switch() {
  [ "$RC" = 0 ] && one_line && json_ok &&
    case $OUT in '{"hookSpecificOutput":{"hookEventName":"PreModelSwitch","permissionDecision":"deny","permissionDecisionReason":"model-mesh: '*) true ;; *) false ;; esac
}
silent() { [ "$RC" = 0 ] && [ -z "$OUT" ]; }
context_has() { # context_has <event> <substring>…
  local ev=$1; shift
  [ "$RC" = 0 ] && one_line && json_ok || return 1
  case $OUT in '{"hookSpecificOutput":{"hookEventName":"'"$ev"'","additionalContext":"model-mesh: '*) ;; *) return 1 ;; esac
  local s
  for s in "$@"; do case $OUT in *"$s"*) ;; *) return 1 ;; esac; done
  return 0
}
context_lacks() { local s; for s in "$@"; do case $OUT in *"$s"*) return 1 ;; esac; done; return 0; }
check() { # check <name> <predicate …>
  local name=$1; shift
  if "$@" && [ "$FENCE" = ok ]; then
    PASS=$((PASS + 1)); echo "PASS [$MODE] $name"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL [$MODE] $name (rc=$RC fence=$FENCE) out=$(printf '%s' "$OUT" | head -c 400) err=$(head -c 200 "$ROOT/stderr" 2>/dev/null)"
  fi
}
skip() { SKIP=$((SKIP + 1)); echo "SKIP [$MODE] $1"; }

# ------------------------------------------------------------------ the matrix (per parser mode)
suite() {
  # --- DENY on tier standard
  reset standard
  run "$(agent)";                                   check "deny: no model" deny_tool
  run "$(agent '"inherit"')";                       check "deny: model inherit" deny_tool
  run "$(agent '"fable"')";                         check "deny: model fable" deny_tool
  run "$(agent '"claude-fable-5-1[1m]"')";          check "deny: model claude-fable-5-1[1m]" deny_tool
  run "$(agent '"fable"')";                    check "deny: model \\u0066able (JSON escape)" deny_tool
  run "$(agent '"\"fable\""')";                     check "deny: model \"fable\" (quoted)" deny_tool
  run "$(agent '"Fable"')";                         check "deny: model Fable" deny_tool
  run "$(agent '" FABLE "')";                       check "deny: model ' FABLE '" deny_tool
  run "$(agent '"claude-opus-fable"')";             check "deny: model claude-opus-fable" deny_tool
  run "$(agent '"us.anthropic.claude-fable-5-1"')"; check "deny: model us.anthropic.claude-fable-5-1" deny_tool
  run "$(agent '"gpt-6-sol"')";                     check "deny: model outside safe set" deny_tool
  run "$(agent '""')";                              check "deny: model empty string" deny_tool
  run "$(agent 'null')";                            check "deny: model null" deny_tool
  run "$(agent '5')";                               check "deny: model non-string" deny_tool
  run "$(agent '"sonnet\nfable"')";                 check "deny: model with embedded newline" deny_tool
  run "$(pre '{"description":"d","prompt":"p","model":"sonnet"}')";                 check "allow: missing subagent_type with a safe model (default agent)" silent
  run "$(pre '{"subagent_type":7,"model":"sonnet"}')";                              check "deny: non-string subagent_type" deny_tool
  run "$(pre '"not-an-object"')";                                                   check "deny: tool_input not an object" deny_tool
  run '{"hook_event_name":"PreToolUse","tool_name":"Agent","tool_input":{"subagent_type":"x","model":"sonnet"'; check "deny: malformed stdin (truncated)" deny_tool
  run '[1,2]';                                       check "deny: malformed stdin (array)" deny_tool
  run '';                                            check "deny: malformed stdin (empty)" deny_tool
  run '{"a":1} {"b":2}';                             check "deny: malformed stdin (two objects)" deny_tool
  run "$(printf '{"session_id":"s","cwd":"%s","hook_event_name":"PreToolUse","tool_input":{"subagent_type":"x","model":"sonnet"}}' "$W/proj")"
  check "deny: missing tool_name" deny_tool
  run "$(pre '{"subagent_type":"general-purpose"}' Task)";                          check "deny: Task tool, no model" deny_tool

  # dir unreadable
  reset fable
  run "$(agent '"sonnet"')" CLAUDE_CONFIG_DIR="$W/no-such-dir"; check "deny: config dir missing" deny_tool
  if [ "$(id -u)" != 0 ]; then
    chmod 000 "$W/cfg"
    run "$(agent '"sonnet"')";                       check "deny: config dir unreadable (mode 000, tier file fable inside)" deny_tool
    chmod 755 "$W/cfg"
  else
    skip "config dir mode 000 (running as root)"
  fi

  # step 0: alias / model env vars, environment and every settings file
  reset standard
  for var in ANTHROPIC_DEFAULT_OPUS_MODEL ANTHROPIC_DEFAULT_SONNET_MODEL ANTHROPIC_DEFAULT_HAIKU_MODEL \
    ANTHROPIC_SMALL_FAST_MODEL CLAUDE_CODE_SUBAGENT_MODEL; do
    run "$(agent '"sonnet"')" "$var=claude-fable-5-1"; check "deny: env $var=claude-fable-5-1 with model sonnet" deny_tool
    for sf in "$W/cfg/settings.json" "$W/proj/.claude/settings.json" "$W/proj/.claude/settings.local.json" "$W/managed.json"; do
      reset standard
      printf '{"env":{"%s":"claude-fable-5-1"}}' "$var" >"$sf"
      run "$(agent '"sonnet"')"
      check "deny: settings env $var=claude-fable-5-1 in ${sf#"$W"/} with model sonnet" deny_tool
    done
    reset standard
  done
  run "$(agent '"sonnet"')" ANTHROPIC_DEFAULT_SONNET_MODEL=fable;      check "deny: env ANTHROPIC_DEFAULT_SONNET_MODEL=fable (label)" deny_tool
  run "$(agent '"haiku"')" CLAUDE_CODE_SUBAGENT_MODEL=inherit;          check "deny: env CLAUDE_CODE_SUBAGENT_MODEL=inherit" deny_tool
  printf '{"env":{"ANTHROPIC_DEFAULT_OPUS_MODEL":7}}' >"$W/cfg/settings.json"
  run "$(agent '"opus"')";                                              check "deny: settings env alias non-string" deny_tool
  reset standard

  # unparsable settings → deny spawns (both tiers)
  for t in standard fable; do
    for sf in "$W/cfg/settings.json" "$W/proj/.claude/settings.json" "$W/proj/.claude/settings.local.json" "$W/managed.json"; do
      reset "$t"
      printf '{"env":' >"$sf"
      run "$(agent '"sonnet"')"; check "deny: unparsable ${sf#"$W"/} (tier $t)" deny_tool
    done
    reset "$t"; printf '{"env":"x"}' >"$W/proj/.claude/settings.json"
    run "$(agent '"sonnet"')"; check "deny: settings env not an object (tier $t)" deny_tool
    reset "$t"; mkdir "$W/proj/.claude/settings.local.json"
    run "$(agent '"sonnet"')"; check "deny: settings path is a directory (tier $t)" deny_tool
  done
  reset standard
  run "$(printf '{"session_id":"s","cwd":"%s\\nx","hook_event_name":"PreToolUse","tool_name":"Agent","tool_input":{"subagent_type":"x","model":"sonnet"}}' "$W/proj")"
  check "deny: cwd with a control character" deny_tool
  # a non-ASCII, comma-bearing project path (OneDrive-style) must still be read verbatim
  mkdir -p "$W/Proj Ltd.,-é/.claude"
  printf '{"env":{"ANTHROPIC_DEFAULT_HAIKU_MODEL":"claude-fable-5-1"}}' >"$W/Proj Ltd.,-é/.claude/settings.json"
  run "$(printf '{"session_id":"s","cwd":"%s","hook_event_name":"PreToolUse","tool_name":"Agent","tool_input":{"subagent_type":"x","model":"haiku"}}' "$W/Proj Ltd.,-é")"
  check "deny: settings env under a non-ASCII cwd" deny_tool
  reset standard

  # alternate credentials force standard, even with tier file fable
  for var in ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN CLAUDE_CODE_OAUTH_TOKEN CLAUDE_CODE_USE_BEDROCK \
    CLAUDE_CODE_USE_VERTEX CLAUDE_CODE_USE_FOUNDRY; do
    reset fable
    run "$(agent '"fable"')" "$var=dummy-invalid";        check "deny: env $var + tier file fable → model fable" deny_tool
    run "$(agent)" "$var=1";                              check "deny: env $var + tier file fable → no model" deny_tool
    run "$(switch '"claude-fable-5-1"')" "$var=1";        check "deny: env $var + tier file fable → /model fable" deny_switch
    run "$(agent '"sonnet"')" "$var=1";                   check "allow: env $var + tier file fable → model sonnet" silent
    printf '{"env":{"%s":"x"}}' "$var" >"$W/cfg/settings.json"
    run "$(agent '"fable"')";                             check "deny: settings env $var + tier file fable → model fable" deny_tool
  done
  reset fable
  printf '{"apiKeyHelper":"/bin/echo k"}' >"$W/cfg/settings.json"
  run "$(agent '"fable"')";                               check "deny: apiKeyHelper in settings + tier file fable → model fable" deny_tool
  reset fable
  printf '{"apiKeyHelper":"/bin/echo k"}' >"$W/proj/.claude/settings.local.json"
  run "$(agent '"fable"')";                               check "deny: apiKeyHelper in project settings.local.json + tier fable" deny_tool
  run "$(start sess-alt '"claude-opus-5-5"')";            check "state: alternate credentials named" context_has SessionStart "tier=standard (alternate credentials: apiKeyHelper in "

  # PreModelSwitch
  reset standard
  run "$(switch '"fable"')";                  check "deny: to_model fable" deny_switch
  run "$(switch '"claude-fable-5-1"')";       check "deny: to_model claude-fable-5-1" deny_switch
  run "$(switch '"Fable[1m]"')";              check "deny: to_model Fable[1m]" deny_switch
  run "$(switch '')";                         check "deny: to_model missing" deny_switch
  run "$(switch '"opus"')";                   check "allow: to_model opus" silent
  run "$(switch '"claude-opus-5-5"')";        check "allow: to_model claude-opus-5-5" silent
  reset fable
  run "$(switch '"claude-fable-5-1"')";       check "allow: to_model fable on tier fable" silent

  # --- ALLOW on tier standard
  reset standard
  run "$(agent '"sonnet"')";                  check "allow: sonnet" silent
  run "$(agent '"opus"')";                    check "allow: opus" silent
  run "$(agent '"haiku"')";                   check "allow: haiku" silent
  run "$(agent '"\"sonnet\""')";              check "allow: \"sonnet\" (quoted)" silent
  run "$(agent '" Sonnet "')";                check "allow: ' Sonnet ' (trim, case)" silent
  run "$(agent '"claude-opus-5-5[1m]"')";     check "allow: claude-opus-5-5[1m]" silent
  run "$(agent '"claude-sonnet-5-5"')";       check "allow: claude-sonnet-5-5" silent
  run "$(agent '"opus"')" ANTHROPIC_DEFAULT_OPUS_MODEL=claude-opus-5-5; check "allow: ANTHROPIC_DEFAULT_OPUS_MODEL=claude-opus-5-5 with opus" silent
  printf '{"env":{"ANTHROPIC_DEFAULT_OPUS_MODEL":"claude-opus-5-5"}}' >"$W/proj/.claude/settings.json"
  run "$(agent '"opus"')";                    check "allow: settings env ANTHROPIC_DEFAULT_OPUS_MODEL=claude-opus-5-5 with opus" silent
  reset standard
  run "$(agent '"sonnet"')" CLAUDE_CODE_SUBAGENT_MODEL=haiku; check "allow: CLAUDE_CODE_SUBAGENT_MODEL=haiku with sonnet" silent
  run "$(pre '{"subagent_type":"plugin:x","model":"sonnet"}')"; check "allow: plugin:x with sonnet" silent
  run "$(pre '{"subagent_type":"general-purpose","model":"sonnet"}' Task)"; check "allow: Task tool with sonnet" silent
  run "$(pre '{"command":"ls"}' Bash)";       check "pass: non-Agent tool untouched" silent
  run "$(pre '{"script":"export const meta={name:\"x\",description:\"d\"}"}' Workflow)"; check "deny: Workflow on tier standard" deny_tool
  run "$(pre '{"name":"deep-research"}' Workflow)"; check "deny: named Workflow on tier standard" deny_tool
  run "$(printf '{"session_id":"s","hook_event_name":"PreToolUse","tool_name":"Agent","tool_input":{"subagent_type":"x","model":"sonnet"}}')"
  check "allow: cwd absent → hook cwd used" silent
  printf '{}' >"$W/cfg/settings.json"; chmod 000 "$W/cfg/settings.json"
  if [ "$(id -u)" != 0 ]; then
    run "$(agent '"sonnet"')";                check "allow: unreadable settings file skipped" silent
  else skip "unreadable settings (root)"; fi
  chmod 644 "$W/cfg/settings.json"

  # --- tier fable: everything allowed, overrides included
  reset fable
  run "$(agent)";                             check "allow: tier fable, no model" silent
  run "$(agent '"fable"')";                   check "allow: tier fable, model fable" silent
  run "$(agent '"claude-fable-5-1[1m]"')";    check "allow: tier fable, model claude-fable-5-1[1m]" silent
  run "$(agent '"sonnet"')";                  check "allow: tier fable, model sonnet" silent
  run "$(agent '"sonnet"')" ANTHROPIC_DEFAULT_SONNET_MODEL=claude-fable-5-1; check "allow: tier fable with alias override" silent
  printf '{"env":{"CLAUDE_CODE_SUBAGENT_MODEL":"fable"}}' >"$W/proj/.claude/settings.json"
  run "$(agent)";                             check "allow: tier fable with settings-env override" silent
  run "$(pre '{"description":"d","model":"fable"}')"; check "allow: tier fable, missing subagent_type" silent
  run "$(pre '{"name":"deep-research"}' Workflow)"; check "allow: tier fable, Workflow" silent

  # --- tier file parsing
  reset none;          run "$(agent '"fable"')";  check "tier: file missing → standard (fable denied)" deny_tool
  reset 'Fable ';      run "$(agent '"fable"')";  check "tier: 'Fable ' → standard (fable denied)" deny_tool
  reset 'FABLE';       run "$(agent '"fable"')";  check "tier: 'FABLE' → standard" deny_tool
  reset 'fable\n\n';   run "$(agent '"fable"')";  check "tier: 'fable' + two newlines → standard" deny_tool
  reset 'fable\r\n';   run "$(agent '"fable"')";  check "tier: 'fable' CRLF → standard" deny_tool
  reset 'fa\0000ble';  run "$(agent '"fable"')";  check "tier: NUL inside → standard" deny_tool
  reset 'fable\n';     run "$(agent '"fable"')";  check "tier: 'fable' + one newline → fable" silent
  reset 'fable';       run "$(agent '"fable"')";  check "tier: 'fable' no newline → fable" silent
  reset none;          run "$(start sess-t)";     check "state: missing tier file → undeclared→standard" context_has SessionStart "tier=undeclared→standard "
  reset 'standard\n';  run "$(start sess-t)";     check "state: tier file standard" context_has SessionStart "tier=standard account="

  # --- account resolution (G3 at hook level): two config dirs, the state line names the one in use
  reset fable
  mkdir -p "$W/home/.claude-second"; printf 'standard\n' >"$W/home/.claude-second/model-mesh-tier"
  run "$(agent '"fable"')" CLAUDE_CONFIG_DIR="$W/home/.claude-second"; check "G3: second dir standard, primary fable → fable denied" deny_tool
  run "$(start sess-g3)" CLAUDE_CONFIG_DIR="$W/home/.claude-second";   check "G3: state line names the second dir" context_has SessionStart "account=$W/home/.claude-second "
  printf 'fable\n' >"$W/home/.claude-second/model-mesh-tier"; printf 'standard\n' >"$W/cfg/model-mesh-tier"
  run "$(agent '"fable"')" CLAUDE_CONFIG_DIR="$W/home/.claude-second"; check "G3: swapped → fable allowed" silent

  # --- state line content
  reset standard
  run "$(start sess-a '"claude-sonnet-5-5"')"
  check "state: standard seat matches, no MISMATCH" context_has SessionStart "tier=standard" "expected-seat=sonnet" "session-model=claude-sonnet-5-5"
  check "state: … and no MISMATCH / FABLE-TIER" context_lacks MISMATCH FABLE-TIER
  run "$(start sess-b)"
  check "state: no model → unknown, (b) suspended" context_has SessionStart "session-model=unknown → (b) suspended, same-model rules apply"
  run "$(start sess-c '"claude-fable-5-1"')"
  check "state: standard tier on Fable → warning + MISMATCH" context_has SessionStart "[SESSION ON FABLE, tier ≠ fable: switch now]" "[MISMATCH: /model → sonnet → press s; permanent: "
  run "$(start sess-d '"claude-opus-5-5"')" ANTHROPIC_DEFAULT_OPUS_MODEL=claude-opus-5-5
  check "state: ALIAS REMAP shown" context_has SessionStart "[ALIAS REMAP: ANTHROPIC_DEFAULT_OPUS_MODEL=claude-opus-5-5]"
  run "$(start sess-e '"claude-sonnet-5-5"')" CLAUDE_CONFIG_DIR="$W/home/.claude-work"
  check "state: unreadable account dir flagged" context_has SessionStart "[ACCOUNT DIR UNREADABLE"
  printf 'MM_SEAT_STANDARD=opus\n' >"$W/mmhome/models.env"
  run "$(start sess-f '"claude-opus-5-5"')"
  check "state: expected seat read from models.env" context_has SessionStart "expected-seat=opus "
  check "state: … and opus session is not a MISMATCH" context_lacks MISMATCH
  reset fable
  run "$(start sess-g '"claude-opus-5-5"')"
  check "state: fable tier → FABLE-TIER, seat opus" context_has SessionStart "tier=fable" "expected-seat=opus" "[FABLE-TIER: pass model: fable to mesh-expert and mesh-judge r3]"
  check "state: … no SESSION ON FABLE" context_lacks "SESSION ON FABLE" MISMATCH

  # --- v2.7: fable-via= on a standard-tier session (first listed account dir whose tier file is exactly fable)
  reset standard
  mkdir -p "$W/acc-std" "$W/acc-fab1" "$W/acc-fab2" "$W/acc-loose"
  printf 'standard\n' >"$W/acc-std/model-mesh-tier"; printf 'fable\n' >"$W/acc-fab1/model-mesh-tier"
  printf 'fable' >"$W/acc-fab2/model-mesh-tier"; printf 'fable \n' >"$W/acc-loose/model-mesh-tier"
  run "$(start sess-v1 '"claude-sonnet-5-5"')"
  check "fable-via: no claude-reviewer file → fable-via=none" context_has SessionStart "tier=standard" " fable-via=none"
  printf '%s\n%s\n%s\n%s\n' "$W/acc-std" "$W/acc-loose" "$W/acc-fab2" "$W/acc-fab1" >"$W/mmhome/claude-reviewer"
  run "$(start sess-v2 '"claude-sonnet-5-5"')"
  check "fable-via: first listed dir with a tier file of exactly fable (skips standard and 'fable ') → its basename" context_has SessionStart "fable-via=acc-fab2"
  printf '%s\n%s\n' "$W/acc-std" "$W/acc-loose" >"$W/mmhome/claude-reviewer"
  run "$(start sess-v3 '"claude-sonnet-5-5"')"
  check "fable-via: listed dirs but none exactly fable → fable-via=none" context_has SessionStart "fable-via=none"
  printf '%s\n' "$W/no-such-dir" >"$W/mmhome/claude-reviewer"
  run "$(start sess-v4 '"claude-sonnet-5-5"')"
  check "fable-via: a listed dir that does not exist → fable-via=none" context_has SessionStart "fable-via=none"
  printf '%s\n' "$W/acc-fab1" >"$W/mmhome/claude-reviewer"
  reset fable
  printf '%s\n' "$W/acc-fab1" >"$W/mmhome/claude-reviewer"
  mkdir -p "$W/acc-fab1"; printf 'fable\n' >"$W/acc-fab1/model-mesh-tier"
  run "$(start sess-v5 '"claude-opus-5-5"')"
  check "fable-via: nothing added on a fable-tier session" context_lacks "fable-via"

  # --- v2.5 P9: SubagentStop -> one usage.log line from the sub-agent transcript (fixture transcripts)
  reset standard
  T1="$W/home/agent-a1.jsonl"
  { printf '%s\n' '{"type":"user","message":{"role":"user","content":"go"}}'
    printf '%s\n' '{"type":"assistant","message":{"id":"msg_1","role":"assistant","model":"claude-haiku-4-5-20251001","usage":{"input_tokens":10,"cache_creation_input_tokens":100,"cache_read_input_tokens":1000,"output_tokens":5}}}'
    printf '%s\n' '{"type":"assistant","message":{"id":"msg_1","role":"assistant","model":"claude-haiku-4-5-20251001","usage":{"input_tokens":10,"cache_creation_input_tokens":100,"cache_read_input_tokens":1000,"output_tokens":5}}}'
    printf '%s\n' '{"type":"assistant","message":{"id":"msg_2","role":"assistant","model":"claude-haiku-4-5-20251001","usage":{"input_tokens":3,"cache_creation_input_tokens":0,"cache_read_input_tokens":1110,"output_tokens":40}}}'
  } >"$T1"
  run "$(substop "$T1")"
  if [ "$MODE" = py ]; then
    check "usage: normal transcript → one line, model and four sums (a message split over two lines counted once)" ulog_last "agent=mesh-lookup" "model=claude-haiku-4-5-20251001" "input=13 cache_creation=100 cache_read=2110 output=45"
  else
    check "usage: no python3 → usage unknown, never a failed hook" ulog_last "agent=mesh-lookup" "usage unknown (no python3)"
  fi
  reset standard
  : >"$W/home/empty.jsonl"
  run "$(substop "$W/home/empty.jsonl")"
  if [ "$MODE" = py ]; then check "usage: empty transcript → usage unknown" ulog_last "usage unknown (no assistant usage"
  else check "usage: empty transcript (no python3) → usage unknown" ulog_last "usage unknown"; fi
  reset standard
  run "$(substop "$W/home/no-such.jsonl")"
  check "usage: missing transcript path → usage unknown, silent, exit 0" ulog_last "usage unknown (transcript absent or unreadable)"
  reset standard
  run "$(substop '')"
  check "usage: no agent_transcript_path in the input → usage unknown" ulog_last "usage unknown (no agent_transcript_path"
  reset standard
  printf '%s\n' '{"truncated' 'garbage' >"$W/home/bad.jsonl"
  run "$(substop "$W/home/bad.jsonl")"
  if [ "$MODE" = py ]; then
    check "usage: malformed JSON transcript → usage unknown (malformed lines counted)" ulog_last "usage unknown (no assistant usage in the transcript (2 malformed lines))"
    reset standard
    { printf '%s\n' 'not json at all {'
      printf '%s\n' '{"type":"assistant","message":{"id":"msg_9","role":"assistant","model":"claude-haiku-4-5-20251001","usage":{"input_tokens":7,"cache_creation_input_tokens":1,"cache_read_input_tokens":2,"output_tokens":3}}}'
    } >"$W/home/mixed.jsonl"
    run "$(substop "$W/home/mixed.jsonl")"
    check "usage: one malformed line among good ones → sums plus skipped_lines=1" ulog_last "input=7 cache_creation=1 cache_read=2 output=3 skipped_lines=1"
  else
    check "usage: malformed JSON transcript (no python3) → usage unknown" ulog_last "usage unknown"
  fi
  reset standard
  run "$(substop "$T1")"; run "$(substop "$T1")"
  check "usage: each SubagentStop appends one line" sh -c "[ \"\$(wc -l < '$W/mmhome/journal/usage.log' | tr -d ' ')\" = 2 ]"
  reset standard
  run "$(agent '"sonnet"')"; run "$(prompt sess-u p-u1)"; run "$(switch '"opus"' PostModelSwitch)"
  check "usage: other events never write usage.log" no_ulog

  # PostModelSwitch caches to_model; the next prompt's state line shows it (G4 at hook level)
  reset standard
  run "$(start sess-h '"claude-sonnet-5-5"')";  check "G4: SessionStart caches model" context_has SessionStart "session-model=claude-sonnet-5-5"
  run "$(switch '"claude-opus-5-5"' PostModelSwitch | sed 's/"sess-1"/"sess-h"/')"; check "G4: PostModelSwitch prints nothing" silent
  run "$(prompt sess-h p-h1)";                  check "G4: next state line shows the switched model" context_has UserPromptSubmit "session-model=claude-opus-5-5" "[MISMATCH: /model → sonnet"

  # dedupe: state line once per occurrence with two hook sources (user + project copies)
  reset standard
  mkdir -p "$ROOT/hook2"; cp "$HOOK" "$ROOT/hook2/model-mesh-hook.sh"; chmod +x "$ROOT/hook2/model-mesh-hook.sh"
  local n
  n=0
  run "$(start sess-dup '"claude-sonnet-5-5"')"; [ -n "$OUT" ] && n=$((n + 1)); context_has SessionStart || n=99
  HOOK_OVERRIDE="$ROOT/hook2/model-mesh-hook.sh" run "$(start sess-dup '"claude-sonnet-5-5"')"; [ -n "$OUT" ] && n=$((n + 1))
  OUT="$n"; check "dedupe: SessionStart printed once with two hook sources" test "$n" = 1
  n=0
  run "$(prompt sess-dup p-1)"; [ -n "$OUT" ] && n=$((n + 1))
  HOOK_OVERRIDE="$ROOT/hook2/model-mesh-hook.sh" run "$(prompt sess-dup p-1)"; [ -n "$OUT" ] && n=$((n + 1))
  OUT="$n"; check "dedupe: UserPromptSubmit printed once per prompt_id with two sources" test "$n" = 1
  run "$(prompt sess-dup p-2)"; check "dedupe: next prompt_id printed again" context_has UserPromptSubmit "session-model=claude-sonnet-5-5"
  n=0
  run "$(prompt sess-dup)"; [ -n "$OUT" ] && n=$((n + 1))
  HOOK_OVERRIDE="$ROOT/hook2/model-mesh-hook.sh" run "$(prompt sess-dup)"; [ -n "$OUT" ] && n=$((n + 1))
  OUT="$n"; check "dedupe: no prompt_id → printed once with two sources" test "$n" = 1
  printf 'x\n' >>"$W/transcript.jsonl"
  run "$(prompt sess-dup)"; check "dedupe: no prompt_id, later turn → printed again" context_has UserPromptSubmit
  run "$(prompt '../../escape' p-9)"; check "cache: hostile session_id → no cache, still prints" context_has UserPromptSubmit
  [ ! -e "$W/escape" ] && [ ! -e "$ROOT/escape" ]; OUT=''; check "cache: hostile session_id wrote nothing outside" test ! -e "$W/escape"

  # log line
  reset standard
  rm -f "$W/hook.log"
  run "$(agent '"fable"')"
  local last
  last=$(tail -n 1 "$W/hook.log" 2>/dev/null)
  OUT=$last
  check "log: one line per decision in the pinned format" \
    eval 'case $last in "PreToolUse effective=fable via=step2 decision=deny reason=model-mesh: "*) true ;; *) false ;; esac'
  run "$(agent '"sonnet"')"
  last=$(tail -n 1 "$W/hook.log" 2>/dev/null); OUT=$last
  check "log: allow line" eval 'case $last in "PreToolUse effective=sonnet via=step2 decision=allow reason="*) true ;; *) false ;; esac'
}

no_parser_suite() {
  MODE=no-parser
  PMODE='MM_TEST_NO_PYTHON=1 MM_TEST_NO_JQ=1'
  reset standard
  run "$(agent '"sonnet"')";          check "deny: no parser → spawn with sonnet denied" deny_tool
  reset fable
  run "$(agent '"sonnet"')";          check "deny: no parser → denied on tier fable too" deny_tool
  run "$(switch '"opus"')";           check "deny: no parser → PreModelSwitch denied (standard fail-closed)" deny_switch
  run "$(start sess-np '"claude-opus-5-5"')"; check "state: no parser → warning context" context_has SessionStart "[NO JSON PARSER"
  run "$(prompt sess-np p-1)";        check "state: no parser → UserPromptSubmit warning" context_has UserPromptSubmit "[NO JSON PARSER"
  run "$(pre '{"subagent_type":"x","prompt":"\"hook_event_name\":\"UserPromptSubmit\""}')"
  check "deny: no parser, spoofed event name in a string → still denied" deny_tool
  run "$(switch '"opus"' PostModelSwitch)"; check "pass: no parser → PostModelSwitch silent" silent
  reset standard
  run "$(substop "$W/home/x.jsonl")"; check "usage: no parser → usage unknown line, silent" ulog_last "usage unknown (no JSON parser)"
  # seams are ignored without MODEL_MESH_TEST=1
  MODE=seams
  reset standard
  run "$(agent '"sonnet"')" MODEL_MESH_TEST=0
  if [ "$HAVE_PY" = 1 ] || [ "$HAVE_JQ" = 1 ]; then
    check "seam: NO_PYTHON/NO_JQ ignored unless MODEL_MESH_TEST=1" silent
  else skip "seam check (no parser on this machine)"; fi
  printf '{"env":' >"$W/managed.json"
  run "$(agent '"sonnet"')"
  check "seam: MM_TEST_MANAGED_SETTINGS honoured under MODEL_MESH_TEST=1" deny_tool
  PMODE=MM_TEST_PARSER_NOTE=none
  run "$(agent '"sonnet"')" MODEL_MESH_TEST=0
  if [ -e '/Library/Application Support/ClaudeCode/managed-settings.json' ] || [ -e /etc/claude-code/managed-settings.json ]; then
    skip "seam: managed path ignored without test mode (a real managed-settings file exists)"
  elif [ "$HAVE_PY" = 1 ] || [ "$HAVE_JQ" = 1 ]; then
    check "seam: MM_TEST_MANAGED_SETTINGS ignored unless MODEL_MESH_TEST=1" silent
  fi
}

echo "test-hook: $HOOK (runner: ${RUNNER:-shebang})"
if [ "$HAVE_PY" = 1 ]; then
  MODE=py; PMODE=MM_TEST_PARSER_NOTE=py; suite
else
  echo "SKIP [py] python3 not installed — suite runs on jq only"; SKIP=$((SKIP + 1))
fi
if [ "$HAVE_JQ" = 1 ]; then
  MODE=jq; PMODE=MM_TEST_NO_PYTHON=1; suite
else
  echo "SKIP [jq] jq not installed — no-python seam not exercised"; SKIP=$((SKIP + 1))
fi
no_parser_suite

echo "test-hook: $PASS passed, $FAIL failed, $SKIP skipped"
[ "$FAIL" = 0 ] && [ "$PASS" -gt 0 ]
