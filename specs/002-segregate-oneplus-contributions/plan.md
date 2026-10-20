# Implementation Plan: Segregate OnePlus Y Series Contributions per CONTRIBUTING Guidelines

**Branch**: `002-segregate-oneplus-contributions` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from [`specs/002-segregate-oneplus-contributions/spec.md`](./spec.md)

---

## Summary

The objective of this plan is to segregate the changes made for the OnePlus Y Series 43" TV so they conform to the open-source repository guidelines in `CONTRIBUTING.md`. Per the latest clarification, the directory `examples/oneplus-tv-y1/` will serve as a complete, self-contained consumer hub containing `debloat.txt`, a dedicated consumer guide in `README.md`, a one-command `restore.sh` script, and visual reference `screenshot.png`. Shared root tools in `tools/` remain 100% brand-neutral, all developer automation and backup scripts in `scripts/` and `backup/` remain 100% preserved without data loss, and the repository is completely ready to merge or create upstream PRs without destructive regressions.

---

## Technical Context

**Language/Version**: Bash / POSIX Shell, Python 3.10+, Markdown

**Primary Dependencies**: `git`, standard Unix text utilities (`grep`, `awk`, `sed`)

**Storage**: Local git working tree and `.gitignore`

**Testing**: Structural audit, shell syntax verification (`bash -n`), git tracking inspection (`git ls-files`)

**Target Platform**: Universal / Cross-Platform (Linux, macOS, Windows)

**Project Type**: Open-Source Packaging, Documentation Refactoring & Git Hygiene

**Performance Goals**: Zero performance impact; clean repository structure

**Constraints**:
- Strictly non-destructive: no deletion of working scripts, backups, or custom launcher configuration
- `examples/oneplus-tv-y1/` acts as a self-contained consumer hub with all necessary files
- Root `tools/` must remain brand-agnostic
- Upstream PR scope must only touch model package (`examples/oneplus-tv-y1/`), catalogs (`examples/README.md`, `README.md`), and generic build enhancements
- Git index must be purged of compiled bytecode (`.pyc`)

**Scale/Scope**: 1 model package, 2 documentation catalogs, 3 script relocations, `.gitignore` updates

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Non-Destructive Operations**: PASS. No working tools, scripts, or backups are deleted.
- **Upstream Compliance**: PASS. Conforms directly to `CONTRIBUTING.md` standards for adding a TV model.
- **Consumer Usability**: PASS. Dedicated `README.md` and `restore.sh` in `examples/oneplus-tv-y1/` ensure consumers can easily configure and restore their TV.
- **Brand Neutrality**: PASS. Shared tools accept arguments and configuration; model-specific scripts are moved out of root `tools/`.
- **Git Hygiene**: PASS. Unwanted bytecode caches are removed from tracking; local backups remain untracked.

---

## Project Structure

### Documentation (this feature)

```text
specs/002-segregate-oneplus-contributions/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: upstream standards and segregation tiers
├── data-model.md        # Phase 1 output: entities and relocation mapping
├── quickstart.md        # Phase 1 output: step-by-step verification guide
├── contracts/           # Phase 1 output: contribution and PR contracts
│   └── contribution-contract.md
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code Relocation Plan

```text
tv-vibehack/
├── examples/
│   ├── README.md                           # Add row for OnePlus Y1 (shibuya, Android 11)
│   └── oneplus-tv-y1/                      # Self-Contained Consumer Model Hub
│       ├── README.md                       # Consumer guide for OnePlus Y1 setup, debloat & restore
│       ├── debloat.txt                     # Formatted with standard CONTRIBUTING.md header
│       ├── restore.sh                      # Dedicated restore helper
│       ├── screenshot.png                  # Clean home screen reference image
│       ├── classify_packages.py            # OnePlus Y1 package classifier & audit tool
│       └── generate_restore_script.sh      # Customized restore generator for OnePlus Y1
├── tools/                                  # 100% Brand-Neutral Shared Tools
│   ├── tv.py                               # Core Python ADB client
│   ├── b64push.py                          # Base64 chunked transfer
│   ├── disable.sh                          # Generic batch debloat tool
│   ├── restore.sh                          # Generic batch restore tool
│   ├── backup.sh                           # Generic baseline device backup tool
│   ├── screenshot.sh                       # Generic screenshot capture & verification suite
│   ├── check_debloat.sh                    # Generic debloat profile validator & linter
│   └── requirements.txt
├── launcher/
│   ├── setup-linux.sh                      # Generic Linux JDK 17 & Android SDK 34 environment setup
│   ├── build-and-install.sh                # Retains generic Linux JDK/SDK path detection
│   └── gradle/wrapper/
│       └── gradle-wrapper.properties       # Retains generic timeout/retry resilience
├── backup/                                 # Preserved local device dumps (untracked)
├── screenshots/                            # Preserved local capture suite (untracked)
└── .gitignore                              # Cleaned up (ignores .pyc, backup/, screenshots/)
```

---

## Complexity Tracking

> No constitution violations or unjustified architectural complexity. All changes are structural segregations following existing repository patterns.
