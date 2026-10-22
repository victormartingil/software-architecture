SHELL := /bin/bash
UV ?= uv
KIND_BIN ?= $(if $(wildcard reports/tools/kind),$(CURDIR)/reports/tools/kind,kind)
COMPOSE := docker compose
.PHONY: up down reset demo smoke test eval docs verify observability observability-smoke observability-rules-test kafka-summary terraform-check kind-up kind-down kind-smoke kind-scale-check
up:
	$(COMPOSE) up -d --build --wait --wait-timeout 240
down:
	$(COMPOSE) --profile observability down
reset:
	@test "$(CONFIRM)" = "delete-local-data" || (echo 'Use make reset CONFIRM=delete-local-data'; exit 1)
	$(COMPOSE) --profile observability down -v
demo smoke:
	python3 scenarios/smoke.py
kind-smoke:
	SCENARIO_RUNTIME=kind python3 scenarios/smoke.py
test:
	UV=$(UV) ./scripts/test.sh
eval: export EVAL_REPORT := $(EVAL_REPORT)
eval:
	cd apps/ai-service && $(UV) run python -m ai_service.evaluate
docs:
	$(UV) run --project tools/docs mkdocs serve -f mkdocs.yml
verify: test observability-rules-test
	python3 scripts/check_repository.py
	$(UV) run --project tools/docs mkdocs build --strict -f mkdocs.yml
	python3 scripts/check_doc_navigation.py
observability:
	TRACING_ENABLED=true TRACING_SAMPLING_PROBABILITY=1.0 AI_OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 $(COMPOSE) --profile observability up -d --wait --wait-timeout 240
observability-rules-test:
	python3 scripts/observability-rules-test.py
observability-smoke:
	python3 scenarios/observability.py
kafka-summary:
	@test -n "$(ORDER_ID)" || (echo 'Use make kafka-summary ORDER_ID=<order UUID printed by make demo>'; exit 1)
	python3 scenarios/event_flow.py --order-id "$(ORDER_ID)"
terraform-check:
	./scripts/terraform-check.sh
kind-up:
	KIND_BIN="$(KIND_BIN)" ./scripts/kind.sh up
kind-scale-check:
	python3 scripts/kind-scale-check.py
kind-down:
	KIND_BIN="$(KIND_BIN)" ./scripts/kind.sh down
