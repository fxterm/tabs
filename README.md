# Tabs

Full name **FxTerminalTabs**; we simply call it **Tabs**. The project and its files are named `fxterm-tabs` (organisation `fxterm`).

A Windows terminal with vertical tabs, made for long sessions with AI coding agents such as Claude Code: tabs sleep and wake like a browser's, every session can be found again in History, and several windows share one process.

Status: 0.1.0, the first release. Windows only.

## What it does

- **Vertical tabs.** Numbered, colourable, renamable, three lines each (title, folder, path). Drag to reorder (or Ctrl+Shift+PgUp/PgDn); drag a tab out of the window to open it in a new window, or onto another window's list; right-click for copy folder, copy command, duplicate, hibernate, move to window, close above / below / others.
- **Sleeping tabs.** On restart every tab comes back asleep, showing the text it had, and wakes only when asked (Enter or F2). A Claude Code tab wakes as the same conversation with the same flags (`claude ... --resume <id>`); a shell wakes in its last folder. Hidden idle Claude tabs are put to sleep when memory runs short; hovering a tab's RAM figure gives a Zz button that puts it to sleep at once. "Restore all" wakes every tab.
- **History.** Every tab that closes, sleeps, or is open when Tabs closes is kept with its text. The History view (Ctrl+Shift+H) searches all of it, filters by date and kind, previews the text, copies it, and resumes any entry in a new tab.
- **Compose box.** Ctrl+Shift+E opens an editor under the terminal for writing what a terminal makes awkward; Ctrl+Enter sends it to the tab as one paste.
- **RAM at a glance.** Each live tab shows its RAM (shell plus the program it runs) on a coloured badge, the heaviest one glowing; the footer shows all tabs together and Tabs' own.
- **Colour themes.** Reads the colour scheme and font size from an existing Tabby configuration, so moving over looks the same.
- **Tabby-compatible command line:** `fxterm-tabs open <dir>`, `fxterm-tabs run <command...>`; a second launch adds a tab to the running window.

## Keys

Ctrl+Shift+T new tab · Ctrl+Shift+N new window · Ctrl+Shift+W close tab · Ctrl+Tab / Ctrl+Shift+Tab next / previous · Ctrl+1..9 or Alt+1..9 go to tab · Ctrl+Shift+PgUp/PgDn move tab · Ctrl+Shift+E compose · Ctrl+Shift+H history · Ctrl+F find · right-click copies a selection or pastes · F11 full screen. Everything else goes to the program in the tab.

## Install

Download `fxterm-tabs.exe` from the [latest release](https://github.com/fxterm/tabs/releases/latest) and run it. It needs Java 25 (the exe finds an installed JDK 25 or later). On first run it downloads `fxterm-tabs.jar` from the same release and checks it against the SHA-256 built into the exe. `fxterm-tabs.exe -Xjr:update` moves to a newer release.

Settings and saved state live in `%USERPROFILE%\fxterm\tabs` (open tabs, window places, history, log).

## Build

JDK 25 and Maven:

```
mvn verify
app\target\jr\fxterm-tabs.exe
```

`mvn -Djr.source=url verify` builds the release files in `app\target\jr\release`.

## Credits and licences

Tabs is Apache-2.0 (see LICENSE). The terminal widget is a modified copy of [JediTermFX](https://github.com/techsenger/jeditermfx) 1.1.0 (module `jeditermfx-ui`), itself a JavaFX port of JetBrains' JediTerm, dual-licensed Apache-2.0 / LGPL-3.0; the licence texts are in that module. Process handling uses [pty4j](https://github.com/JetBrains/pty4j); the exe is made with [jr](https://github.com/jarrunner/jr).
