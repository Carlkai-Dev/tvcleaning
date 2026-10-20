# Feature Specification: Segregate OnePlus Y Series Contributions per CONTRIBUTING Guidelines

**Feature Branch**: `002-segregate-oneplus-contributions`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "The changes we had made are specific to one plus y series tv. Can you please help me segregate them following the CONTRIBUTING.md ? We do not want to delete a change just want to make sure this fork does not create a destructive change which hinder other areas when merged to actual source which is for other tvs as well."

---

## Clarifications

### Session 2026-10-20

- Q: How should OnePlus Y Series scripts, debloat profiles, and instructions be packaged so that any consumer can easily discover, apply, and restore changes on their device? → A: Option A: Self-contained model hub. Co-locate `debloat.txt`, `restore.sh`, `screenshot.png`, and a dedicated consumer `README.md` inside `examples/oneplus-tv-y1/`, keeping general developer automation and baseline backup tools in `scripts/`.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Upstream-Compliant TV Model Contribution (Priority: P1)

As an open-source contributor and TV owner, I want the OnePlus Y1 43" debloat profile, documentation, and screenshots organized strictly according to `CONTRIBUTING.md`, so that other users with this TV model can safely use it and upstream maintainers can review and merge the model without friction.

**Why this priority**: Contributing a new TV model profile is the primary open-source contribution mechanism defined by the repository; conforming to repository structure ensures community compatibility.

**Independent Test**: Can be validated by inspecting `examples/oneplus-tv-y1/debloat.txt`, `examples/oneplus-tv-y1/README.md`, `examples/README.md`, and checking that model headers, package annotations, restore scripts, and screenshot attachments adhere to repository contribution standards.

**Acceptance Scenarios**:

1. **Given** debloat profile data for the OnePlus Y1 43" TV, **When** formatting the profile file, **Then** `examples/oneplus-tv-y1/debloat.txt` contains a standardized header listing the exact model (`Y Series`), codename (`shibuya`), Android version (`11`), build ID (`RTO9.231122.002`), Live TV package (`com.mediatek.wwtv.tvcenter`), and stock launcher package (`com.google.android.tvlauncher`).
2. **Given** the classified packages in `examples/oneplus-tv-y1/debloat.txt`, **When** organizing package entries, **Then** packages are grouped under descriptive category comments with explanatory notes for non-obvious items, omitting destructive uninstall commands.
3. **Given** a consumer visiting `examples/oneplus-tv-y1/`, **When** reviewing the directory, **Then** a dedicated `README.md` clearly guides the consumer on applying the debloat list, restoring the device, and configuring the custom launcher for the OnePlus Y1.
4. **Given** the upstream `examples/README.md` catalog, **When** updating documentation, **Then** a new table entry clearly summarizes the OnePlus Y1 model, operating system, and key debloat characteristics without altering existing TV rows.
5. **Given** visual verification evidence, **When** packaging example assets, **Then** a representative home screenshot is placed directly in `examples/oneplus-tv-y1/screenshot.png`.

---

### User Story 2 - Brand Neutrality for Shared Core Tools (Priority: P2)

As a maintainer of multiple TV platforms, I want core CLI tools and the launcher build scripts to remain brand-agnostic and non-destructive, so that changes introduced for the OnePlus Y1 do not disrupt or hardcode assumptions for other Android and Google TV models.

**Why this priority**: Core tools (`tools/`) and launcher code (`launcher/`) must work universally across Xiaomi, TCL, Sony, Hisense, and generic Android TV sets; hardcoded vendor logic breaks upstream functionality.

**Independent Test**: Can be validated by auditing `tools/` and `launcher/` source files to verify that all hardware- or vendor-specific package identifiers are supplied via parameters or configuration, while generic environment enhancements (e.g. Linux path discovery, network timeout resilience) remain intact.

**Acceptance Scenarios**:

1. **Given** shared device administration scripts in `tools/`, **When** reviewing tool interfaces, **Then** generic tools (`tools/tv.py`, `tools/disable.sh`, `tools/restore.sh`, `launcher/build-and-install.sh`) accept model debloat lists and package names as arguments rather than hardcoding vendor package IDs.
2. **Given** vendor-tailored automation scripts, **When** segregating repository files, **Then** model-specific restore scripts reside in `examples/oneplus-tv-y1/restore.sh` and classifier scripts in `scripts/classify_packages.py` rather than displacing or polluting generic root tools.
3. **Given** build environment improvements (e.g., Linux OpenJDK 17 and Android SDK detection in `build-and-install.sh`, wrapper download resilience), **When** merging with upstream, **Then** these enhancements apply generically to all target environments without regression on macOS or Windows.

---

### User Story 3 - Clean Segregation and Preservation of Working Assets (Priority: P3)

As a developer working on this fork, I want all code, scripts, device backups, and verification artifacts created for the OnePlus Y Series preserved safely in dedicated locations, so that local working state is maintained and future consumers can easily reference them without committing transient files or leaking sensitive TV data into upstream PRs.

**Why this priority**: Prevents accidental data loss for the user while ensuring git cleanliness (ignoring compiled bytecode, local backup caches, and transient files).

**Independent Test**: Can be validated by checking `git status` and `.gitignore` to confirm that all required assets remain accessible locally, no unwanted cache files (such as compiled `.pyc` files) are tracked by git, and private backup dumps remain local.

**Acceptance Scenarios**:

