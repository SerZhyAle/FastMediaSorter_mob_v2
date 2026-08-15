# PHASE 02 - Wire entry point into add-resource

Goal: a scanned payload string flows through the existing companion import and creates the resource(s). Add the button + launcher + camera-feature visibility gate.

## Steps

1. Payload entry on the use-case.
   - File: `ImportCompanionConfigUseCase.kt`.
   - Add `suspend fun importFromPayload(payload: String): Result<CompanionImportResult>` = `import(parser.parse(payload))`, wrapped in the same try/catch as the `Uri` overload (reject -> `Result.failure`).
   - Verification: compiles; reuses `import(dto)`; no duplication of the mapping logic.

2. Coordinator payload method.
   - File: `AddResourceCompanionCoordinator.kt`.
   - Add `importFromPayload(payload: String)` mirroring `importFromUri` (markLoading, call `importCompanionConfigUseCase.importFromPayload`, emit success message + `ResourcesAdded` / error).
   - Verification: compiles; same event surface as the file path.

3. ViewModel method.
   - File: `AddResourceViewModel.kt`.
   - Add `fun importCompanionConfigFromQr(payload: String)` delegating to the coordinator (mirror `importCompanionConfig(uri)`).
   - Verification: compiles.

4. Activity launcher + button + gate.
   - File: `AddResourceActivity.kt`.
   - `companionQrScanLauncher = registerForActivityResult(StartActivityForResult)` -> on `RESULT_OK` read `EXTRA_PAYLOAD` -> `viewModel.importCompanionConfigFromQr(payload)`.
   - `binding.btnSftpScanCompanionQr.setOnClickListener { launch CompanionQrScanActivity.createIntent(this) }` with `UserActionLogger.logButtonClick`.
   - Visibility gate in `setupViews()` (or form manager): `btnSftpScanCompanionQr.isVisible = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)` - hides on Quest/VR (ADR-2) and camera-less devices. No BuildConfig flavor guard (Rule 14).
   - Verification: compiles; button hidden when no camera.

5. Layout button.
   - File: `app_v2/src/main/res/layout/activity_add_resource.xml` (no `layout-land` counterpart exists - Rule 11 satisfied).
   - Add `MaterialButton android:id="@+id/btnSftpScanCompanionQr"` right after `btnSftpImportCompanion`, same style, `android:text="@string/companion_qr_scan_button"`, `app:icon` = a scan/qr icon (reuse existing camera/scan drawable).
   - Verification: `fr` passes; binding field generated.
