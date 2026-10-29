# Developer notes — Blast Pixel Dungeon

This page documents version checks and CI release workflows for maintainers. The [main README](../README.md) retains the original player-facing gameplay information.

## Version updates

The Blast version is set **manually** in `core/src/main/java/com/spd/blast/BlastVersion.java` (`BlastVersion.VERSION`). Neither the build nor release workflow increments it automatically.

Release tags must follow `Blast-v<version>-SPD-v<upstream-version>`. To notify players of a new Blast version, manually change `VERSION` and publish a non-draft, non-prerelease GitHub Release with the new version in its tag. Replacing APK assets under an unchanged Blast version does not trigger a notice.

The title screen's bottom-right label and the in-game top-right menu pane both use `BlastVersion.display()`. When a newer published version is found, both show `v<installed-version> | new <version>` (for example, `v0.1.0 | new 0.1.1`), or simply `v<installed-version>` when no newer release is available. Checks are non-blocking, occur when either screen opens, are limited to once an hour following success, and are retried on a later opening following failure. Draft releases, prereleases and unrelated tags such as `build-test` are excluded. Standalone and SMM builds display the same Blast update notice.

## Build and release policy

Blast versions are set **manually** in `BlastVersion.VERSION`. Neither workflow increments this value.

- **Scheduled Release Blast:** At 13:00 Taiwan time each day, compare the **pair** of manually assigned Blast version and latest published SPD version with the combinations already released. If the exact pair exists, skip compilation and leave Releases unchanged. If either Blast or SPD has a new version and the pair has not been released, compile four standalone/SMM APK/JAR assets and create one official Release. Changes to Blast source or the SMM version alone do **not** trigger scheduled builds. Scheduled runs never overwrite an existing official Release.
- **Manual Release Blast:** Always compile all four APK/JAR assets, even when every version and source is unchanged. Create a new official Release if its tag does not exist; otherwise overwrite its APK/JAR assets, refresh notes, remove obsolete APK/JAR filenames, and move its tag to the rebuilt commit. Replaced asset download counts reset.
- **Manual Build Blast:** Always compile all four APK/JAR assets and update the rolling `build-test` prerelease. **There is no scheduled test build.** Replacing test assets may reset their download counts.

Official Release builds use the latest published SPD and SMM versions. Manual test builds use the latest published SPD version and SMM `main`. The normal one-hour separation from SMM's scheduled release remains in the official Release Blast schedule.

Setting Blast to an older version than the latest published official Blast release fails immediately. Rebuilding the same Blast version does not trigger a new in-game version notification.

