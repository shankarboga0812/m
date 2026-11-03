# GATE Solo Leveling AI System 2027

A standalone Android app (Kotlin, Jetpack Compose, Material 3, Room) for the 114-day GATE CS 2027 plan
(11 Oct 2026 - 1 Feb 2027, target 85/100). Application id `com.chandu.gatesystem.ai2027`.
It is **not** an upgrade of the V7.x app and shares no code or data with it.

## Build
```
./gradlew testDebugUnitTest assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. Requires JDK 17 and the Android SDK (platform 34).

### GitHub Actions
`.github/workflows/gate-ai2027-android.yml` runs unit tests, builds the debug APK and uploads it as the
artifact **gate-ai2027-debug-apk** (Actions tab -> the run -> Artifacts). It also runs the backend tests.
Set `working-directory` in the workflow if the project is not in `GATE_Solo_Leveling_AI_2027/`.

### Local checks that do not need the Android SDK
- `bash tools/run_domain_tests.sh` - 111 pure-Kotlin domain tests.
- `bash tools/typecheck_data.sh` - compiles the data layer against stubs (BackupManager is expected to be skipped/fail here).
- `cd backend && npm test` - 12 backend tests.
- `bash tools/gen_bank.sh` - regenerates `question_bank_v1.json`.

## AI backend
`backend/` is a dependency-free Node 22 server that holds the AI provider key and (optionally) the Telegram bot token.
See `backend/.env.example` (placeholders only). Deploy: `docker build -t gate-ai2027 backend && docker run -p 8787:8787 --env-file backend/.env gate-ai2027`
or `node backend/server.js` on any host. In the app: AI Teacher settings -> backend URL + app token. No keys live in the APK or repo.

## Safe integration into github.com/Chandhuboga/gate-solo-leveling
Nothing is pushed for you. Suggested, non-destructive steps:
```
git clone https://github.com/Chandhuboga/gate-solo-leveling && cd gate-solo-leveling
git switch -c ai2027-system                       # new branch; main untouched
mkdir GATE_Solo_Leveling_AI_2027 && cp -r <unzipped>/. GATE_Solo_Leveling_AI_2027/
mv GATE_Solo_Leveling_AI_2027/.github/workflows/gate-ai2027-android.yml .github/workflows/   # new file name, replaces nothing
rm -r GATE_Solo_Leveling_AI_2027/.github
git add -A && git status    # review: only new paths should appear
git commit -m "Add GATE Solo Leveling AI 2027 (separate directory)" && git push -u origin ai2027-system
```
Review the diff before pushing; never force-push.

See `ARCHITECTURE.md` and `FEATURE_STATUS.md`.
