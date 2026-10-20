# Tasks: Segregate OnePlus Y Series Contributions per CONTRIBUTING Guidelines

**Input Documents**:
- Spec: [`specs/002-segregate-oneplus-contributions/spec.md`](./spec.md)
- Plan: [`specs/002-segregate-oneplus-contributions/plan.md`](./plan.md)
- Data Model: [`specs/002-segregate-oneplus-contributions/data-model.md`](./data-model.md)
- Contracts: [`specs/002-segregate-oneplus-contributions/contracts/contribution-contract.md`](./contracts/contribution-contract.md)
- Research: [`specs/002-segregate-oneplus-contributions/research.md`](./research.md)
- Quickstart: [`specs/002-segregate-oneplus-contributions/quickstart.md`](./quickstart.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository verification and environment baseline preparation.

- [X] T001 Verify branch state and target directory structure in `examples/oneplus-tv-y1/` and `scripts/`
- [X] T002 [P] Verify executable permissions and syntax for preserved automation scripts in `scripts/backup_baseline.sh`, `scripts/generate_restore_script.sh`, `scripts/prompt_debloat_preferences.sh`, `scripts/verify_ui_screenshots.sh`, and `scripts/install-android-jdk.sh`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core git repository hygiene and ignore boundaries that MUST be in place before file relocations.

**⚠️ CRITICAL**: Must complete before user story file relocations and catalog updates.

- [X] T003 Configure repository ignore rules in `.gitignore` to exclude bytecode `*.pyc`, `**/__pycache__/`, local device backups `backup/`, and capture sessions `screenshots/`
- [X] T004 Purge tracked compiled bytecode files in `.specify/scripts/python/__pycache__/` from git index using `git rm --cached` without deleting local files

**Checkpoint**: Repository hygiene boundary established; user story work can now begin safely.

---

## Phase 3: User Story 1 - Upstream-Compliant TV Model Contribution (Priority: P1) 🎯 MVP

**Goal**: Organize the OnePlus Y1 43" debloat profile, dedicated consumer guide, one-command restore script, and reference screenshot inside `examples/oneplus-tv-y1/` per `CONTRIBUTING.md`, and update shared documentation catalogs.

**Independent Test**: Verify `examples/oneplus-tv-y1/` contains `debloat.txt` (with standard headers), `README.md`, executable `restore.sh`, and `screenshot.png`, and verify both `examples/README.md` and root `README.md` include OnePlus Y1 entries per Quickstart Scenarios 1 and 2.

### Implementation for User Story 1

- [X] T005 [P] [US1] Format model header and package category comments in `examples/oneplus-tv-y1/debloat.txt` quoting exact model "OnePlus TV (Y Series 43\", codename shibuya)", Android "11", firmware "RTO9.231122.002", Live TV app "com.mediatek.wwtv.tvcenter", and stock launcher "com.google.android.tvlauncher"
- [X] T006 [P] [US1] Copy clean home screen reference image from `screenshots/01_home.png` to `examples/oneplus-tv-y1/screenshot.png`
- [X] T007 [P] [US1] Create consumer guide `examples/oneplus-tv-y1/README.md` with hardware specifications, ADB setup, apply commands, restore instructions, launcher config, and preserved features
- [X] T008 [US1] Co-locate and make executable the dedicated model restoration script `examples/oneplus-tv-y1/restore.sh` adapted from `tools/restore_oneplus_y1.sh`
- [X] T009 [P] [US1] Add OnePlus Y1 model row to documentation catalog table in `examples/README.md`
- [X] T010 [P] [US1] Update supported TVs table and replace hardcoded `./tools/restore_oneplus_y1.sh` command in root `README.md` with pointer to `examples/oneplus-tv-y1/restore.sh`
- [X] T011 [US1] Validate User Story 1 model packaging and documentation consistency using quickstart tests in `specs/002-segregate-oneplus-contributions/quickstart.md` (Scenarios 1 & 2)

**Checkpoint**: User Story 1 is complete. The OnePlus Y1 model package is 100% self-contained and ready for open-source consumers (MVP achieved).

---

## Phase 4: User Story 2 - Brand Neutrality for Shared Core Tools (Priority: P2)

**Goal**: Ensure core tools in `tools/` and build scripts in `launcher/` remain 100% brand-agnostic, argument-driven, and non-destructive.

**Independent Test**: Audit `tools/` to verify absence of vendor-specific scripts (`restore_oneplus_y1.sh`, `classify_packages.py`), verify `scripts/classify_packages.py` is preserved, and test tool syntax (`bash -n`) per Quickstart Scenarios 3 and 5.

### Implementation for User Story 2

- [X] T012 [P] [US2] Relocate package classifier helper from `tools/classify_packages.py` to `scripts/classify_packages.py` and remove it from `tools/`
- [X] T013 [P] [US2] Remove model-specific restore script `tools/restore_oneplus_y1.sh` from root `tools/` after co-location in `examples/oneplus-tv-y1/restore.sh`
- [X] T014 [US2] Audit shared core tools `tools/disable.sh`, `tools/restore.sh`, `tools/tv.py`, and `launcher/build-and-install.sh` to ensure brand neutrality and no hardcoded OnePlus package IDs
- [X] T015 [US2] Verify generic Linux build environment paths in `launcher/build-and-install.sh` and network resilience settings in `launcher/gradle/wrapper/gradle-wrapper.properties`
- [X] T016 [US2] Validate User Story 2 brand neutrality and script syntax using quickstart checks in `specs/002-segregate-oneplus-contributions/quickstart.md` (Scenarios 3 & 5)

**Checkpoint**: User Stories 1 and 2 are both complete. Shared tools are brand-neutral and all model-specific logic is segregated.

---

## Phase 5: User Story 3 - Clean Segregation and Preservation of Working Assets (Priority: P3)

**Goal**: Ensure all code, scripts, device backups, and verification artifacts are safely preserved in dedicated locations without committing transient files or leaking sensitive TV data into upstream PRs.

**Independent Test**: Inspect `git status`, `git ls-files`, `backup/`, and `screenshots/` per Quickstart Scenario 4.

### Implementation for User Story 3

- [X] T017 [P] [US3] Verify 100% preservation and local integrity of OnePlus Y1 device backups and settings dumps in `backup/`
- [X] T018 [P] [US3] Verify 100% preservation and local integrity of full UI screenshot captures in `screenshots/`
- [X] T019 [US3] Validate git index hygiene with Quickstart Scenario 4 in `specs/002-segregate-oneplus-contributions/quickstart.md` confirming zero tracked `.pyc` files and untracked `backup/`

**Checkpoint**: All assets safely preserved locally; git tracking index is clean and upstream-ready.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final repository-wide verification and quality gate validation.

- [X] T020 Run comprehensive quickstart validation suite covering all Scenarios 1 through 5 in `specs/002-segregate-oneplus-contributions/quickstart.md`
- [X] T021 [P] Verify POSIX/Bash syntax for all modified and relocated scripts across `tools/`, `scripts/`, and `examples/oneplus-tv-y1/` using `bash -n`
- [X] T022 Review `git diff --stat` and `git status` to verify strict non-destructive compliance and upstream PR readiness

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1; establishes git ignore boundaries and purges tracked bytecode before files are touched.
- **User Story 1 (Phase 3 - P1)**: Depends on Phase 2; can proceed independently.
- **User Story 2 (Phase 4 - P2)**: Depends on Phase 2 and T008 (ensuring `restore.sh` is copied to `examples/oneplus-tv-y1/` before removing from `tools/`).
- **User Story 3 (Phase 5 - P3)**: Depends on Phase 2; validates non-destructive preservation across `backup/` and `screenshots/`.
- **Polish (Phase 6)**: Depends on completion of all user story phases.

### User Story Dependencies

- **US1 (P1)**: Independent of US2 and US3. Can be delivered as complete MVP.
- **US2 (P2)**: Relies on `examples/oneplus-tv-y1/restore.sh` being created in US1 before removing `tools/restore_oneplus_y1.sh` from `tools/`.
- **US3 (P3)**: Validates overall git hygiene and local asset retention after US1 and US2 relocations.

---

## Parallel Opportunities

- Within Phase 1: `T002` can execute in parallel with environment setup.
- Within Phase 2: `T003` (`.gitignore` update) and `T004` (git cache removal) can execute sequentially or together.
- Within Phase 3 (US1):
  - `T005` (`debloat.txt`), `T006` (`screenshot.png`), `T007` (`README.md`), `T009` (`examples/README.md`), and `T010` (`README.md`) touch different files and can proceed in parallel.
- Within Phase 4 (US2):
  - `T012` (`classify_packages.py`) and `T013` (`tools/restore_oneplus_y1.sh`) touch independent files and can run in parallel.
- Within Phase 5 (US3):
  - `T017` (`backup/` verification) and `T018` (`screenshots/` verification) can run in parallel.
- Within Phase 6 (Polish):
  - `T021` (script syntax checks) can run in parallel with documentation review.

---

## Parallel Example: User Story 1

```bash
# Launch independent file creations/updates for User Story 1 in parallel:
Task: "Format model header and package category comments in examples/oneplus-tv-y1/debloat.txt"
Task: "Copy clean home screen reference image from screenshots/01_home.png to examples/oneplus-tv-y1/screenshot.png"
Task: "Create consumer guide examples/oneplus-tv-y1/README.md with hardware specifications, ADB setup..."
Task: "Add OnePlus Y1 model row to documentation catalog table in examples/README.md"
Task: "Update supported TVs table and replace hardcoded ./tools/restore_oneplus_y1.sh command in root README.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (verify directory state).
2. Complete Phase 2: Foundational (configure `.gitignore` and purge tracked `.pyc`).
3. Complete Phase 3: User Story 1 (packaging `examples/oneplus-tv-y1/` and updating catalogs).
4. **STOP and VALIDATE**: Test User Story 1 with Quickstart Scenarios 1 & 2.
5. Deliver MVP model hub!

### Incremental Delivery

1. Complete Phase 1 & 2 → Git baseline secure and clean.
2. Complete Phase 3 (US1) → Consumers have self-contained `examples/oneplus-tv-y1/` hub with guide, restore script, screenshot, and debloat profile (MVP).
3. Complete Phase 4 (US2) → Brand neutrality restored to root `tools/` without losing classifier in `scripts/`.
4. Complete Phase 5 (US3) → Local backups, screenshots, and developer helpers 100% confirmed safe and untracked.
5. Complete Phase 6 (Polish) → Run full 5-scenario quickstart validation suite and syntax checks.
