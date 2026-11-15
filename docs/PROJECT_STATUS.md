# NIRA Print Report — Project Status & Implementation Log

## Current Status: Part C Completed & Verified (Awaiting User Approval for Part D)

**Project Name:** NIRA (Textile & Garment Screen Printing Intelligence)  
**Package:** `com.mos.nira`  
**Phase:** NIRA Print Report — Deterministic Kotlin Core  
**Current Stage:** PART C — Screens and Proof (Film Positives & Side-by-Side Verification)  
**Build Status:** `:app:assembleDebug` **SUCCESS**  
**Unit Test Status:** `:app:testDebugUnitTest` **44 Passed, 0 Failed, 0 Skipped (SUCCESS)**  

---

## 1. Summary of Completed & Approved Stages

### Part A — Print Area and Measured Palette (Approved)
- Dynamic physical dimensions from user-specified width in cm with aspect-ratio preserving height calculation.
- Fast white garment masking ($L^* > 80, \text{chroma} < 9$), user-adjustable non-white garment threshold with 5.0 default, and touch drag-to-erase bounding box exclusion.
- K-Means ($K=16$) Lab clustering with CIEDE2000 metric, $3.2\ \Delta E_{00}$ cluster merging, $1.5\%$ noise pruning, and shade library matching.
- Flat-separation posterisation error curve across $K \in \{4, 6, 8, 10, 12, 16\}$.
- Edge-case resilience: empty images, $1 \times 1$ pixel, and 100% garment-coloured images verified.

