.PHONY: verify unit mcp sim test console console-test stats local-chat local-mcp down clean

verify:
	./scripts/verify-environment.sh

unit:
	./scripts/test-unit.sh

mcp:
	./scripts/test-mcp.sh

sim:
	./scripts/test-sim.sh

test:
	./scripts/test-all.sh

console:
	./scripts/llmsim-console.sh

console-test:
	./scripts/test-sim-console.sh

stats:
	./scripts/llmsim-stats.sh

local-chat:
	./scripts/local-chat.sh

local-mcp:
	./scripts/local-mcp.sh

down:
	./scripts/down.sh

clean:
	./scripts/force-clean.sh
