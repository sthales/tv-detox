# Stremio shelf on the home

Approved in chat on 2026-10-23 from the mock-ups (A5a with posters, details only on the focused poster).

## Why
Continue what you were watching in Stremio and find something popular without opening Stremio first,
while the home keeps its clear wallpaper the rest of the time.

## Behaviour
- The home is unchanged. Up from the Stremio tile opens the shelf (Up from other tiles still reaches the chip).
- The shelf fills the screen: the focused title's background art, a left and bottom dark gradient, a small
  clock top right, the title's logo (or its name), then `IMDb 8.4  2022– · Drama · Thriller` (plus `S4 · E3` on
  Continue items) and a two-line description.
- Row 1 "Continue watching": the account's library items Stremio itself shows there
  (`type != other && (!removed || temp) && state.timeOffset > 0`), newest `state.lastWatched` first, up to 10.
- Row 2 "Popular on Stremio": Cinemeta top movies and top series interleaved, minus anything in the library,
  up to 10. It peeks at the bottom; Down slides the rows up so it shows fully.
- Posters are clean. Only the focused one shows, under it, a progress bar and `S4 · E3` / `1h 12m left`
  (Continue) or the year (Popular). Focus scales it 1.1 with a white outline.
- OK opens the title in Stremio (`stremio:///detail/<type>/<id>[/<videoId>]`). Back, or Down from row 2,
  returns to the apps with focus on the Stremio tile. Leaving the home closes it.
- Without a linked account the shelf shows Popular only.

## Data
- Account: `stremio.sh <TV>` on the Mac asks for email and password, logs in at `api.strem.io/api/login` and
  sends only the authKey (`--es stremio_key <key>`, `off` unlinks). The password is never stored.
- Library: `api.strem.io/api/datastoreGet` (collection `libraryItem`, all). Details for Continue items:
  `v3-cinemeta.strem.io/meta/<type>/<id>.json`. Popular: `v3-cinemeta.strem.io/catalog/<type>/top.json`.
- Refreshed in the background when the home resumes, at most once a minute, into `stremio/shelf.json`, so the
  shelf opens instantly and still works offline. Images are cached on disk by URL.

## Calls made by the builder
- Up from the Stremio tile no longer reaches the chip; the other tiles still do.
- The authKey lives in the home's prefs, so `backup.sh` copies it into `backups/` on the Mac (git-ignored).
