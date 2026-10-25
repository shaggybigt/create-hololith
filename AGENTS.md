# Hololith development

Work on the existing Minecraft 1.21.1 / NeoForge 21.1.259 / Create 6.0.10 targets.
Use Java 21 and the checked-in Gradle wrapper. Do not upgrade dependencies or
change unrelated mods as part of routine improvements. Optional Aeronautics and
Sable support must not prevent the Create-only client or dedicated server from
loading. Keep probe code out of the player JAR.

For moving contraption acceptance, prefer small public schematics with documented
controls and dependencies. Record creator/source/hash and prove the original
craft works on this pack before fitting Hololith. Use the procedure and local
inputs in `docs/public-schematics.md`. Retain focused synthetic rigs for regression
tests, but do not describe their controlled movement as powered flight. Report
compilation, unit tests, actual gameplay and untested scenarios separately.

Keep downloaded schematics, temporary worlds, raw captures and machine-specific
paths out of Git and player packages. Preserve existing licenses and releases.
Use a development branch for ongoing work. Do not publish releases, merge to main
or promote to communities without the user's authorization for that step.

Build with `gradlew.bat build probeJar` (Windows) or `./gradlew build probeJar`.
The optional PowerShell scripts use an explicitly supplied runtime root and
marked disposable worlds; never point them at an ordinary save.
