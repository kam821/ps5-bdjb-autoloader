#!/usr/bin/env bash
# Script to download dependency binaries from GitHub releases

set -euo pipefail

# Ensure we are in the project root
cd "$(dirname "$0")/.."

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --elfldr-ver) ELFLDR_VER="$2"; shift ;;
        --kexp-ver) KEXP_VER="$2"; shift ;;
        --pldmgr-ver) PLDMGR_VER="$2"; shift ;;
        *) echo "Unknown parameter passed: $1" ;;
    esac
    shift
done

ELFLDR_GITHUB_RELEASE="${ELFLDR_VER:-latest}"
KEXP_GITHUB_RELEASE="${KEXP_VER:-latest}"
PLDMGR_GITHUB_RELEASE="${PLDMGR_VER:-latest}"

DEST_DIR="payloads/poops/src/org/bdj/external"
mkdir -p "$DEST_DIR"

AUTOLOADER_DIR="ps5_autoloader"
mkdir -p "$AUTOLOADER_DIR"

echo "Checking for curl..."
if ! command -v curl &> /dev/null; then
    echo "Error: curl is required to download dependencies." >&2
    exit 1
fi

echo "Fetching $ELFLDR_GITHUB_RELEASE release URL for ps5-elfldr..."
ELFLDR_URL=$(curl -s https://api.github.com/repos/itsPLK/ps5-elfldr/releases/$ELFLDR_GITHUB_RELEASE | grep -o 'https://github.com/itsPLK/ps5-elfldr/releases/download/[^"]*\.elf' | head -n 1)
if [ -z "$ELFLDR_URL" ]; then
    echo "Error: Could not retrieve latest release URL for ps5-elfldr." >&2
    exit 1
fi
ELFLDR_FILE=$(basename "$ELFLDR_URL")

echo "Fetching $KEXP_GITHUB_RELEASE release URL for ps5-kexp..."
KEXP_URL=$(curl -s https://api.github.com/repos/itsPLK/ps5-kexp/releases/$KEXP_GITHUB_RELEASE | grep -o 'https://github.com/itsPLK/ps5-kexp/releases/download/[^"]*\.bin' | head -n 1)
if [ -z "$KEXP_URL" ]; then
    echo "Error: Could not retrieve latest release URL for ps5-kexp." >&2
    exit 1
fi
KEXP_FILE=$(basename "$KEXP_URL")

echo "Fetching $PLDMGR_GITHUB_RELEASE release URL for ps5-payload-manager..."
PLDMGR_URL=$(curl -s https://api.github.com/repos/itsPLK/ps5-payload-manager/releases/$PLDMGR_GITHUB_RELEASE | grep -o 'https://github.com/itsPLK/ps5-payload-manager/releases/download/[^"]*\.elf' | head -n 1)
if [ -z "$PLDMGR_URL" ]; then
    echo "Error: Could not retrieve latest release URL for ps5-payload-manager." >&2
    exit 1
fi
PLDMGR_FILE=$(basename "$PLDMGR_URL")

# Clean old dependency files
echo "Cleaning old binaries from $DEST_DIR..."
rm -f "$DEST_DIR"/kexp-*.bin
rm -f "$DEST_DIR"/elfldr-*.elf
rm -f "$DEST_DIR"/kexp_v6.bin
rm -f "$DEST_DIR"/elfldr.elf

echo "Cleaning old payload manager binaries from $AUTOLOADER_DIR..."
rm -f "$AUTOLOADER_DIR"/pldmgr-*.elf
rm -f "$AUTOLOADER_DIR"/pldmgr_v*.elf

# Download assets
echo "Downloading $ELFLDR_FILE..."
curl -L -o "$DEST_DIR/$ELFLDR_FILE" "$ELFLDR_URL"

echo "Downloading $KEXP_FILE..."
curl -L -o "$DEST_DIR/$KEXP_FILE" "$KEXP_URL"

echo "Downloading $PLDMGR_FILE..."
curl -L -o "$AUTOLOADER_DIR/$PLDMGR_FILE" "$PLDMGR_URL"

echo "Generating autoload.txt with $PLDMGR_FILE..."
cat << EOF > "$AUTOLOADER_DIR/autoload.txt"
#
# ps5_autoloader
# autoload config file
# -----------------------------------------------------------------------------------------
# The loader looks for ps5_autoloader/autoload.txt in this order (highest priority first):
# 1) USB drives
# 2) /data directory
# 3) BD Disc
# Only the first autoload.txt found will be used.
#
# Usage:
# - Put one filename per line (e.g., payload.elf).
# - Supported payload types: .elf, .bin, .jar
# - Lines starting with '!' are sleep commands (example: !1000 sleeps for 1000 ms).
#
# Notes:
# - The kernel exploit will start automatically - do NOT include it here!
# - You can use custom elf loader by putting it here and adding
#   elfldr.elf (must be that filename!) line before other ELFs.
# -----------------------------------------------------------------------------------------

$PLDMGR_FILE
EOF

echo "Successfully downloaded all dependencies!"
ls -la "$DEST_DIR"
ls -la "$AUTOLOADER_DIR"

