#!/usr/bin/env bash
# Rebuilds RiftArena.rbxlx from the source files:
#   1. runs the project checks (compiles every file, validates hero data, builds the map)
#   2. bakes the arena into map/Map.rbxm
#   3. builds the place file with Rojo
# Needs Rojo (https://rojo.space) and Lune (https://lune-org.github.io/docs) on your PATH.
set -euo pipefail
cd "$(dirname "$0")/.."

lune run tools/check
lune run tools/bake-map
rojo build default.project.json -o RiftArena.rbxlx
echo "Built RiftArena.rbxlx"