### Part B — Separation Strategies Comparison Table (Approved)
- Editable nominal CMYK process inks stored in [`print_rules.json`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/assets/print_rules.json) (`cyan: #00AEEF`, `magenta: #EC008C`, `yellow: #FFF200`, `black: #231F20`) with confidence `"assumption"` and dynamic loader [`PrintRulesRepository.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/data/PrintRulesRepository.kt).
- Linear RGB absorbance conversion $\mathbf{a} = -\ln(\text{linear RGB})$ clipped to $[0.004, 1.0]$.
- Pure Kotlin Lawson-Hanson Non-Negative Least Squares (NNLS) solver with Tikhonov regularization ($\lambda = 10^{-6}$).
- 5-bit color quantization enabling $< 250\text{ ms}$ evaluation on 1-megapixel images.
- Four separation strategies evaluated with Three Quality Figures: Mean $\Delta E_{00}$, 95th Percentile $\Delta E_{00}$, and % of pixels within $3.0\ \Delta E_{00}$.
- Dark garment rule: automatically adds Base White Underbase and Highlight White screens (+2 screens) and displays warning: `"CMYK alone is not suitable on dark garments without an underbase white."`
- Model disclaimer label: `"Model result. No dot gain or registration error included."`

---

## 2. PART C — Screens, Proof & Film Positives Implementation

### 2.1 Film Positive Greyscale Channel Generation
- **Industry Specification:**
  - In screen printing film positive exposure, black ink on film blocks UV light from hardening photo-emulsion, creating the stencil mesh opening.
  - **100% ink coverage ($c = 1.0$)** $\implies$ Pure **Black** (`#000000`, grey 0).
  - **0% ink coverage ($c = 0.0$)** $\implies$ Pure **White** (`#FFFFFF`, grey 255).
  - **Intermediate halftones ($c \in [0.0, 1.0]$)** $\implies$ Linear inverted density:
    $$\text{grey} = \text{round}((1.0 - c) \times 255.0).\text{toInt}().\text{coerceIn}(0, 255)$$
    $$\text{pixelColor} = 0\text{xFF000000} \mid (\text{grey} \ll 16) \mid (\text{grey} \ll 8) \mid \text{grey}$$
- **Channel Data Model ([`FilmPositiveChannel`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/domain/PrintReportModels.kt)):**
  - `channelIndex: Int`
  - `inkName: String`
  - `inkHex: String`
  - `inkRgb: ColorConverter.Rgb`
  - `averageCoveragePercent: Double`: Active inked print area coverage % ($\frac{\sum c_j}{N_{\text{print}}} \times 100\%$).
  - `boxCoveragePercent: Double`: Total print bounding box coverage % ($\frac{\sum c_j}{N_{\text{total}}} \times 100\%$).
  - `filmPositiveBitmap: Bitmap?`: Generated greyscale film positive bitmap.
  - `rawGreyscalePixels: IntArray?`: Direct access pixel array for verification and high-speed processing.
  - `recommendedMesh: String`: Derived from `print_rules.json` (e.g. `305 tpi` for CMYK, `110 tpi` for Underbase White, `156 tpi` for Flat Spot, `200 tpi` for Spot Halftone, `230 tpi` for Highlight White).

### 2.2 Reconstructed Model Proof
- **Halftone Strategies (CMYK, Best N Spot, Extended Spot):**
  - Solves ink amounts $c_j$ per pixel using 5-bit quantized NNLS lookup.
  - Reconstructs spectral absorbance: $\mathbf{a}_{\text{proof}} = \sum_{j} c_j \mathbf{a}_{\text{ink}, j}$.
  - Converts back to linear RGB: $C_{\text{linear}} = \exp(-\mathbf{a}_{\text{proof}})$.
  - Converts linear RGB to sRGB and clamps to $[0, 255]$.
- **Flat Spot Strategy:**
  - Maps each masked pixel to its nearest cluster centroid in Lab space (solid screen).
  - Proof displays the exact spot ink RGB.
- Substrate / unprinted pixels display the garment color.

### 2.3 Side-by-Side Comparison UI
- Implemented in [`PrintReportScreen.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/presentation/PrintReportScreen.kt) under **Section 6**:
  - Side-by-side pane comparing **Original Artwork** and **Reconstructed Model Proof**.
  - Dynamic channel list showing each film positive thumbnail, ink swatch square, ink name, HEX code, mesh count, and coverage percentages.

### 2.4 Composite Separation Sheet & PNG Export
- **Canvas Generation ([`createCompositeSeparationSheet`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/core/image/SeparationStrategyEngine.kt)):**
  - Consolidated multi-pane separation sheet:
    1. **Header Banner:** App title, strategy name, physical print dimensions, screen count, and garment color.
    2. **Side-by-Side Section:** Original Artwork vs Reconstructed Proof thumbnails.
    3. **Grid Section:** 3-column layout of all film positive channel screens with ink swatches, HEX, recommended mesh, and coverage %.
    4. **Footer Banner:** Mandatory disclaimer label.
- **Export Functionality:**
  - `ArtworkViewModel.exportSeparationSheet(context)` compresses the composite separation sheet into a high-resolution PNG file in app cache/exports and provides status confirmation.

### 2.5 Mandatory Technical Disclaimer Label
- Displayed prominently in [`PrintReportScreen.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/presentation/PrintReportScreen.kt), [`SeparationStrategyEngine.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/core/image/SeparationStrategyEngine.kt), and on the composite separation sheet:
  `"Preliminary digital separation, not print-ready films. Model has no dot gain or registration error. Needs original artwork at about 300 dpi at print size."`

---

## 3. Real 1024px Benchmark Execution Results

Tested on a full $1024 \times 1024$ image (1,048,576 pixels) featuring 8 distinct chromatic zones:

| Engine Component | Spec Budget | Measured Run Time | Status |
| :--- | :--- | :--- | :--- |
| **Part A (Print Area, Mask & Palette)** | $< 5000\text{ ms}$ | **~250–350 ms** | **PASSED** |
| **Part B (4 Strategies Evaluation & NNLS)** | $< 3000\text{ ms}$ | **~120–250 ms** | **PASSED** |
| **Part C (All Channels, Proof & Sheet)** | $< 3000\text{ ms}$ | **~150–280 ms** | **PASSED** |
| **Total Engine Latency (1024px)** | $< 11000\text{ ms}$ | **< 880 ms** | **WELL WITHIN THRESHOLD** |

---

## 4. Comprehensive Unit Test Suite Results

```bash
./gradlew :app:testDebugUnitTest
```
- **Status:** **44 passed, 0 failed, 0 skipped (100% SUCCESS)**

### Part C Tests ([`SeparationStrategyEngineTest.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/test/java/com/mos/nira/core/image/SeparationStrategyEngineTest.kt)):
1. `testPartCFilmPositiveGenerationCMYK`: **PASSED**
   - Verifies 4 process channels generated.
   - Verifies pure Cyan artwork produces $> 80\%$ ink on Cyan channel and $< 5\%$ ink on Magenta, Yellow, Black.
   - Verifies film positive pixels: dark (grey $\le 50$) on Cyan screen, pure white (grey $\ge 250$) on Magenta screen.
   - Verifies exact mandatory disclaimer label.
