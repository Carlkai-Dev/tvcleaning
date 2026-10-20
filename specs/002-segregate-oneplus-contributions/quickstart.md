# Quickstart Validation Guide: Segregation Verification

**Feature Branch**: `002-segregate-oneplus-contributions`  
**Date**: 2026-10-20  
**Feature Spec**: [spec.md](./spec.md)  
**Data Model**: [data-model.md](./data-model.md)  
**Contracts**: [contracts/](./contracts/)

---

## 1. Validation Scenarios

### Scenario 1: Model Package Hub Verification
Verify that `examples/oneplus-tv-y1/` is properly packaged as a complete, self-contained consumer hub according to `CONTRIBUTING.md`.

```bash
# 1. Verify debloat.txt exists and has correct header metadata
grep -E "OnePlus|shibuya|Android|RTO9" examples/oneplus-tv-y1/debloat.txt
grep -E "Live TV app: com.mediatek.wwtv.tvcenter" examples/oneplus-tv-y1/debloat.txt
grep -E "Stock launcher: com.google.android.tvlauncher" examples/oneplus-tv-y1/debloat.txt

# 2. Verify dedicated consumer documentation guide exists
cat examples/oneplus-tv-y1/README.md

# 3. Verify dedicated restore script is executable
[ -x examples/oneplus-tv-y1/restore.sh ] && echo "[✓] restore.sh is executable"

# 4. Verify reference screenshot exists in example directory
ls -lh examples/oneplus-tv-y1/screenshot.png
```

---

### Scenario 2: Documentation Catalog Consistency
Verify that both `examples/README.md` and root `README.md` list the OnePlus Y1 model correctly.

```bash
# Check examples/README.md
grep -E "oneplus-tv-y1" examples/README.md

# Check root README.md
grep -E "OnePlus Y1" README.md
```

---

### Scenario 3: Shared Core Tools Brand-Neutrality Audit
Verify that root `tools/` does not contain hardcoded model-specific scripts.

```bash
# tools/ should NOT contain vendor-specific scripts like restore_oneplus_y1.sh or classify_packages.py
[ ! -f tools/restore_oneplus_y1.sh ] && echo "[✓] tools/ has no vendor restore script"
[ ! -f tools/classify_packages.py ] && echo "[✓] tools/ has no vendor classifier script"

# Verify scripts were preserved in examples/oneplus-tv-y1/
[ -f examples/oneplus-tv-y1/classify_packages.py ] && echo "[✓] classify_packages.py preserved in model hub"
[ -f examples/oneplus-tv-y1/restore.sh ] && echo "[✓] restore.sh preserved in model hub"
[ ! -d scripts ] && echo "[✓] root scripts/ directory eliminated"
```

---

### Scenario 4: Git Cleanliness & Bytecode Hygiene
Verify that git index has no tracked `.pyc` files or private backup dumps.

```bash
# Check for any tracked .pyc files
git ls-files | grep '\.pyc$' || echo "[✓] No bytecode tracked"

# Check that backup/ remains untracked
git ls-files backup/ || echo "[✓] backup/ is safely untracked"
```

---

### Scenario 5: Universal Tool Execution Verification
Verify that generic core tools, launcher scripts, and model tools pass syntax checks cleanly.

```bash
# Syntax check tools
bash -n tools/disable.sh
bash -n tools/restore.sh
bash -n tools/backup.sh
bash -n tools/screenshot.sh
bash -n tools/check_debloat.sh

# Syntax check launcher scripts
bash -n launcher/build-and-install.sh
bash -n launcher/setup-linux.sh

# Syntax check model scripts
bash -n examples/oneplus-tv-y1/restore.sh
bash -n examples/oneplus-tv-y1/generate_restore_script.sh
```
