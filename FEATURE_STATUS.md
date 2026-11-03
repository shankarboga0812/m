# Feature status (honest)

Verification levels: **T** = unit-tested on JVM/Node, **C** = compiled against stubs only, **U** = written, never compiled or run.

| Area | Status |
|---|---|
| Scoring, validation, dedupe, generators, Error Monster, 1-4-7, XP/rank, quest evaluation, exam/practice state, mock blueprint, alarm model, lesson codec, bank codec | T (111 tests) |
| AI backend endpoints, auth, rate limit, validation | T (12 tests) |
| Repositories, mappers, settings, ViewModels | C (type-checked vs stubs; Room SQL not executed) |
| Room schema/queries, Compose screens (21), navigation, alarms/notifications/boot, backup/restore, MainActivity | U - first real build happens in GitHub Actions |
| GitHub Actions workflow | U |

## Known limitations
- The Android layer has not been built or run. Expect to fix some compile errors on the first Actions run.
- Only 6 lessons are hand-curated; other topics show a skeleton or an AI lesson (needs backend).
- Bank questions are computed value-variants of templates; no real PYQs are bundled (verified PYQ slot exists but is empty).
- Only 5:00, 5:15, 20:30, 21:00 alarm times come from the plan document; other alarms stay off until you choose a time.
- Workout calendar in the doc and its weekday table disagree; the calendar is followed. Workout is manual only.
- AI is used for Prep/Revision/GA supply and explanations, not in exams.
- Android may delay or block alarms (battery optimisation, exact-alarm permission); the app says so in Alarm settings.
- Art is Canvas-drawn (no image generation available).
- MSQ has no partial credit by default; MCQ negative = marks/3.
