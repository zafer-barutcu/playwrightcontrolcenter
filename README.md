# Playwright Control Center

Track, run, and replay your UI tests with live diagnostics, parallel runs, video capture, and history.

![UI Screenshot](poc.png)

## Features
- **Run Settings**: Maven command, environment, baseUrl, browser, headless, parallelism, video mode (fail/all/off), working directory.
- **Live Pulse**: Realtime total/passed/failed/running; donut and sparkline.
- **Scenario Stream**: Scenario list, steps, “Watch” video (in fail-only mode only failed scenarios show videos).
- **History**: Manage recent runs with Open/Delete.
- **Console Log**: Live SSE log stream.
- **DB Reset**: Wipe all runs and scenario data.

## Run (standalone jar)
1. Package: `mvn -DskipTests package`
2. Start: `java -jar target/Play-Ground-1.0-SNAPSHOT.jar`  
   (Change port if needed: `-Dserver.port=8090`)
3. Open: `http://localhost:8080`
4. Enter command in Run Settings (e.g., `mvn test`), pick parameters, click **Run**.

## Run the Spring Boot UI (dev mode)
- Dev server: `mvn spring-boot:run` (uses port 8080 by default)
- Custom port: `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090"`
- Then open `http://localhost:<port>` and use the UI as usual.

## Video Modes
- `fail`: Keep/show videos only for failed scenarios; delete pass videos.
- `all`: Keep/show all videos.
- `off`: Disable video recording.

## Drop-in QA Addon (fast integration into another project)
Copy `src/test/java/qaaddon` into your project:
- Formatter: `qaaddon.addon.reporting.RunCaptureFormatter`
- Playwright Guice config: `qaaddon.addon.config.{PlaywrightModule, TestConfig}`
- Video hook: `qaaddon.addon.hooks.PlaywrightHooks`

Add plugin to your runner:
```java
@ConfigurationParameter(
  key = PLUGIN_PROPERTY_NAME,
  value = "pretty,summary,qaaddon.addon.reporting.RunCaptureFormatter"
)
```
System props supported: `env, baseUrl, browserName, headless, videoMode, run.id, parallelism`.

## Repo Fetch (optional)
Use Settings modal to fetch GitHub/Bitbucket repos with token/branch into `target/repos/...`, select as working dir, and run tests there.
