# ValueLens Android (scaffold)

Kotlin / Jetpack Compose skeleton. Not yet built or run — Sprint 1 work.

- `domain/Models.kt` — kotlinx.serialization mirror of the engine contract (`docs/API.md`)
- `data/EngineApi.kt` — OkHttp client with `X-SEC-User-Agent` forwarding
- `ui/ValueLensApp.kt` — theme matching iOS + placeholder screen

Open in Android Studio (Ladybug or newer) and let it generate the Gradle wrapper, or run
`gradle wrapper --gradle-version 8.11` first. Screens to port are listed in `docs/TASKS.md`.
