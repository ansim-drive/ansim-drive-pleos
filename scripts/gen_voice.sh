#!/bin/bash
# docs/voice-lines.md 표의 문장을 macOS 음성 합성(say)으로 만들어 app/src/main/res/raw/<파일명>.m4a로 넣는다.
# 시연 영상용 임시 음성이며 배포용이 아니다. 생성된 파일은 .gitignore 대상(app/src/main/res/raw/voice_*)이다.
# usage: scripts/gen_voice.sh            (기본 음성 Yuna)
#        VOICE=Eddy scripts/gen_voice.sh
set -euo pipefail
cd "$(dirname "$0")/.."

VOICE=${VOICE:-Yuna}
OUT=app/src/main/res/raw
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

count=0
# 표의 행 형식: | `voice_xxx` | 문장 | 언제 |
while IFS='|' read -r _ name text _; do
  name=$(echo "$name" | tr -d ' `')
  text=$(echo "$text" | sed 's/^ *//; s/ *$//')
  if [[ ! "$name" =~ ^[a-z0-9_]+$ ]]; then
    echo "파일명 오류: '$name' (영문 소문자·숫자·밑줄만)" >&2
    exit 1
  fi
  rm -f "$OUT/$name".*   # 확장자만 다른 같은 이름이 있으면 빌드가 실패하므로 지운다
  say -v "$VOICE" -o "$TMP/$name.aiff" "$text"
  afconvert -f m4af -d aac "$TMP/$name.aiff" "$OUT/$name.m4a"
  echo "$OUT/$name.m4a  <- $text"
  count=$((count + 1))
done < <(grep -E '^\| `voice_[a-z0-9_]+` \|' docs/voice-lines.md)

echo "${count}개 생성 (음성: ${VOICE})"
