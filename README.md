<p align="center"><img src="docs/icon.png" width="96" alt=""></p>

# Tabs

Full name **FxTerminalTabs**; we simply call it **Tabs**. The project and its files are named `fxterm-tabs` (organisation [fxterm](https://fxterm.github.io)).

A terminal for Windows and macOS with vertical tabs, made for long sessions with AI coding agents such as Claude Code. Tabs sleep and wake like a browser's, every session can be found again in History, and several windows share one process. An idle window uses a few tens of MB.

Status: 0.1, early releases. Windows is the main platform; macOS (Apple silicon and Intel) is new in 0.1.4 and tested far less (Read aloud and dictation need a voice engine and speech keys that a Mac does not have yet).

![Tabs with two groups of tabs; Claude Code has just answered a question about a small Java project](docs/screenshots/main.png)

## What it does, with examples

### Vertical tabs, grouped

Each tab shows three lines: its title, its folder's name, and the full path. A number in the corner is the key that reaches it (Ctrl+1 or Alt+1 for the first). In the picture above, **Backend**, **Frontend** and **Later** are separators: labels that group the tabs under them. Add one with the "⎯ label" button under the list, or with "Insert separator above" in a tab's menu. Separators are never selected and are skipped by Ctrl+Tab and the number keys.

Give a tab a colour from its menu (right-click, Colour) and the colour shows as a strip at its left edge. The active tab is a lighter row with white text. If you prefer the whole row and the frame round the terminal in the tab's colour, start Tabs with `--tab-fill full`.

Drag a tab to reorder it (or press Ctrl+Shift+PgUp / PgDn). A new tab opens just after the current one.

### Tabs that sleep and wake

![A sleeping tab: the moon in the list, and its saved text with a Restore button](docs/screenshots/asleep.png)

When Tabs closes it saves every tab. When it starts again, every tab comes back **asleep**: no process runs, and the tab shows the text it had, so you can read or copy it. Press Enter or F2 (or click Restore) to wake it.

- A shell wakes in its last folder.
- A Claude Code tab wakes as the same conversation with the same flags (`claude ... --resume <id>`).
- If a shell had been running Claude Code before Tabs closed, F3 resumes that last conversation instead.
- "Restore all" in the footer wakes every tab in the window.

A sleeping tab is dulled in the list and carries a grey moon; hover the moon and it turns into a yellow sun, and one click wakes the tab. To put a tab to sleep yourself, hover its RAM figure and click Zz, or choose Hibernate in its menu. When memory runs short, hidden Claude tabs that have been idle are put to sleep for you.

Closing a tab (its x, Ctrl+Shift+W, or its menu) closes a bare shell at once. When Claude Code or any other program is running in it, Tabs asks first: **Close** (F2), **Hibernate** (F3) or cancel (Esc).

### RAM at a glance

Each live tab shows the RAM of its shell and everything it runs: blue under 100 MB, yellow from 100, orange from 200, and the heaviest tab red. The footer shows all tabs together ("All tabs 70 MB (4 live, 1 asleep)") and Tabs' own share.

### Several windows, one process

Drag a tab out of the window and it opens in a new window, with its program still running. Drop it on another window's list to move it there, or use "Move to window" in its menu. Ctrl+Shift+N opens a new window. A window left without tabs closes. Every window, with its place on the screen and its tabs, comes back after a restart.

### History

![History: four saved sessions, the text of one in the preview](docs/screenshots/history.png)

Every tab that closes or goes to sleep is kept, with its text, and so is every tab that was open when Tabs closed. Ctrl+Shift+H opens History. Search finds words in titles, folders and the saved text. Filter by date and kind, preview the text, copy it, copy the command that resumes it, or press Enter to resume it in a new tab.

### Compose box

![The compose box under the terminal, with a two-line request for Claude](docs/screenshots/compose.png)

Ctrl+Shift+E opens an editor under the terminal for writing what a terminal makes awkward, such as a request of several lines for an AI agent. It takes the terminal's own colours, and has an editor's tools (bold, italic, headings, lists) and a writer bar for dictation (F3) and spelling. Ctrl+Enter sends it to the tab as one paste followed by Enter; "Type only" sends it without Enter.

### Select, read aloud, quote

Select text in the terminal and a small bar appears beside it: **Copy**, **Read aloud**, **Quote & reply** (into the compose box) and **Quote in notepadfx**. Read aloud speaks the selection and lights each word in the terminal as it is said, scrolling to keep it in sight; while it reads, the footer shows Pause, Stop, speed and Voice. Claude's own markers and the box-drawing characters are not read.

### Font size

Ctrl+= makes the text bigger, Ctrl+- smaller, and Ctrl+0 goes back to the default. Ctrl+mouse wheel and pinching on a touchpad or touch screen do the same. The size applies to every tab in every window and is remembered.

### Also

- The right mouse button pastes (Ctrl+right-click keeps the menu).
- The footer tells whether a newer Tabs is released: it checks when Tabs starts and every 24 hours, and the small button beside the status checks now. Nothing is downloaded by itself. When one is out, click "Update ... available": Tabs asks, downloads it (checked against its SHA-256), replaces itself, and offers to restart, with every tab saved and waking where it was. The same on a Mac. From a terminal: `fxterm-tabs -Xjr:update` (a copy not started through the release exe opens the release page instead).
- The footer shows the tab's folder; click it to copy. Copy text copies the whole tab, Find (Ctrl+F) searches it.
- A tab's menu: copy folder path, copy command (the line that brings the tab back in any cmd window), rename, colour, duplicate, hibernate or restore, move to window, insert separator above, close, close tabs above / below / others.
- Colours and font size are read from an existing Tabby configuration, so moving over looks the same.
- Tabby-compatible command line: `fxterm-tabs open <dir>` opens a shell there, `fxterm-tabs run <command...>` opens a tab running a command. A second launch adds a tab to the running window.

### On a Mac

Install in Terminal (curl sets no download mark, so macOS does not block the file; a browser download would be blocked until you choose Open Anyway in System Settings, Privacy & Security):

```
curl -fLo /tmp/fxterm-tabs https://github.com/fxterm/tabs/releases/latest/download/fxterm-tabs-macos-$(uname -m) && chmod +x /tmp/fxterm-tabs && /tmp/fxterm-tabs -Xjr:install
```

That puts `Tabs.app` in `~/Applications` and a command `fxterm-tabs` in `~/.local/bin`; no administrator rights. It needs Java 25 (jr offers to download one if none is found). Update later by clicking the footer's "Update ... available", or with `fxterm-tabs -Xjr:update`.

What differs from Windows:

- The Command key does what Ctrl does on Windows: Cmd+T new tab, Cmd+N new window, Cmd+W close, Cmd+1..9 go to a tab, Cmd+= / Cmd+- / Cmd+0 font size, Cmd+Shift+E compose, Cmd+Shift+H history, Cmd+C / Cmd+V copy and paste. Ctrl+Tab and Ctrl+Shift+Tab still step between tabs, and every Ctrl key goes to the program in the tab.
- New tabs run your login shell (`$SHELL -l`, zsh by default). A tab that ran Claude Code wakes as `claude ... --resume <id>` inside a login shell, and drops back to a shell when claude ends.
- The look is VS Code's terminal in its Dark Modern theme, in Menlo. `--theme tabby` uses an existing Tabby configuration instead (`~/Library/Application Support/tabby/config.yaml`).
- Not on a Mac yet: dragging a tab out of the window into a new window, clickable file paths (web links work), Read aloud (it needs a Deepgram key; the free system voices are not connected) and dictation (it needs speech keys, and the app needs a microphone permission text that the launcher cannot write yet). Everything else in this README works on a Mac.

## Keys

- Ctrl+Shift+T new tab, Ctrl+Shift+N new window, Ctrl+Shift+W close tab
- Ctrl+Tab / Ctrl+Shift+Tab next / previous tab, Ctrl+1..9 or Alt+1..9 go to a tab
- Ctrl+Shift+PgUp / PgDn move the tab up / down
- Ctrl+Shift+E compose, Ctrl+Shift+H history, Ctrl+F find
- Ctrl+= / Ctrl+- / Ctrl+0 font size
- On a sleeping tab: Enter or F2 wake, F3 resume its last Claude conversation
- F11 full screen

Everything else goes to the program in the tab.

## Install

Download `fxterm-tabs.exe` from the [latest release](https://github.com/fxterm/tabs/releases/latest) and run it. It needs Java 25 (the exe finds an installed JDK 25 or later). On first run it downloads `fxterm-tabs.jar` from the same release and checks it against the SHA-256 built into the exe. `fxterm-tabs.exe -Xjr:update` moves to a newer release.

Settings and saved state live in `%USERPROFILE%\fxterm\tabs`: open tabs, window places, font size, history and the log.

## Options

- `--tab-fill strip|full` a tab's colour as the strip at its edge (the default), or filling the row and the frame
- `--font N`, `--family NAME` the terminal font (Consolas on Windows, Menlo on a Mac)
- `--theme tabby|vscode` colours from an existing Tabby configuration (the default on Windows), or VS Code's Dark Modern terminal (the default on a Mac)
- `--shell CMD` the shell for new tabs (cmd.exe on Windows, your login shell on a Mac)
- `--restore false` start with a fresh tab instead of the saved ones

## Build

JDK 25 and Maven:

```
mvn verify
app\target\jr\fxterm-tabs.exe
```

`mvn -Djr.source=url verify` builds the release files in `app\target\jr\release`.

## Credits and licences

Tabs is Apache-2.0 (see LICENSE). The terminal widget is a modified copy of [JediTermFX](https://github.com/techsenger/jeditermfx) 1.1.0 (module `jeditermfx-ui`), itself a JavaFX port of JetBrains' JediTerm, dual-licensed Apache-2.0 / LGPL-3.0; the licence texts are in that module. Process handling uses [pty4j](https://github.com/JetBrains/pty4j); the exe is made with [jr](https://github.com/jarrunner/jr).
