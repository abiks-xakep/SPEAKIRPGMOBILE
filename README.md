# SpeakiRPG Mobile v0.2

Changes from the first proof of concept:

- smaller joystick
- joystick moved above the in-game chat area
- persistent SPACE/auto-attack button
- ESC/E overlay buttons removed
- collapsible utility hotkey panel
- one-finger touch translated to LMB hold + mousemove + release
- short one-finger tap translated to normal click
- two-finger drag translated to RMB hold + drag for camera rotation
- two-finger pinch translated to mouse wheel for camera zoom
- F5-style reload button included in utility panel

## Gesture mapping

- 1 finger tap: normal left click
- 1 finger hold + drag: held LMB drag (petting / cheek pulling)
- 2 finger drag: held RMB drag (camera)
- pinch: wheel zoom

## Utility panel

Tap `☰`.

Includes:

- Portal F
- Emote T
- Respawn R
- Jump G
- Reset camera C
- Equipment E
- Stat Upgrade U
- Inventory I
- Quests Q
- Chat Enter
- Mailbox M
- Ranking K
- Attendance J
- Settings O
- Channel H
- Reload / F5 equivalent

## Important

Mouse gestures are translated in injected JavaScript because the original game is desktop/mouse-first.
The keyboard path was already confirmed to accept synthetic browser KeyboardEvents. Mouse support now needs testing on the actual game in WebView.
