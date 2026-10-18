# Feature Specification: Android TV Debloat & Apple TV Style Custom Launcher

**Feature Branch**: `001-debloat-apple-tv-launcher`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "I want to debloat my Android TV and replace its launcher with a clean Apple TV style home screen. My TV model is: OnePlus Y1 43 inch android 11 . Work on the real device, step by step, and verify everything you do with screenshots pulled from the TV. Ask me before anything that depends on my preferences. Connect: Find the TV on my network (hostname, ARP table, or ask me for the IP) and connect over ADB on port 5555. If the stock adb binary fails with 'No route to host' on macOS even though the port is open, that is macOS Local Network privacy blocking adb. Don't debug it: use the Python adb-shell package in a venv as the ADB client instead. If its file push hangs on larger files, transfer files as base64 chunks through the shell and verify the md5. Tell me when the 'Allow USB debugging?' prompt will appear on the TV so I can accept it. Back up first: Save the full package list, disabled-package list, getprop output and all settings to a backup folder before changing anything, and write a restore script that re-enables everything you disable. Debloat: List installed packages and sort them into: manufacturer telemetry/analytics, manufacturer content and ad services (PatchWall, recommendation rows, content providers), background services I probably don't need (share/cast duplicates, cleaners), preloaded apps, and streaming apps. Disable the first categories with pm disable-user --user 0, never uninstall system packages. Never disable what the TV needs to work: the TV app and input services that play HDMI and the tuner, the input/source menu, remote control and Bluetooth pairing services, the on-screen keyboard, Google Play Store and Play Services. Manufacturer package names often don't say what a package does: check its activities and services with dumpsys package first, and ask me if it's still unclear. Before touching streaming apps, voice assistant, Chromecast, Miracast or the update service, ask me which ones I use with a checklist question and disable only what I don't tick. Run pm commands one per shell call, chained commands may silently stop after the first. Launcher: Don't install a third-party launcher that charges for basics like wallpapers. Build a small native Android TV launcher instead (Java, AndroidX RecyclerView, minSdk matching the TV, debug build is fine), install it over ADB, set it as the default home and disable the stock launcher. If several apps claim the HOME intent, set the default with cmd package set-home-activity. Design, Apple TV 4K style: pitch black background; a dock row at the bottom of the screen inside a slightly lighter rounded band holding the four apps I pick (ask me which, and the order); the rest of the apps in a 4-column grid below that scrolls up when I press Down; one translucent 'glass' highlight that springs from tile to tile instead of per-tile borders; the focused tile scales up slightly and shows its name underneath; above the dock, the focused dock app's name in large light type with an ambient glow in a colour sampled from its banner; frosted pill buttons top right for Inputs, Wi-Fi (showing the network name) and Settings; a greeting, a large thin clock and the date top left in a downloaded open-licence typeface such as Outfit. Inputs opens a compact glass dropdown under the pill listing the HDMI ports, AV and Live TV, switching via the TV input framework. Make D-pad navigation deterministic: Up/Down move exactly one row, Up from the dock goes to the top bar, the ends of the top bar don't fall through to tiles. Remember the last focused tile. Keep it light: the TV has about 1 GB of RAM. No blur, no video, no network polling. Verify: After each change, inject remote key presses over ADB, take a screenshot from the TV and look at it. Fix what you see before moving on. When done, give me a restore script, a build-and-install script, and a README that explains how to re-enable any app and how to change the dock order or the wallpaper."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Safe TV Connectivity and System Baseline Backup (Priority: P1)

As a TV owner, I want to reliably connect to my TV over the local network and capture a full backup of all existing packages, properties, and configuration states before making any system modifications, so that my TV can be fully restored if any step encounters issues.

**Why this priority**: Establishing a stable connection and full system backup is the prerequisite for all device actions; without this, any modification risks leaving the TV in an unrecoverable state.

