Mnemosyne Launcher:
For beta release:
- Set apps for launchable menu items
- Search page
- Themeable gui elements
- Toggle for enabling auto rotate, should be disabled by default
- Settings for text size
- Highlight/focus background image for items in the main menu
- Sound for opening and closing menu
- User setting for changing "Android" home screen grid
- Users should be able to change items and arrangements on the 3x4 menu. They should be able to replace "Games" with another app which will take the icon and name of that app on the menu
- Users should be able to set menu grid to 3x3, 2x2 or 3x4
- Keypad popup -> As the user types a phone number, it will be shown on the screen with a phone icon like on the Sony Ericsson phones
- Highlight/clicked effects for left and right side options on the bottom dock
- Themes:
    - Mnemosyne theme pack: external apks that the launcher will find to pick themeable gui elements from (theme packs), they can also be used for other Mnemosyne apps
    - Icon packs: external apks which will be icon packs. Icon packs compatible with Mnemosyne should also contain icons for the main menu.

Bugs:
- Uninstalled apps aren't being removed from the frequent apps bar
- Android home screen layout isn't stretching across its entire container
- Main menu icon pop animation is not played in the 1st run of openMainMenu() after launcher starting

Other apps:
- Theme Manager: An app that will apply themes across all Mnemosyne apps. Every individual Mnemosyne app will scan for settings from this specific app and prioritize it over local-app configurations.
    - Theme ideas:
        - Sony Ericsson Clarity
        - Sony Ericsson Floroflow
        - Sony Ericsson Floom
        - Sony Ericsson Female Lines
        - Sony Ericsson Eternal
        - Sony Ericsson
    - Users should be able to choose a font in `${InternalStorageRoot}/Mnemosyne/Fonts/`
- Phone Dialer: An app for sending and receiving calls
- Messaging: An app for sending SMS and E-mail messages. UI should be similar to Sony Ericsson.
- Contacts: An app for contacts. Its UI is similar to the Sony Ericsson A1/A2 and UIQ contacts app.
- Video player: An app for playing videos. It should be based on MPV.
- Photo viewer: It should be based

For final version:
- Symbian S60 homescreen gui