1. **Given** device backup archives containing serial numbers and local network properties, **When** reviewing git tracking, **Then** local device backups remain strictly ignored by git and preserved on the developer's machine in `backup/`.
2. **Given** workflow automation tools created for the OnePlus Y1, **When** reviewing the repository layout, **Then** all scripts (`scripts/backup_baseline.sh`, `scripts/generate_restore_script.sh`, `scripts/prompt_debloat_preferences.sh`, `scripts/verify_ui_screenshots.sh`, `scripts/classify_packages.py`) are fully preserved in `scripts/`.
3. **Given** build and test artifacts (e.g. Python bytecode caches, temporary APKs), **When** checking repository status, **Then** ignore rules prevent compiled binaries from being tracked in version control.

---

## Edge Cases

- **Upstream Merge Conflicts in Documentation**: Upstream `README.md` or `examples/README.md` may have received concurrent edits from other contributors. Documentation changes must add distinct, non-overlapping rows and preserve existing formatting.
- **Generic Tool Parameter Compatibility**: Existing tools (`tools/restore.sh`, `launcher/build-and-install.sh`) accept optional package arguments (`STOCK_LAUNCHER`, `stock launcher package`). OnePlus-specific defaults must be passed via standard script arguments rather than modifying root tool defaults.
- **Bytecode and Cache Artifact Ingestion**: Past commits inadvertently introduced `.pyc` compiled files in `.specify/`. Git index must be purged of tracked bytecode files without deleting functional Python code.
- **Consumer Discoverability of Restores**: A consumer who disables packages on a OnePlus Y1 might look in `examples/oneplus-tv-y1/` for an instant fix; having `examples/oneplus-tv-y1/restore.sh` alongside `README.md` ensures self-service recovery even if they don't know CLI flags.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide an upstream-compliant debloat profile in `examples/oneplus-tv-y1/debloat.txt` containing model metadata (model name, device codename, Android version, firmware build ID, Live TV package, and stock launcher package).
- **FR-002**: System MUST organize package entries in `debloat.txt` with clear category headers (telemetry, ad rows, demos, preloaded bloat) and actionable inline comments.
- **FR-003**: System MUST provide a dedicated consumer documentation guide in `examples/oneplus-tv-y1/README.md` detailing how to inspect, apply debloat, configure the launcher, and restore a OnePlus Y Series TV.
- **FR-004**: System MUST update `examples/README.md` and root `README.md` with accurate tabular entries for the OnePlus Y1 43" (Y Series / shibuya) model.
- **FR-005**: System MUST ensure core tools in `tools/` and `launcher/` remain 100% brand-neutral and rely on arguments or `config.xml` for TV-specific package names.
- **FR-006**: System MUST maintain generic build system improvements (Linux JDK/Android SDK discovery, gradle wrapper network resilience) that benefit all users.
- **FR-007**: System MUST relocate the dedicated OnePlus restore helper from root `tools/restore_oneplus_y1.sh` to `examples/oneplus-tv-y1/restore.sh`.
- **FR-008**: System MUST relocate the package classifier script to `scripts/classify_packages.py` to preserve developer tooling without cluttering root `tools/`.
- **FR-009**: System MUST place a real-device visual verification screenshot inside `examples/oneplus-tv-y1/screenshot.png` to serve as reference evidence.
- **FR-010**: System MUST ensure `.gitignore` properly excludes local device backups, binary caches, and temporary files, removing any erroneously tracked `.pyc` files from version control.
- **FR-011**: System MUST preserve 100% of all existing device backups, helper scripts, and launcher configurations without destructive deletion of any user asset.

---

### Key Entities

- **TV Example Model Profile**: Standalone directory (`examples/oneplus-tv-y1/`) containing `debloat.txt`, `README.md`, `restore.sh`, and reference visual evidence (`screenshot.png`).
- **Core CLI Tooling**: Root scripts (`tools/tv.py`, `tools/disable.sh`, `tools/restore.sh`, `tools/b64push.py`) providing universal, brand-agnostic device automation.
- **Developer Helper Suite**: Collection of preserved automation scripts (`scripts/`) supporting backup, preference verification, testing, and screenshots.
- **Repository Hygiene Configuration**: `.gitignore` entries and git tracking index governing what files belong in upstream commits versus local user environments.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of OnePlus Y1-specific debloat definitions, documentation, and dedicated restore scripts reside in `examples/oneplus-tv-y1/` without hardcoded vendor dependencies in shared core files.
- **SC-002**: 100% of core tools in `tools/` execute successfully on any generic Android TV model using standard command-line arguments.
- **SC-003**: Any consumer reviewing `examples/oneplus-tv-y1/README.md` can complete debloat or full restoration with a single documented terminal command.
- **SC-004**: Zero tracked bytecode files (`.pyc`), private device backup dumps, or extraneous binary artifacts remain in git index.
- **SC-005**: Zero loss of local user data: 100% of local backup files, custom launcher source code, and verification screenshots remain intact on the developer's system.

---

## Assumptions

- The target upstream branch is `origin/main` or the upstream development branch, which expects modular model folders under `examples/`.
- Changes made to `launcher/build-and-install.sh` (adding Linux paths for `JAVA_HOME` and `ANDROID_HOME`) and `gradle-wrapper.properties` (increasing network timeout and retries) are universally beneficial improvements acceptable in upstream PRs.
- Having a dedicated `README.md` and `restore.sh` inside `examples/oneplus-tv-y1/` maximizes consumer accessibility while maintaining strict repository separation.