**Independent Test**: Can be verified by initiating the connection workflow, confirming connectivity on the device, prompting the user for authorization, and inspecting generated backup snapshots containing complete package and property inventories.

**Acceptance Scenarios**:

1. **Given** a TV with network debugging enabled on the local network, **When** the discovery and connection process runs, **Then** the TV is discovered, the user is notified before the on-screen authorization prompt appears, and an active debug session is established.
2. **Given** an active device session, **When** the baseline backup routine runs, **Then** full package lists, disabled package lists, system properties, and global/secure settings are exported to a dedicated backup directory without modifying the TV state.
3. **Given** an exported backup, **When** the restore script generator executes, **Then** an automated rollback script is produced capable of reversing any future disabled package operations.

---

### User Story 2 - Targeted TV Debloat with Preference Confirmation (Priority: P2)

As a TV owner, I want to disable manufacturer telemetry, advertising engines, recommendation rows, and redundant background services while keeping essential hardware and input services intact, and I want explicit confirmation before any streaming apps, voice assistant, casting, or update services are modified.

**Why this priority**: Eliminating bloatware frees up limited memory (1 GB RAM), stops manufacturer background analytics, and removes ad banners, directly improving system responsiveness while preserving critical TV functionality.

**Independent Test**: Can be verified by auditing the categorized package inventory, executing the safe disablement workflow for approved background packages, and confirming that HDMI inputs, remote pairing, TV tuner, and keyboard remain fully operational.

**Acceptance Scenarios**:

1. **Given** an active device session, **When** package inspection occurs, **Then** installed packages are classified into manufacturer telemetry, ad/recommendation services, non-essential background services, preloaded apps, and media/streaming apps.
2. **Given** packages requiring user discretion (streaming services, voice assistant, casting, OTA update service), **When** debloat staging occurs, **Then** the user is prompted with an interactive checklist and only unchecked items are staged for disablement.
3. **Given** classified packages to disable, **When** the debloat process executes, **Then** packages are disabled non-destructively for the primary user without removing system binaries, and core TV capabilities (HDMI, tuner, input menu, Bluetooth remote, keyboard, Play Store) are verified operational.

---

### User Story 3 - Apple TV Style Minimalist Home Screen (Priority: P3)

As a TV owner, I want a clean, high-performance, dark-themed custom launcher inspired by Apple TV that features a dedicated 4-app dock, a scrollable application grid, fluid translucent focus highlights, an input source switcher, and top-bar status indicators without ads or memory bloat.

**Why this priority**: Replacing the stock Android TV launcher with an ultra-lightweight, ad-free Apple TV aesthetic delivers the primary user-facing experience of simplicity, speed, and modern design.

**Independent Test**: Can be verified by deploying the launcher, launching dock and grid applications, testing D-pad navigation across all screen zones, switching TV inputs from the top pill dropdown, and verifying sub-second responsiveness on 1 GB RAM hardware.

**Acceptance Scenarios**:

1. **Given** the user selects 4 preferred dock applications and their order, **When** the launcher is installed and configured, **Then** a pitch-black home screen renders with a dock band at the bottom containing the 4 chosen apps, and a 4-column application grid below.
2. **Given** the launcher is open, **When** navigating with remote D-pad controls, **Then** a translucent glass highlight springs smoothly between tiles, the focused tile gently scales up with its title displayed, and the dock area displays the focused app title with an ambient banner-colored glow.
3. **Given** the top-bar controls, **When** navigating to the top bar, **Then** frosted pill buttons display Inputs, current Wi-Fi network name, and Settings, and selecting Inputs displays a glass dropdown of HDMI, AV, and Live TV sources that switch inputs when chosen.
4. **Given** continuous D-pad navigation, **When** moving between the top bar, dock, and grid, **Then** navigation moves exactly one row per click, boundaries prevent focus dropping or getting trapped, and returning to the home screen restores the last focused tile.

---

### User Story 4 - Visual Validation, Rollback Safety, and Documentation (Priority: P4)

