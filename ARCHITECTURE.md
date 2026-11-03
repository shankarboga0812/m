# Architecture

- `domain/` pure Kotlin, no Android imports: scoring, question model/validation/dedupe, template generators, practice/exam state machines,
  Error Monster, 1-4-7 revision, XP/levels/ranks/achievements, alarms model, quest evaluation, lessons, bank codec. JVM-tested.
- `data/` Room entities/DAOs (`data/db`), mappers, repositories (Content, Plan, Progress, Question supply, Study, AI), BankInstaller, BackupManager, SettingsStore.
- `net/` HttpURLConnection transport behind `AiTransport`.
- `alarms/` AlarmManager scheduler (exact when permitted, otherwise inexact), receivers, boot/maintenance rescheduling.
- `ui/` Compose theme, Canvas-drawn original art, components, ViewModels, 21 screens, `Nav.kt`.
- `backend/` AI proxy (generate-questions, explain, lesson, Telegram relay).

Key rules: Room migrations only (never destructive); XP awarded once per unique source key; AI output is validated, de-duplicated,
labelled and never alters scores; exam clock uses real time; flagged questions are excluded from reports, not penalised.
Question supply: optional AI -> unseen bundled bank questions -> offline generator.
Bank: `question_bank_v1.json`, 250 per subject x 12 subjects (150 practice / 60 exam / 40 mock), computed variants of ~80 templates.
