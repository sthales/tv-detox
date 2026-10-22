# Home additions: TCL button remap, image wallpaper, ambient view, notification bell

Date: 2026-10-22. Fork: `sthales/tv-detox` (from Ansub/tv-detox v1.5.2). Owner: the TV's user, a software engineer who drives everything from the terminal over adb.

## Why

The TV (TCL Smart TV Pro G15, Google TV 14, 2.6 GB RAM, 32-bit Realtek SoC) now runs the TV Detox home with Google TV's home (`launcherx`) and setup app (`setupwraith`) turned off. That freed about 220 MB and most of the idle CPU. A few things Google's home did, or the user wants, are missing:

- The **TCL button** on the remote is dead (it opened TCL's content hub, which is turned off). The user wants it to open **Stremio**.
- The user wants **their own image** as the home's wallpaper, not only the built-in gradients.
- On Google's home, **Back** started the screensaver. Starting a dream needs a system-only permission (tested: `Somnambulator` finishes without dreaming, `cmd dreams` needs root), so the home gets its **own ambient view** on Back instead.
- Google's notification panel went with its home. The user wants a **notification bell** on the home.

## Goals

1. **TCL button → Stremio**, from anywhere: the home, any app, the PS5 HDMI input.
2. **Image wallpaper**, sent from the Mac with one command and selectable on the TV next to the gradients.
3. **Back on the home → ambient view**: full-screen wallpaper with a small clock; any button returns.
4. **Notification bell**: an icon by the clock with a dot for new notifications, and a side panel listing them.
5. **HDMI hide in the home.** Add **Hide** to the hold-OK menu of HDMI tiles. The user chose this over TCL's own Inputs settings. Inputs hidden by the TV's own settings (`TvInputInfo.isHidden`) stay hidden as today.

## Non-goals (for now)

- Replacing Google's screensaver (`dreamx`). It stays as it is; a custom dream may come later.
- Remapping any other button. The mic button (`KEYCODE_SEARCH`, 84) is free, but its use is undecided.
- Automatic HDMI hiding. TCL reports all 4 ports as connected (`state 1`, `cable_connection_status=1`) with nothing plugged in, so input state can't be trusted.
- Automatic CEC names ("PS5"). The user renames the port in TV settings, and the home already shows custom labels.
- Home Assistant integration, video wallpapers, upstreaming to Ansub/tv-detox.

## Anti-goals (failure even if it works)

- Anything that could stop the TV booting or leave Home unresolved. No extra system apps turned off.
- RAM creep. The home is about 30 MB, and the additions should keep it near that. An image wallpaper is decoded once at screen size.
- New libraries, Gradle, or a second app. It stays plain Java in the one APK, built by `build.sh`.
- Breaking what works today: the quick panel (TCL key 4514 → TV Settings), the other app buttons (Netflix 4062, Prime, YouTube, Globoplay, Disney+), AirPlay, casting, the phone remote, Home Assistant's Android TV Remote integration, and HDMI-CEC.

## Facts measured on this TV

| Thing | Value |
|---|---|
| TCL button | HID usage `0x0c0053` → TCL keycode **4020** (`TclKeyLayoutMap`, `shortcutkey.tkl`). The firmware's `TclShortCutKeyManager` used to launch `com.tcl.waterfall.overseas` (now disabled). |
| Other TCL app keys | Netflix 4062. Prime, YouTube, Globoplay and Disney+ use other TCL custom codes. Keys reach the foreground app's `ViewRootImpl` as `keyCode=4062` and so on. |
| Mic | `0x0c0221` → `KEYCODE_SEARCH` (84). Down and up arrive 0.2 ms apart, so there is no long press. |
| Stremio | package `com.stremio.one`, has a leanback launch intent |
| HDMI inputs | `com.tcl.tvinput/.TvPassThroughService/HW15..HW18` = HDMI 1..4 |
| Notifications seen today | Nearly all are ongoing or system ones ("Wireless debugging connected", "AirPlay running", Live TV and miracast services) |
| Android | 14 / SDK 34, 1920×1080 UI, Wireless debugging over TLS. Port 5555 can be opened with `adb tcpip`, but it does not survive a reboot. |

## Design decisions (the user's)

- TCL → Stremio; the other buttons stay as they are.
- Wallpaper images go to the TV **from the terminal**. Picking from USB on the TV is not needed.
- Google's screensaver stays. Back gives the home's own ambient view.
- The bell is in.

## How (decided by Claude; the user can override)

**1. Key remap.** An `AccessibilityService` in the same APK (e.g. `KeyService`) with `canRequestFilterKeyEvents`. `onKeyEvent` looks the key code up in a small map saved in the app's prefs and, on key-up, starts that package's launch intent with `FLAG_ACTIVITY_NEW_TASK`. It returns `true` for mapped keys (down and up) and `false` for everything else, so unmapped keys are untouched.
- The map is set the same way the app list is today: an `am start` extra, e.g. `--es keys "4020:com.stremio.one"`, saved by `saveConfig` with the same stamp logic.
- Enabling it: `install.sh` (or a small script) writes `enabled_accessibility_services`, appending to any existing value, and `accessibility_enabled=1` over adb.
- **Risk to verify first.** This is the first build task, and it's gated: confirm that key 4020 reaches the accessibility key filter.
  - **Why it should work:** the log shows TCL keys being dispatched to the foreground app (`ViewRootImpl … keyCode=4062`). That means the key passed `interceptKeyBeforeQueueing` with pass-to-user set, and the accessibility input filter sees those events.
  - **Fallback if it doesn't** (Claude's call): the remap works **on the home only**, via `MainActivity.onKeyDown`, which receives the same key code. Tell the user it doesn't work from inside other apps.
  - **Not an option:** re-enabling `com.tcl.waterfall.overseas` to watch for its launch. That costs RAM and flashes TCL's hub on screen.