As a TV owner, I want every state change visually verified through device screenshots, a verified one-command restore script, and comprehensive documentation for future customization, so that I have complete confidence in system stability and long-term control.

**Why this priority**: Guarantees quality and transparency during implementation and provides the user with self-service tools to re-enable apps or adjust launcher preferences at any point.

**Independent Test**: Can be verified by simulating key presses, capturing on-device screenshots after key operations, executing the restore script in a dry/test run, and verifying build/install scripts and documentation.

**Acceptance Scenarios**:

1. **Given** any layout or debloat change applied to the TV, **When** validation executes, **Then** synthetic remote key presses are injected and high-fidelity screenshots are captured from the TV to visually confirm expected appearance and behavior.
2. **Given** a modified TV state, **When** the user runs the restore script, **Then** all disabled packages are re-enabled and the system returns to its baseline operational state.
3. **Given** project completion, **When** reviewing the project deliverables, **Then** comprehensive documentation and automated build/install scripts explain how to rebuild the launcher, reconfigure dock items, and customize wallpapers.

---

### Edge Cases

- **Connection Drops during Operation**: Network debugging disconnects midway through backup or installation. The workflow must detect dropped connections, attempt automatic re-connection, and resume idempotently without leaving partially applied states.
- **Ambiguous Package Capabilities**: A manufacturer package has an unfamiliar package name and ambiguous metadata. The system must inspect its exported activities, services, and intent filters; if purpose remains uncertain, it must not disable the package without user confirmation.
- **Low Memory (1 GB RAM) Constrained Behavior**: Heavy assets or continuous polling could trigger system Low Memory Killer (LMK). The custom launcher must avoid background service polling, expensive image decodes, live video previews, and memory-intensive blur shaders.
- **Single or Missing Remote Input Signals**: D-pad navigation reaches edges (e.g., navigating Up from top bar or Left from the leftmost tile). Boundaries must hold focus predictably without wrapping around erratically or dropping focus off-screen.
- **Multiple Registered Home Launchers**: Multiple applications respond to the home intent after installation. The system must explicitly set the custom launcher as the default home activity and deactivate the stock OEM launcher.
- **TV Input Framework Source Absence**: Physical TV inputs (HDMI ports, AV, tuner) vary by hardware detection state. The input dropdown must enumerate and display only valid available hardware inputs via the TV input framework and gracefully report unavailable inputs.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST establish a reliable remote debugging session to the TV over the local area network on port 5555, supporting automated discovery and explicit IP input fallbacks.
- **FR-002**: System MUST alert the user prior to triggering the on-screen USB/network debugging authorization dialog on the TV.
- **FR-003**: System MUST support resilient file and data transfer mechanisms that prevent transport hangs and verify payload integrity.
- **FR-004**: System MUST capture and save a complete baseline backup snapshot of all installed packages, disabled packages, system properties, and system settings prior to any device modifications.
- **FR-005**: System MUST generate an automated, standalone restore script capable of re-enabling all disabled packages and returning device configuration to baseline.
- **FR-006**: System MUST discover and categorize all installed packages into manufacturer telemetry/analytics, manufacturer content/ad engines, redundant background services, preloaded applications, and streaming/media services.
- **FR-007**: System MUST perform package deep-inspection (activities, services, intent filters) before categorizing unfamiliar manufacturer packages.
- **FR-008**: System MUST protect essential system services from disablement, including TV input services, HDMI/tuner playback, remote control pairing, Bluetooth services, on-screen keyboard, and core system store/services.
- **FR-009**: System MUST present an interactive checklist to the user for discretionary packages (streaming apps, voice assistant, casting/Miracast, system update services) and only disable unselected packages.
- **FR-010**: System MUST execute package disablement non-destructively for the primary user without removing underlying system binaries.
- **FR-011**: System MUST provide a native, lightweight Android TV launcher featuring a pitch-black background, a bottom dock band holding exactly four user-selected apps in specified order, and a 4-column scrollable grid for all remaining applications.
- **FR-012**: System MUST render an Apple TV style fluid translucent glass highlight that springs smoothly between focused tiles and slightly scales the active tile with its title displayed.
- **FR-013**: System MUST display the active dock application's title in large light typography accompanied by a soft ambient glow sampled from the application's visual branding.
- **FR-014**: System MUST provide frosted pill status buttons in the top-right corner for TV Inputs, active Wi-Fi network name, and System Settings.
- **FR-015**: System MUST provide an interactive TV Inputs dropdown displaying physical TV sources (HDMI ports, AV, Live TV) and seamlessly switch video inputs via the TV input framework.
- **FR-016**: System MUST display a personalized greeting, a large clean clock, and the current date in the top-left corner utilizing an open-licence modern typeface.
- **FR-017**: System MUST enforce deterministic D-pad navigation across all home screen zones (top bar, dock row, application grid) with strict row boundaries, edge clamping, and memory of the last focused tile.
- **FR-018**: System MUST visually verify all interactive states and system modifications by injecting remote key inputs and capturing real-device screenshots from the TV.
- **FR-019**: System MUST provide automated build-and-install scripts and complete documentation for dock reordering, app restoration, and wallpaper customization.

