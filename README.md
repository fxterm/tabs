# fxterm-tabs (FxTerminalTabs)

A Windows terminal with vertical tabs, made for long sessions with AI coding agents such as Claude Code.

Status: experimental, in daily use by its authors.

## What it does

- Tabs down the left side: numbered, colourable, renamable; Alt+1..9 selects, Ctrl+Shift+T opens, Ctrl+Shift+W closes, Ctrl+Tab steps.
- Colour themes: reads the colour scheme and font size from an existing Tabby configuration, so the switch looks the same.
- Sleeping tabs, like a browser's tab hibernation: on restart every tab comes back asleep and wakes only when visited. A Claude Code tab wakes as the same conversation with the same flags (`--resume`); a shell wakes in its folder. Hidden idle agent tabs are put to sleep when memory runs short.
- Per-tab memory on hover and in the footer.
- Tabby-compatible command line (`open <dir>`, `run <command...>`); a second launch adds a tab to the running window.

## Build

JDK 25 and Maven:

```
mvn package
java -jar shade/fxterm-tabs.jar
```

## Credits and licences

The terminal widget is a modified copy of [JediTermFX](https://github.com/techsenger/jeditermfx) 1.1.0 (module `jeditermfx-ui`), itself a JavaFX port of JetBrains' JediTerm, dual-licensed Apache-2.0 / LGPL-3.0; the licence texts are in that module. Process handling uses [pty4j](https://github.com/JetBrains/pty4j).
