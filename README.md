# 😈 Evil VisionCamera 😈

**A modified, unstable, and dangerous fork of VisionCamera designed to bypass Android Manufacturer Vendor Locking.**

<div>
  <img align="right" width="35%" src="docs/static/img/example.png">
</div>

### Why "Evil"?

Standard Android Camera APIs often hide auxiliary lenses (Ultra-wide, Telephoto, Macro) behind "hidden" IDs (e.g., ID 2, 21, 52, 100) to prevent third-party apps from using them. This library breaks those chains.

**This fork implements Brute-Force Discovery:**
*   Iterates Camera IDs from `0` to `150`.
*   Ignores `INFO_SUPPORTED_HARDWARE_LEVEL` safety checks.
*   Bypasses CameraX for initial discovery to access raw `CameraCharacteristics`.

### ⚠️ WARNING: HIGHLY UNSTABLE

**This library is NOT safe for production.**
*   It may crash your app immediately upon launch.
*   It utilizes undocumented camera IDs that may not return valid stream configurations.
*   Using "Evil" cameras disables standard CameraX features (Extensions, High-Speed Video).

---