### Key Entities

- **Device Profile**: Represents the connected Android TV instance (model name, operating system version, IP address, connection port, authorization state).
- **Package Inventory**: Comprehensive list of installed packages with classification metadata (category, package name, enabled/disabled state, system critical flag, user approval flag).
- **Baseline Snapshot**: Time-stamped backup archive containing full package dump, disabled package dump, `getprop` output, and settings tables (`global`, `system`, `secure`).
- **Dock Configuration**: User-configured list of exactly four application package identifiers and their ordinal positions (1 through 4) in the persistent dock bar.
- **TV Input Source**: Physical or logical video input entity (Input ID, human-readable label such as HDMI 1/2/3, AV, or Live TV, and connection status).
- **Launcher Focus State**: Current navigation cursor state (focused zone: top bar, dock, or grid; focused item index; previous focused item index; transition spring animation state).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Device discovery, secure connection establishment, and full baseline backup complete in under 2 minutes.
- **SC-002**: 100% of critical TV functions (HDMI input switching, remote control pairing, audio/video playback, keyboard, and app store access) remain fully functional after debloat.
- **SC-003**: Background RAM utilization of the home launcher remains below 50 MB with zero frame drops or lag on low-specification hardware (1 GB RAM total).
- **SC-004**: Cold launch and return-to-home latency of the custom launcher is under 1.0 second.
- **SC-005**: 100% of disabled packages can be re-enabled and restored to normal operation via the restore script within 60 seconds without requiring a factory reset.
- **SC-006**: Remote D-pad navigation achieves 100% deterministic focus traversal with zero focus loss, focus hopping, or boundary clipping across all interface components.
- **SC-007**: 100% of user preference-dependent items (discretionary debloat selections, dock app selection, dock ordering) require explicit user selection before application.
- **SC-008**: 100% of major visual states (initial home, dock focus, grid scroll, inputs dropdown, settings navigation) are visually validated with on-device screenshots.

## Assumptions

- Target TV is a OnePlus Y1 43-inch running Android TV 11 connected to the same local subnet as the development environment.
- Developer options and Network/USB debugging are accessible and can be enabled by the user on the TV.
- The user has access to the physical or paired TV remote control to confirm on-screen authorization dialogs.
- The TV hardware platform is memory-constrained (~1 GB total RAM), necessitating an ultra-lean native launcher design without background polling, heavy video playback in UI, or complex real-time blur shaders.
- The stock OnePlus launcher and advertising components can be disabled via user package management (`disable-user --user 0`) once the custom launcher is installed and designated as default home.
- Standard TV input framework APIs are supported on the device for querying and tuning HDMI and AV input sources.
