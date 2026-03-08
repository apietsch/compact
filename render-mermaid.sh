#!/usr/bin/env sh
set -eu

usage() {
  cat <<'EOF'
Usage:
  ./render-mermaid.sh <input.mmd> <svg|jpg> [output-file]

Examples:
  ./render-mermaid.sh C4_CONTEXT.mmd svg
  ./render-mermaid.sh C4_CONTAINER.mmd jpg
  ./render-mermaid.sh C4_CONTEXT.mmd jpg diagrams/context.jpg
EOF
}

if [ "${1-}" = "" ] || [ "${2-}" = "" ]; then
  usage
  exit 1
fi

INPUT="$1"
FORMAT="$2"
OUTPUT="${3-}"

if [ ! -f "$INPUT" ]; then
  echo "Input file not found: $INPUT" >&2
  exit 1
fi

if [ "$FORMAT" != "svg" ] && [ "$FORMAT" != "jpg" ]; then
  echo "Unsupported format: $FORMAT (use svg or jpg)" >&2
  exit 1
fi

if [ "$OUTPUT" = "" ]; then
  BASENAME="${INPUT%.*}"
  OUTPUT="${BASENAME}.${FORMAT}"
fi

WORKDIR="$(pwd)"
INPUT_ABS="${WORKDIR}/${INPUT}"
OUTPUT_ABS="${WORKDIR}/${OUTPUT}"
OUTPUT_DIR="$(dirname "$OUTPUT")"
mkdir -p "$OUTPUT_DIR"

if [ "$FORMAT" = "svg" ]; then
  docker run --rm -u "$(id -u):$(id -g)" \
    -v "${WORKDIR}:/data" \
    minlag/mermaid-cli \
    -i "/data/${INPUT}" -o "/data/${OUTPUT}"
  echo "Created: ${OUTPUT_ABS}"
  exit 0
fi

TMP_PNG="${OUTPUT%.*}.png"
TMP_PNG_ABS="${WORKDIR}/${TMP_PNG}"

docker run --rm -u "$(id -u):$(id -g)" \
  -v "${WORKDIR}:/data" \
  minlag/mermaid-cli \
  -i "/data/${INPUT}" -o "/data/${TMP_PNG}"

docker run --rm -u "$(id -u):$(id -g)" \
  -v "${WORKDIR}:/work" \
  dpokidov/imagemagick \
  "/work/${TMP_PNG}" "/work/${OUTPUT}"

rm -f "${TMP_PNG}"
echo "Created: ${OUTPUT_ABS}"
