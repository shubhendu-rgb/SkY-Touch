SkY Touch 🎯✨
> An elegant, interactive notch gesture assistant with Side Deck quick access panels, OTP code detectors, and system-wide Text Assistant & AI Snippets powered by Gemini.
> 
📸 App Screenshots
| Dashboard Interface | Navigation Drawer | About Developer |
|---|---|---| 
HOW TO ADD I DON'T KNOW 
|  |  |  |
🌟 Overview
SkY Touch is an advanced Android utility and accessibility application built with Jetpack Compose. It transforms your phone’s camera cutout (notch or hole-punch) into an interactive shortcut hub. By leveraging Android's Accessibility Services, SkY Touch empowers users with custom gesture controls, instant 2FA/OTP code interception, a floating edge side deck, and smart AI text assistance across any app.
🚀 Key Features
1. 📐 Notch Gesture Shortcuts
 * Multi-Action Triggers: Assign custom actions to Single Tap, Double Tap, Triple Tap, Long Press, Swipe Left, Swipe Right, and Swipe Holds around your front camera.
 * System Controls & Utilities: Quickly toggle flashlights, take screenshots, change music volumes, adjust screen brightness, or launch silent background "Spy Cam" recordings.
 * Interactive Live Calibration: Visually adjust the notch overlay's width, height, X/Y offsets, shape (circle, capsule, rectangle), and color with real-time feedback.
2. 📂 Side Deck Quick Access Panel
 * Edge Floating Handle: Place a sleek, customizable vertical trigger handle on either the left or right edge of your screen.
 * Capsule Dock: Swipe the handle to reveal a quick-access capsule containing system tools and pinned favorite applications.
 * System Back Protection: Configurable touch buffer zones to ensure swipe gestures open the dock without accidentally triggering system back navigation.
3. 🛡️ OTP & Verification Code Detector
 * Background Interception: Automatically scans incoming notification banners and raw SMS messages for one-time passwords (OTPs) and 2FA codes.
 * Clipboard Automation: Instantly copies detected codes to the clipboard and posts rich heads-up alert notifications with a 1-tap Copy Code button.
 * Interactive Testing Sandbox: Test regex patterns, numeric ranges, and custom keyword filters against sample message templates in real time.
4. 🤖 Text Assistant & Gemini AI Snippets
 * Inline Expansion: Type short abbreviations (e.g., ?addr, ?meet) in any text field to auto-expand them into full templates.
 * Smart AI Transformations: Use AI-powered keyword triggers to instantly fix grammar, make text formal, summarize, or translate content right inside any application using Gemini.
5. ⚙️ Robust Customization & Backup
 * Excluded Apps: Select specific applications (like full-screen games or video players) where gestures and overlays should be temporarily hidden.
 * Backup & Restore: Export all custom gesture configurations, snippets, and app settings into a portable JSON file to sync across devices.
🛠️ Tech Stack & Architecture
 * UI Framework: 100% Jetpack Compose with Material 3 design and dynamic color support.
 * Architecture: MVVM (Model-View-ViewModel) pattern with Kotlin Coroutines and Flows.
 * Local Database: Room Database for persisting configurations, gesture statistics, text snippets, and detected code history.
 * AI Integration: Google Gemini API integration for system-wide text rewriting and snippet transformations.
📱 Getting Started & Permissions
To function properly, SkY Touch requires the following standard Android permissions:
 * Accessibility Service: Required to listen for touch gestures around the camera cutout and render floating overlays.
 * Notification Listener / SMS: Required for automated background OTP interception.
 * Overlay Permission: Required to draw the notch assistant and Side Deck handles over other apps.
