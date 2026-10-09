# Changelog

All notable changes to **Transcribe Offline Android** are documented here.

## 0.4.0-alpha1 — Offline writing assistant

Status: **experimental device benchmark**

### Added

- Fully local Qwen2.5 0.5B Instruct Q4_K_M text generation through `llama.cpp`.
- **✨ Mejorar redacción** after a Whisper transcription.
- Separate **Redactar** workspace: dictate a writing instruction, review/edit it, then generate the requested text locally.
- Shared local vocabulary context for both speech recognition and local writing so uncommon names and terms can be preserved.
- Editable generated text with copy/share actions and local generation timing.

### Privacy

- No OpenAI account, API key, server or cloud runtime.
- The Android app still requests no `INTERNET` permission.
- Whisper and Qwen inference remain on-device after installation.

### Compatibility

The local writing model is currently enabled on 64-bit Android devices. The existing Whisper transcription path keeps its broader compatibility.

### Size

The Qwen Q4_K_M model is approximately 491 MB, so this alpha must be tested on real target hardware before promotion to a stable release.

## 0.3.4 — Proper-name phonetic refinement

Status: **experimental device benchmark**

### Improved

- Multi-word vocabulary matching now tolerates a larger phonetic miss on the first token when the remaining name tokens match exactly.
- Specifically covers observed cases such as `ser María` → `Esther María`.
- Keeps the safeguard that the distinctive trailing part of the proper name must already match, reducing the risk of unrelated replacements.

### Performance

The refinement is a small local string comparison and adds negligible latency.

## 0.3.3 — Dictation typo refinement

Status: **experimental device benchmark**

### Improved

- Refined multi-word vocabulary matching so close variants such as `esta María` can resolve to `Esther María` when that name is present in the local vocabulary.
- Added a narrowly scoped post-correction for the observed dictation error `ditar` → `dictar`.
- Kept the correction layer conservative: it never inserts missing semantic content such as `mi hija` when Whisper did not transcribe it.

### Performance

These corrections are local string operations and add negligible latency compared with Whisper inference.

## 0.3.2 — Conservative vocabulary correction

Status: **experimental device benchmark**

### Added

- Local post-processing that corrects only near-matches against the user vocabulary.
- Multi-word proper-name recovery such as `Este María` → `Esther María`.
- Conservative matching thresholds to reduce accidental edits to ordinary words.
- Canonical casing and accent restoration for vocabulary entries.

### Performance

The correction runs locally after Whisper inference and is negligible compared with model inference time.

## 0.3.1 — Local vocabulary hints

Status: **experimental device benchmark**

### Added

- Editable local vocabulary for proper names and difficult terms.
- On-device persistence through Android SharedPreferences.
- Whisper `initial_prompt` support through the speech-engine abstraction and JNI bridge.
- Initial vocabulary: Esther María, Amarilys, Rodovaldo, Guillermina, Alejandro, Martín, Romel and Daniel.

### Privacy

The vocabulary is stored only on the phone and is never sent to a network service.

## 0.3.0 — Base accuracy experiment

Status: **experimental device benchmark**

### Changed

- Upgraded the on-device recognizer from Whisper Tiny Q5_1 to Whisper Base multilingual Q5_1.
- Increased packaged model size from roughly 32 MB to roughly 60 MB.
- Switched decoding from greedy sampling to a short beam search with beam size 3 and patience 1.0.
- Kept Spanish fixed at `es`, deterministic decoding, silence trimming and anti-hallucination safeguards.
- Preserved the same fully offline runtime and stable Android signing identity.

### Goal

Measure whether the larger model produces a meaningful improvement in natural Spanish dictation quality on older Android hardware without making processing latency impractical.

## 0.2.1 — Stable baseline

Status: **validated on real Android hardware**

### Added

- Fully local Whisper Tiny multilingual Q5_1 inference.
- Lightweight energy-based voice activity trimming before transcription.
- Visible audio duration, useful-voice duration, processing time and real-time factor (RTF).
- Adaptive launcher icon with modern Android monochrome support.
- Stable release-signing configuration kept outside the public repository.
- Professional project documentation focused on offline-first accessibility.

### Improved

- Leading and trailing silence are removed before inference to reduce unnecessary CPU work.
- Whisper decoding is deterministic with `temperature = 0`.
- Blank and non-speech tokens are suppressed more aggressively.
- No-speech handling is stricter to reduce hallucinations during silence.
- Native `whisper.cpp` code is optimized in Release builds.
- The inference engine remains isolated from the UI through `SpeechToTextEngine`.

### Distribution

The stable application identity uses a long-lived signing certificate.

Certificate SHA-256:

```text
F8:45:DB:90:80:06:0A:59:9D:80:00:99:34:52:93:D7:4C:7E:C4:24:51:49:E5:18:C8:43:0E:0D:AC:B4:B6:E0
```

Future stable APKs must use the same private signing key to install as updates over this version.

## 0.2.0 — First working offline build

- End-to-end microphone → local Whisper → editable text flow.
- Whisper Tiny Q5_1 bundled into the APK.
- Jetpack Compose / Material 3 interface.
- Copy and share actions.
- No runtime Internet permission.
- ARM64 and ARMv7 native support.