2. `testPartCDarkGarmentWhitesRule`: **PASSED**
   - Verifies dark garment ($L^* \le 50$) automatically creates 6 screens (Base White + CMYK + Highlight White).
   - Verifies Base White mesh is `"110 tpi"` and ink coverage is $> 90\%$.
   - Verifies Highlight White mesh is `"230 tpi"`.
3. `testPartCFlatSpotSolidBinaryScreens`: **PASSED**
   - Verifies solid spot screen separation: every pixel is binary (either $100\%$ ink = black 0, or $0\%$ ink = white 255).
4. `testPerformanceBenchmarkOn1024pxImage`: **PASSED**
   - Verifies Parts A, B, and C all execute well under individual latency thresholds on a 1-megapixel image.

---

## 5. Files Summary for Part C

### Files Modified & Enhanced
- [`app/src/main/java/com/mos/nira/features/artwork/domain/PrintReportModels.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/domain/PrintReportModels.kt): Added `FilmPositiveChannel` and `PartCResult`.
- [`app/src/main/java/com/mos/nira/core/image/PrintAreaAndPaletteEngine.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/core/image/PrintAreaAndPaletteEngine.kt): Extracted public `computePrintMask`.
- [`app/src/main/java/com/mos/nira/core/image/SeparationStrategyEngine.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/core/image/SeparationStrategyEngine.kt): Implemented `generatePartC` and `createCompositeSeparationSheet`.
- [`app/src/main/java/com/mos/nira/features/artwork/presentation/ArtworkViewModel.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/presentation/ArtworkViewModel.kt): Added `partCResult`, `isComputingPartC`, `computePartCReport`, and `exportSeparationSheet`.
- [`app/src/main/java/com/mos/nira/features/artwork/presentation/PrintReportScreen.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/presentation/PrintReportScreen.kt): Added Section 6 with side-by-side comparison, `FilmPositiveCard`, and composite sheet export card.
- [`app/src/main/java/com/mos/nira/features/artwork/navigation/ArtworkNavHost.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/main/java/com/mos/nira/features/artwork/navigation/ArtworkNavHost.kt): Wired Part C callbacks and export actions.
- [`app/src/test/java/com/mos/nira/core/image/SeparationStrategyEngineTest.kt`](file:///C:/Users/shank/AndroidStudioProjects/NIRA/app/src/test/java/com/mos/nira/core/image/SeparationStrategyEngineTest.kt): Added unit tests for Part C.

---

## 6. Next Steps (Stopping for User Approval)

- [x] **PART A — Print area and measured palette** (COMPLETED & APPROVED)
- [x] **PART B — Separation strategies (the comparison table)** (COMPLETED & APPROVED)
- [x] **PART C — Film positives & proof preview side-by-side** (COMPLETED & VERIFIED)
- [ ] **STOPPING FOR USER APPROVAL** before proceeding to:
  - **PART D — Ink consumption calculations**:
    - Per channel formula: $\text{ink g} = \text{average ink amount over print box} \times \text{box area } (\text{m}^2) \times \text{deposit } (\text{g/m}^2\text{ from } \texttt{print\_rules.json}\text{ for mesh}) \times \text{strokes} \times (1 + \text{wastage})$.
    - Show per garment, per 100 pieces, and min-max range from rules file.
    - State: `"Screen residue and set-up loss are not included."`
  - **PART E — Report layout, rule integration & Tamil localization**
