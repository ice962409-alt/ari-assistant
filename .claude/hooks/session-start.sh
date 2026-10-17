#!/bin/bash
# Al empezar una sesión de Claude Code en la nube: instala graphify (versión fija, con hashes
# comprobados) y arma el mapa del código de ARI (app/src/graphify-out, no se sube a git).
# Nunca hace fallar la sesión: si algo no se puede, sigue sin graphify.
set -uo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

# Sin nube: graphify nunca manda código a un modelo ni lanza otros "claude -p".
export GRAPHIFY_BACKEND=none GRAPHIFY_NO_AUTO_REFRESH=1 GRAPHIFY_NO_TIPS=1 GRAPHIFY_NO_CTA=1
V="$HOME/.graphify-venv"
if [ ! -x "$V/bin/graphify" ]; then
  python3 -m venv "$V" || exit 0
  "$V/bin/pip" install --quiet --disable-pip-version-check --no-input --require-hashes \
    --only-binary=:all: --no-deps -r "$CLAUDE_PROJECT_DIR/.claude/graphify-requirements.txt" >/dev/null 2>&1 || exit 0
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export PATH=\"$V/bin:\$PATH\"" >> "$CLAUDE_ENV_FILE"
fi
cd "$CLAUDE_PROJECT_DIR" && { "$V/bin/graphify" update app/src >/dev/null 2>&1 || true; }
exit 0
