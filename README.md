# Mock Exam — Android source project

An offline-first Android MCQ mock-test app. Question banks are imported from `.xlsx` files through Android's Storage Access Framework. Test attempts, question snapshots, answers, flags, and bookmarks are persisted in a local Room database.

## Current implementation

- Import `.xlsx` question banks and validate the required headers and every non-empty question row.
- Support standard OOXML shared strings / inline strings and embedded PNG, JPG/JPEG, GIF, or WebP images anchored to the corresponding row in the `Image` column.
- Provide in-app export of the blank template and sample question workbook.
- Configure all questions or explicitly select individual questions.
- Configure an overall timer or an individual timer per question.
- Configure positive, negative, and skipped-question marks, including a negative-marking toggle.
- One-question-at-a-time exam UI, navigation, question map, bookmark and flag controls, countdowns, manual submit confirmation, and automatic submission on timeout.
- Persist results and test settings; reopen completed attempts from history.
- Review correct answers and original explanations; browse bookmarked, incorrect, and skipped questions across submitted attempts.
- Filter the review list by source question bank.

## Excel contract

The first worksheet named `Questions` is used. When there is no worksheet with that name, the first worksheet is used. Row 1 must contain these headers (case-insensitive; surrounding spaces are ignored):

`Question_ID | Question | Image | Option_A | Option_B | Option_C | Option_D | Correct_Answer | Explanation`

Rules:

1. `Question_ID`, `Question`, all four options, `Correct_Answer`, and `Explanation` are required.
2. IDs must be unique within a workbook (case-insensitive comparison).
3. `Correct_Answer` must be exactly one of `A`, `B`, `C`, or `D`.
4. Fully empty rows are ignored. Partially completed rows are errors.
5. The import is all-or-nothing. The app reports row-specific errors and does not save any questions from an invalid workbook.
6. Maximum workbook size: 30 MB. Maximum 2,000 questions. Each embedded image is limited to 8 MB and all extracted images together are limited to 20 MB.
7. For images, insert/embed the picture in Excel and position its top-left anchor in the `Image` column on the same row as its question. Alternatively, put the exact filename/path of an image that is embedded in the workbook package in the `Image` cell. External file-system paths and HTTP(S) URLs are not downloaded.
8. Supported image formats: PNG, JPG/JPEG, GIF, and WebP. Legacy `.xls` is not supported; save as `.xlsx`.

## Scoring rules

For each question:

- Correct: `+positiveMarks`
- Incorrect: `-negativeMarks` when negative marking is enabled; otherwise `0`
- Skipped: `+skippedMarks` (defaults to `0`)

`totalScore = correct awards + skipped awards - enabled incorrect deductions`.

`maxPossibleMarks = questionCount × max(positiveMarks, skippedMarks, 0)`.

`accuracy = correct / (correct + incorrect) × 100`; skipped questions are excluded from the accuracy denominator. If there are no attempted questions, accuracy is 0%.

## Architecture

- **UI:** Jetpack Compose Material 3; Navigation Compose; Activity Result APIs for `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT`.
- **Import:** a small Android OOXML reader built on `ZipFile` and `XmlPullParser`. It reads worksheet relationships, shared strings, cell values, drawing relationships and embedded image parts without a network connection or a server.
- **Domain:** scoring is isolated in `domain/Scoring.kt` and has JVM unit tests.
- **Persistence:** Room (`mock_exam.db`) with question banks, imported questions, global bookmarks, test attempts and per-attempt question snapshots.
- **Question snapshots:** each attempt stores its own question text, options, image path, correct answer and explanation, so reopening previous tests does not depend on a later import or a changed source workbook.
- **Images:** embedded images are extracted to the app's private `filesDir/question_images/<question-set-id>/` directory. The app needs no broad storage permission.

### Database tables

| Table | Purpose |
|---|---|
| `question_sets` | Source workbook metadata and import date |
| `questions` | Canonical imported questions, keyed by question-set ID + question ID |
| `question_bookmarks` | Durable bookmarks independent of individual test attempts |
| `test_attempts` | Test configuration, deadline, submission state, score and summary metrics |
| `attempt_questions` | Immutable question snapshots plus selected answer, flag, expiry and per-question deadline |

## Build and install

This source project targets Android API 35, minimum API 26, JDK 17, Kotlin 2.0.21, Android Gradle Plugin 8.7.3, and Gradle 8.9.

1. Install Android Studio (Ladybug or newer), Android SDK Platform 35, Android Build Tools 35.x, and JDK 17.
2. Open the `MockExamAndroid` folder in Android Studio and allow Gradle sync. If Android Studio asks to install missing SDK components, accept the API 35 components.
3. Run the `app` configuration on an Android 8.0+ emulator/device, or choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
5. To install from a terminal once the Android SDK / platform tools are installed: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The repository does not include generated Gradle wrapper binaries. Configure Gradle 8.9 in Android Studio, or install Gradle 8.9 locally and run `gradle :app:assembleDebug` from the project root.

## Verification performed in the authoring environment

- The pure Kotlin scoring engine was compiled with the available Kotlin compiler and exercised with a smoke test containing one correct, one incorrect, and one skipped response. Expected score (3/12) and 50% accuracy were confirmed.
- The pure Kotlin OOXML parser was compiled and run directly against the actual sample workbook. It imported all five rows and extracted the embedded PNG (6,409 bytes) from row 2, column C (`Image`).
- Additional parser checks confirmed row-specific rejection of a duplicate question ID, an invalid correct-answer value, and a zero-byte embedded image. The invalid workbooks were rejected rather than partially imported.
- The blank and sample `.xlsx` files were exported and inspected as OOXML ZIP packages. The sample workbook contains its embedded PNG and worksheet/drawing relationships; the image is anchored at row 2, column C (`Image`).
- The Android source project, database, Compose UI, and importer are included. An Android APK build and emulator/device workflow could not be executed in this environment because Android SDK, Android Gradle Plugin/Gradle, and adb are not installed. The full Android build, Room code generation, UI compilation and the 10-step device QA checklist therefore remain to be run in Android Studio; they are not claimed as tested here.

## Suggested device QA checklist

1. Export the blank template and populate it with valid MCQs; insert an embedded image into the Image column of a question row.
2. Import the populated `.xlsx`; confirm question count and image rendering.
3. Try a workbook with a duplicate ID, missing option, invalid correct-answer letter, and partial row; confirm row-level errors and that nothing is imported.
4. Configure a test with a selected subset, positive marks, negative marks, skipped marks, and negative marking on.
5. Answer one correctly, one incorrectly, and leave one unanswered; submit manually and verify the score breakdown.
6. Repeat with negative marking disabled and verify no incorrect-answer deduction.
7. Set a short overall timer and verify automatic submission at expiry.
8. Set a short per-question timer and verify an unanswered question locks and the test advances; verify expiry on the final question submits.
9. Reopen the completed attempt from history and inspect every answer and explanation.
10. Bookmark questions in multiple tests, review bookmarked / incorrect / skipped tabs, close and relaunch the app, and verify persisted records.
