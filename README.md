<p align="center">
  <img src=".github/assets/thyme-banner-github.png" alt="Thyme banner" width="100%" />
</p>

# Thyme

*yes it has no icon yet :(*

> [!WARNING]
> Thyme is a work in progress. Things may change or break !!!

## Install

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22:%22dev.whayn.thyme%22%2C%22url%22:%22https://github.com/whayn/thyme%22%2C%22author%22:%22whayn%22%2C%22name%22:%22Thyme%22%2C%22preferredApkIndex%22:0%2C%22additionalSettings%22:%22%7B%5C%22includePrereleases%5C%22:false%2C%5C%22fallbackToOlderReleases%5C%22:true%2C%5C%22filterReleaseTitlesByRegEx%5C%22:%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22:%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22:false%2C%5C%22sortMethodChoice%5C%22:%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22:false%2C%5C%22releaseTitleAsVersion%5C%22:false%2C%5C%22trackOnly%5C%22:false%2C%5C%22versionExtractionRegEx%5C%22:%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22:%5C%22%5C%22%2C%5C%22versionDetection%5C%22:true%2C%5C%22releaseDateAsVersion%5C%22:false%2C%5C%22useVersionCodeAsOSVersion%5C%22:false%2C%5C%22apkFilterRegEx%5C%22:%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22:false%2C%5C%22autoApkFilterByArch%5C%22:true%2C%5C%22appName%5C%22:%5C%22%5C%22%2C%5C%22appAuthor%5C%22:%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22:false%2C%5C%22allowInsecure%5C%22:false%2C%5C%22exemptFromBackgroundUpdates%5C%22:false%2C%5C%22skipUpdateNotifications%5C%22:false%2C%5C%22about%5C%22:%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22:false%2C%5C%22includeZips%5C%22:false%2C%5C%22zippedApkFilterRegEx%5C%22:%5C%22%5C%22%7D%22%2C%22overrideSource%22:null%7D">
  <img src="https://raw.githubusercontent.com/ImranR98/Obtainium/b1c8ac6f2ab08497189721a788a5763e28ff64cd/assets/graphics/badge_obtainium.png"
    alt="Get it on Obtainium"
    height="80">
</a>

Or get the latest APK in the [Releases](https://github.com/whayn/thyme/releases) page.

## Features

- **A day at a glance** — today's doses laid out morning to night, with a week strip to flip through days. One tap logs a dose; your history is a real event log, not a checkbox.
- **Flexible schedules** — specific weekdays, every-N-days intervals, on/off cycles (think 21-on/7-off), and multiple concurrent courses per medication for tapers.
- **Nights belong to tomorrow** — the day rolls over at 05:00, so a 02:00 dose shows up as tonight's last item, not yesterday's.
- **Adherence stats** — per-medication adherence and a month calendar of how things actually went.
- **Graceful endings** — stopping a course keeps its history; deleting never orphans your logs.
- **Looks like home** — Material 3 with dynamic color, light and dark.
- **Private by construction** — no accounts, no analytics, and the app requests *zero* permissions. There is no internet permission, so your data physically cannot leave the device.

## Build

You'll need [Android Studio](https://developer.android.com/studio) (or a JDK 21 + Android SDK setup) and Git.

```bash
git clone https://github.com/whayn/thyme.git
cd thyme
./gradlew installDebug   # build and install on a connected device/emulator
```

Other useful tasks:

```bash
./gradlew test     # unit tests
./gradlew lint     # Android lint
```

Tip: if Gradle claims everything is up-to-date right after a source edit, add `--rerun-tasks` — KSP sometimes skips regeneration otherwise, and Room's query checks only surface as warnings.

Minimum supported Android version is 8.0 (API 26).

## Roadmap

- [ ] An actual app icon
- [ ] Dose reminders with escalation and skip reasons *(in development, on [`feature/alerts`](https://github.com/whayn/thyme/tree/feature/alerts))*
- [ ] Distribution via F-Droid / IzzyOnDroid
- [ ] Data export and backup

## License

[GPL-2.0](LICENSE)