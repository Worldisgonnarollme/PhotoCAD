# Photo Album Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task by task, with a failing test before product code.

**Goal:** Add a five-step photo album flow with cover, general information, one photograph per page, and safe post-save archiving.

**Architecture:** Keep `PdfReportGenerator` for the simple report. Add immutable album input and focused renderers. `ReportViewModel` owns state and persistence; renderers use prepared values only.

**Tech Stack:** Kotlin, Compose, Room, Android PdfDocument, coroutines.

**Spec:** `docs/superpowers/specs/2026-10-07-photo-album-design.md` and the user's attached request.

## Global constraints

- Preserve the simple report, registration, profile, and existing data.
- A4 portrait; images retain aspect ratio; no new heavy PDF/UI dependency.
- Room migration is additive, 6 to 7, with exported schema.
- Do not archive until `ReportFileManager.save` succeeds.

## Tasks

### 1. Point state and rules

Files: `Entities.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `PointColors.kt`, migration tests, report rule tests.

- [ ] Write tests for color persistence, archive default, active drawing query, and partial photo selection.
- [ ] Run tests and confirm the expected failure.
- [ ] Add `isArchived`, migration 6→7, active drawing query, selected-photo archive rule, and shared ARGB palette.
- [ ] Run unit and migration tests; commit.

### 2. Album input and page renderers

Files: `PhotoAlbumModels.kt`, `CoverPageRenderer.kt`, `GeneralInfoPageRenderer.kt`, `PhotoPointPageRenderer.kt`, `PhotoAlbumGenerator.kt`, `res/raw/general_information.txt`, rendering tests.

- [ ] Write failing tests for caption handoff, page count/order, cover text, Cyrillic, point crop/color, multiple photos, and failure cleanup.
- [ ] Run tests and confirm the expected failure.
- [ ] Implement immutable album input and focused renderers using the existing file and image helpers.
- [ ] Run tests; commit in model/page/generator groups.

### 3. Wizard and export lifecycle

Files: `ReportViewModel.kt`, `ReportScreen.kt`, `ReportModels.kt`, `ReportRules.java`, `DrawingScreen.kt`, wizard and archive tests.

- [ ] Write failing tests for caption source, empty caption warning, selection/order, page preview, and archive after success only.
- [ ] Run tests and confirm the expected failure.
- [ ] Build document type, cover, content, preview, and completion steps; preserve simple report.
- [ ] Verify save cancellation/failure leave points active; commit.

### 4. Final verification

- [ ] Run Gradle build, unit tests, available instrumentation/migration tests.
- [ ] Generate and inspect a real sample PDF if an Android runtime is available.
- [ ] Review branch diff and report files, tests, limits, and device checks.

## Review focus

- Point at a source-page edge remains visible after cropping.
- A point with three photos and only two selected remains active.
- A photo added after generation prevents archiving its point.
- Export cancellation/failure leaves all point states unchanged.
- Long Cyrillic text never silently clips or disappears.
