#!/usr/bin/env bash
# Lab 7, steps 5 and 6: check that LM Studio serves the model and answers a chat completion.
# Usage: ./scripts/check-local-llm.sh [base-url] [model]
set -euo pipefail

BASE_URL="${1:-http://localhost:12345}"
MODEL="${2:-google/gemma-3-270m}"

echo "1) GET $BASE_URL/v1/models"
if ! models="$(curl -fsS --max-time 5 "$BASE_URL/v1/models")"; then
  echo "LM Studio is not reachable at $BASE_URL. Start its server on port 12345 and retry." >&2
  exit 1
fi
echo "$models"
if ! grep -q "\"$MODEL\"" <<<"$models"; then
  echo "Model '$MODEL' is not listed. Download it with: lms get $MODEL" >&2
  exit 1
fi

echo
echo "2) POST $BASE_URL/v1/chat/completions"
curl -fsS --max-time 120 -X POST "$BASE_URL/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"system\",\"content\":\"Always reply with the user's message, but rewritten in a more funny style.\"},{\"role\":\"user\",\"content\":\"Today I'm at my 7th Software Engineering lab.\"}],\"temperature\":0.7,\"max_tokens\":256,\"stream\":false}"
echo
echo "OK: the model answered. Look for choices[0].message.content above."
