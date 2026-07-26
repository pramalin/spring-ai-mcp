#!/usr/bin/env sh
set -eu
. "$(dirname "$0")/common.sh"

"$project_dir/scripts/test-unit.sh"
"$project_dir/scripts/test-mcp.sh"
"$project_dir/scripts/test-sim.sh"
