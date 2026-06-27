# ADR-0009: No External YAML Library — Custom Parser

**Status:** Accepted  
**Date:** 2026-06-27

## Context

The Rule Engine reads rule definitions from YAML files. The standard choice would be an existing YAML library (`js-yaml`, `yaml`, etc.). However, adding any library inflates `pnpm-lock.yaml` and `package.json`, which requires lockfile review and creates a transitive dependency surface.

The rule files use a small, well-defined subset of YAML:
- Nested objects (indentation-based)
- String, number, and boolean scalars
- Lists of objects or scalars
- Double-quoted strings with escape sequences (needed for regex patterns containing colons)

## Decision

A custom YAML parser was implemented in `scripts/lib/yaml.ts`. It handles only the subset of YAML used in rule files. It supports:
- Object nesting by indentation
- Scalar types: string, number, boolean
- List items (plain values and inline objects)
- Double-quoted strings with `\\`, `\n`, `\t`, `\r`, `\b` escape decoding
- Fully-quoted list values (strings containing colons are not split as objects)

## Consequences

**Positive:**
- Zero additional dependencies
- `pnpm-lock.yaml` is not inflated
- No transitive dependency vulnerabilities from a YAML library
- The parser is ~100 lines and entirely auditable

**Negative:**
- Does not support the full YAML 1.2 spec
- Unsupported features: multi-line strings (`|`, `>`), anchors (`&`) and aliases (`*`), flow sequences (`[a, b, c]`), flow mappings (`{key: val}`), YAML directives
- Rule files must stay within supported constructs
- If rule files ever require advanced YAML features, a full parser must be substituted

**Invariant:** If rule file complexity grows to require full YAML support, replace `scripts/lib/yaml.ts` with a library import — do not extend the custom parser further.