**2. Image wallpaper.** A `wallpaper.sh <TV> <image>` script:
- pushes the file to a folder the app can read (e.g. `/sdcard/Pictures/tvhome/`)
- grants read permission with `pm grant` (`READ_MEDIA_IMAGES` on 33+, `READ_EXTERNAL_STORAGE` below)
- selects it with an `am start` extra (e.g. `--es wallpaper "image:<name>"`)

On the TV, every image in that folder shows up in *Settings → Wallpaper* after the gradients, with the same preview-on-focus and OK/Back behaviour. The app decodes the chosen image once, scaled and center-cropped to the screen, and draws it as the window background. On top it adds a subtle dark gradient (stronger top-left behind the clock, and at the bottom behind the rows) so white text stays readable. If the file is missing, it falls back to Dusk.

**3. Ambient view.** In `onBackPressed`, which currently does nothing, hide the page content and show only the wallpaper and a small, dimmed clock (bottom-right, `TextClock`). The next key of any kind restores the page and is consumed, so it doesn't also trigger a tile. Google's screensaver still starts after the system idle timeout as before.

**4. Bell.**
- A `NotificationListenerService` in the same APK, granted over adb with `cmd notification allow_listener com.ansub.tvhome/<service>`.
- **What it shows (the user's decision):** everything, including ongoing system ones like "AirPlay running" and "Wireless debugging connected". The only thing skipped is group summaries, which duplicate their children.
- **Icon:** a bell icon (`LineIcon` style) in the existing top-right icon row, left of the network icon. It gets a dot when a notification was posted after the panel was last opened. Ongoing ones that are always there don't keep the dot lit.
- **Panel:** OK opens the existing `panel()` side panel with app name, title and text, newest first. OK on a row sends its `contentIntent`, and if the notification is clearable it is also dismissed. Ongoing ones stay. A last row, "Clear all", cancels all clearable ones. An empty state reads "No notifications".
- If the permission isn't granted, the bell is hidden.

**5. HDMI hide.** HDMI tiles get the hold-OK/Menu dropdown with **Hide**. Hidden input ids are saved in prefs and skipped in `hdmiInputs()`. **Settings → "Show hidden inputs"** brings them back.

**Install and upgrade.** We build with `build.sh` and our own `key.jks`, which is a different signature from the installed release. `install.sh` then uninstalls and reinstalls, losing the saved app order, and the home role is re-set by the script. While the old APK is gone, Home briefly falls back to TV Settings' `FallbackHome`; that's acceptable. After install, re-run with the app list, the key map, and the permission grants. Keep `key.jks`, because later updates must use the same key.

## Left to the builder

- Class and file names, how to split `MainActivity.java` (keep the existing style: plain Java, no comments beyond the existing density, `Strings.t()` for labels).
- The exact bell icon drawing, dot size, gradient strengths, and the clock size in the ambient view.
- Whether to extend `install.sh` or add a separate setup script for the accessibility, notification and media grants.
- Tests: extend `ci/smoke-test.sh` where it can check things (service enabled, listener granted, wallpaper extra applied), plus a manual on-TV checklist for the remote.

## Done means

On this TV, after a reboot:
1. The TCL button opens Stremio from the home, from inside YouTube, and from the PS5 input.
2. The other app buttons and the quick panel behave as before.
3. A pushed image is the wallpaper and appears in the Wallpaper list.
4. Back on the home shows the ambient view, and any button returns.
5. The bell lists AirPlay and debugging, and shows, opens and clears a test notification.
6. The home's RAM (PSS) stays around 30–40 MB with an image wallpaper.
