#!/usr/bin/env python3
"""Merge per-service springdoc OpenAPI specs into one public contract (code-first).

Each Spring service is the source of truth for its own API; springdoc exports a spec per
service (see OpenApiDocGenerationTest). This script joins them into a single api/openapi.yaml
that mirrors the public surface behind the gateway: each service's paths are prefixed with its
gateway route (e.g. /api/users), and component definitions are merged. Downstream consumer
clients (web-client TypeScript, gen-ai Python) are generated from the result.

Usage:
  merge-openapi.py --output api/openapi.yaml \
      services/spring/user-service/build/openapi/user-service.json:/api/users \
      services/spring/content-service/build/openapi/content-service.json:/api/content
"""
import argparse
import json
import sys

import yaml


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--title", default="Personalised News Aggregator API")
    parser.add_argument("--version", default="1.0.0")
    parser.add_argument("specs", nargs="+", metavar="spec.json:/prefix")
    args = parser.parse_args()

    merged = {
        "openapi": "3.1.0",
        "info": {"title": args.title, "version": args.version},
        "servers": [{"url": "/"}],
        "paths": {},
        "components": {},
    }

    for entry in args.specs:
        spec_file, _, prefix = entry.partition(":")
        spec = load(spec_file)
        prefix = prefix.rstrip("/")
        merged["openapi"] = spec.get("openapi", merged["openapi"])

        for path, item in (spec.get("paths") or {}).items():
            new_path = f"{prefix}{path}" if prefix else path
            if new_path in merged["paths"]:
                sys.exit(f"ERROR: path collision after prefixing: {new_path} (from {spec_file})")
            merged["paths"][new_path] = item

        # Merge every component bucket (schemas, securitySchemes, parameters, ...) generically.
        # $refs stay valid because component names are preserved as-is.
        for bucket, items in (spec.get("components") or {}).items():
            target = merged["components"].setdefault(bucket, {})
            for name, value in items.items():
                if name in target and target[name] != value:
                    sys.exit(
                        f"ERROR: '{bucket}/{name}' defined differently across services "
                        f"(conflict from {spec_file}); rename one to merge them."
                    )
                target[name] = value

    if not merged["components"]:
        del merged["components"]

    # Sort keys at every level so the output is deterministic: springdoc serializes schema
    # properties from a HashMap (unstable order), which would otherwise make the CI drift check
    # flaky. Key order is cosmetic in OpenAPI, so normalizing it is safe.
    with open(args.output, "w", encoding="utf-8") as handle:
        yaml.safe_dump(merged, handle, sort_keys=True, default_flow_style=False, width=100)

    schema_count = len(merged.get("components", {}).get("schemas", {}))
    print(
        f"merged {len(args.specs)} spec(s) -> {args.output} "
        f"({len(merged['paths'])} paths, {schema_count} schemas)"
    )


if __name__ == "__main__":
    main()
