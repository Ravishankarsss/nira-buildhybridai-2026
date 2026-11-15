# NIRA Android — Phase 2: Print Engineering Progress Report

## Stage 1 — Print Colour Palette (Auto-Detection & Interactive Tuning)
- **Status:** COMPLETED & VERIFIED
- **Key Features:** CIEDE2000 metric, over-clustering with adaptive merging ($3.2\ \Delta E_{00}$), edge transition pixel filtering, garment base exclusion, max screens constraints, interactive merge/split/lock/remove/add HEX color actions, up to 1024px separation preview.

---

## Stage 2 — Shade Library Matching (Built-in Demo & User CSV Standards)

### 1. Implementation Summary
- **Built-in Demo Library:** Automatically loads `app/src/main/assets/nira_demo_shades.csv` (150 generic demo standards with columns `type,code,name,L,a,b`). Never includes or downloads proprietary third-party color systems.
- **Privacy & Git Hygiene:** Added user library CSV patterns (`user_libraries/*.csv`, `**/user_libraries/*.csv`, `*.csv.user`) to `.gitignore`. User imports reside exclusively in app-private storage (`filesDir/user_libraries/`).
- **User Library CSV Import:** Supports multi-library CSV imports via system file picker (`ActivityResultContracts.GetContent()`), validates structure, handles malformed lines safely, and allows enabling/disabling individual libraries with live toggles.
- **Manual "Add Shade" Form:** Allows adding shades with `type` (e.g. TCX, TPG, TN, FACTORY, BUYER), `code`, `name`, and either HEX input (converted to $L^*,a^*,b^*$ via `ColorConverter`) or direct numeric $L^*,a^*,b^*$. Stored on device only in `My_Custom_Shades.csv`.
- **Nearest Shade Matching & Grading:** For every extracted print color, identifies top 3 nearest shades across active libraries using CIEDE2000 ($\Delta E_{00}$) with standard grades:
  - $< 1.0\ \Delta E_{00}$: Excellent
  - $1.0\text{–}2.0\ \Delta E_{00}$: Good
  - $2.0\text{–}4.0\ \Delta E_{00}$: Visible
  - $> 4.0\ \Delta E_{00}$: Poor
- **Shade Assignment & Type Filtering:** Users can filter shade matches by type (e.g. TCX, TPG, BUYER) and tap "Assign" to lock a specific library standard to any extracted color layer.
- **Technical Disclaimers:** All screens explicitly label values as estimates calculated from digital RGB inputs under D65 illuminant, not physical spectrophotometer readings.

---

## 2. Verification Results

### Build Verification
- Task: `:app:assembleDebug`
- Status: **SUCCESS**

### Unit Tests Verification
- Task: `:app:testDebugUnitTest`
- Status: **SUCCESS**
- Total Tests: **24 passed, 0 skipped, 0 failed**
  - `ShadeMatchingTest` (4 new tests):
    - `parseCsv_validFormat_parsesAllRecords`: PASSED
    - `parseCsv_malformedRows_skippedSafely`: PASSED
    - `findNearestShades_returnsClosestInCIEDE2000`: PASSED
    - `findNearestShades_filtersByType`: PASSED
  - `ColorConverterTest` (8 tests): ALL PASSED
  - `ColorExtractorTest` (4 tests): ALL PASSED
  - `ColorSeparatorTest` (3 tests): ALL PASSED
  - `ArtworkModelsTest` (3 tests): ALL PASSED
  - `ArtworkRepositoryTest` (1 test): PASSED
  - `ExampleUnitTest` (1 test): PASSED

---

## 3. Stage 2 Acceptance Checklist
- [x] Load `nira_demo_shades.csv` as built-in asset library
- [x] User CSV library import to app-private storage
- [x] Added `*.csv` in user libraries to `.gitignore`
- [x] Manual "Add shade" dialog supporting HEX or Lab input
- [x] Library enable/disable toggles and type filter chips
- [x] Top 3 nearest shades per color with CIEDE2000 and match grade
- [x] Shade assignment to print color layers
- [x] Sourced estimate disclaimers on UI
- [x] All 24 unit tests pass and debug build succeeds
