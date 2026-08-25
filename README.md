# **DEPRECATED! Due to the official developer adding a mobile interface directly into the browser, this repository has been abandoned. Updates will only be provided in exceptional cases upon request.**




# SpeakiRPG Mobile

An unofficial Android client for **SpeakiRPG** focused on making the desktop-only web game playable on touch devices.

The app loads the original SpeakiRPG website inside an Android `WebView` and adds a mobile control layer on top of it. The game itself is not reimplemented or hosted by this project.

> **Unofficial community project.**  
> This project is not affiliated with or endorsed by SpeakiRPG, Overture, or EPID Games.  
> All original game content, characters, artwork, trademarks, and related intellectual property belong to their respective owners.

## What it does

SpeakiRPG is designed around keyboard and mouse input. On Android, this client translates touch input into the same browser keyboard and mouse events used by the desktop version.

Current features include:

- Virtual WASD joystick
- Diagonal movement
- Dedicated `SPACE` attack / auto-attack button
- Extra hotkeys through the `☰` menu
- Camera rotation using a two-finger drag
- Camera zoom using pinch gestures
- One-finger LMB drag support
- Speaki head-patting support
- Speaki cheek-stretching support
- Scroll support inside Settings, Ranking, and other long in-game windows
- Tap-vs-scroll detection inside scrollable menus
- Editable mobile HUD
- Persistent HUD position, size, and opacity
- Desktop browser user-agent for compatibility
- Fullscreen landscape mode

## Controls

### Movement

The virtual joystick emulates:

| Direction | Keyboard input |
|---|---|
| Up | `W` |
| Down | `S` |
| Left | `A` |
| Right | `D` |
| Diagonal | Two movement keys at once |

### Touch / mouse emulation

| Gesture | Desktop equivalent |
|---|---|
| Short single tap | Left click |
| One-finger hold + drag | Hold LMB + mouse move |
| Two-finger drag | Hold RMB + mouse move |
| Pinch | Mouse wheel |

The one-finger drag behavior is important because SpeakiRPG uses held mouse movement for interactions such as patting Speaki and stretching cheeks.

### Main mobile buttons

- `SPACE` — basic attack / auto-attack
- `☰` — opens the mobile hotkey menu

### Extra hotkeys

The mobile menu currently exposes game actions that are inconvenient or unavailable directly on the compressed mobile layout:

| Action | Key |
|---|---|
| Portal | `F` |
| Emote | `T` |
| Respawn | `R` |
| Jump | `G` |
| Reset Camera | `C` |
| Equipment | `E` |
| Stat Upgrade / Boost | `U` |
| Inventory | `I` |
| Quests | `Q` |
| Mailbox | `M` |
| Ranking | `K` |
| Attendance / Daily | `J` |
| Settings | `O` |
| Channel | `H` |

## Editable HUD

The mobile controls can be repositioned to fit different phones, screen sizes, and play styles.

Open:

`☰ → Edit HUD`

In edit mode you can:

- Drag supported controls anywhere on the screen
- Increase or decrease their size
- Increase or decrease their opacity
- Reset the layout to its defaults
- Save the layout with `Done`

The current editable controls include:

- Joystick
- `SPACE`
- `☰`

HUD settings are stored with Android `SharedPreferences`, so the layout remains after restarting the app.

## Scrollable in-game windows

Some SpeakiRPG menus are taller than the available viewport on a phone.

The client detects scrollable HTML containers and separates taps from scrolling:

- Short touch → presses the button/control
- Vertical drag → scrolls the menu

This is useful for interfaces such as Settings, Ranking, and other long modal windows.

## How it works

The client uses two layers:

1. **Android UI overlay**
   - Joystick
   - SPACE button
   - Mobile hotkey menu
   - HUD editor

2. **Injected browser input bridge**
   - `KeyboardEvent`
   - `MouseEvent`
   - `PointerEvent`
   - `WheelEvent`

The original SpeakiRPG page continues running normally inside the `WebView`.

No modified SpeakiRPG game files are bundled with this project.

## Requirements

- Android 8.0+ (`minSdk 26`)
- Internet connection
- Android System WebView / Chromium
- Landscape orientation

## Build

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Use JDK 17.
4. Let Gradle sync.
5. Connect an Android device with USB debugging enabled or use an emulator.
6. Run the `app` configuration.

Current project targets:

```text
compileSdk = 34
targetSdk = 34
minSdk = 26
JVM target = 17
```

## Important notes

### This is a WebView wrapper

SpeakiRPG is still loaded from:

`https://speakirpg.overture.io.kr/`

If the website is unavailable, the Android client will also be unavailable.

### Game updates may break compatibility

This project depends on behavior exposed by the current SpeakiRPG web client.

A future game update may change:

- Keyboard shortcuts
- DOM structure
- Input event handling
- UI layout
- Browser compatibility

The mobile input layer is intentionally kept separate from the game itself so that most fixes can be made without modifying the server or original game files.

### Chat

Text input inside Android WebView behaves differently from ordinary mouse controls. The client currently lets text fields receive native Android touches instead of converting those touches into synthetic mouse events.

Depending on Android/WebView behavior, chat interaction may still need additional compatibility work.

## Current status

The project is still experimental, but the core gameplay controls are functional on Android:

- Movement
- Combat
- Camera movement
- Zoom
- Touch-based character interactions
- In-game menu scrolling
- Mobile hotkeys
- Custom HUD placement

Bug reports and device-specific feedback are welcome.

## Planned / experimental ideas

These are **not implemented yet**:

- Game controller / gamepad support
- Screenshot mode that hides the full game UI
- Compact/custom server UI presentation inside the WebView
- Additional mobile layout presets
- More HUD elements editable by the player
- User-configurable hotkey mapping
- Improved chat / soft-keyboard integration
- Experimental enemy/entity inspection

## Disclaimer

This is an unofficial fan-made compatibility client.

The application does not claim ownership of SpeakiRPG, Trickcal-related content, characters, artwork, or any third-party intellectual property displayed by the original website.

The purpose of this project is only to provide an Android touch-control layer for the existing SpeakiRPG web client.
