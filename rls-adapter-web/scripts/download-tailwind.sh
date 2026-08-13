#!/usr/bin/env bash
# Downloads the standalone Tailwind CSS CLI binary for Linux/macOS into rls-adapter-web/bin/.
# Idempotent: does nothing if the binary is already present. No Node/npm required.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$SCRIPT_DIR/../bin"
BIN_PATH="$BIN_DIR/tailwindcss"

if [ -f "$BIN_PATH" ]; then
    exit 0
fi

mkdir -p "$BIN_DIR"

OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Linux)
        case "$ARCH" in
            x86_64) ASSET="tailwindcss-linux-x64" ;;
            aarch64|arm64) ASSET="tailwindcss-linux-arm64" ;;
            *) echo "Unsupported Linux architecture: $ARCH" >&2; exit 1 ;;
        esac
        ;;
    Darwin)
        case "$ARCH" in
            x86_64) ASSET="tailwindcss-macos-x64" ;;
            arm64) ASSET="tailwindcss-macos-arm64" ;;
            *) echo "Unsupported macOS architecture: $ARCH" >&2; exit 1 ;;
        esac
        ;;
    *)
        echo "Unsupported OS: $OS" >&2
        exit 1
        ;;
esac

URL="https://github.com/tailwindlabs/tailwindcss/releases/latest/download/${ASSET}"

echo "Downloading Tailwind CLI ($ASSET)..."
curl -fsSL -o "$BIN_PATH" "$URL"
chmod +x "$BIN_PATH"
