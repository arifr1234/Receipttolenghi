# AI Agent Instructions for Receipttolenghi

Welcome! You are working on **Receipttolenghi**, an Android application that communicates with a thermal printer using Bluetooth. The app has different pages ("Modes") for interacting with the printer.

## Project Overview
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Printer Library**: `dantsu/escpos-thermalprinter-android`
- **Theme**: Dark mode by default

## Coding Standards
1. **Reduce the amount of code you write**
   a. Always use libraries and pre-made components when fitting libraries exist and are maintained.
   b. Write minimal code, prioritize code maintainability.
   c. Don't do redundant error handling, make it as contained and minimal as possible.
2. **Write modular, decoupled code and components.**
3. **If you don't know which solution is preferable, ask.**

## Architecture & Implementation Rules
1. **Global Services:** Keep hardware connections and core services (like `PrinterManager`) globally accessible to avoid unnecessary delays (e.g., reconnecting to Bluetooth on every page).
2. **Global Error Handling:** Handle errors globally and gracefully rather than scattering try-catch blocks in the UI layers. Make error management minimal and contained. (Note: The current codebase is transitioning to this, avoid adding more localized error logs unless necessary).
3. **Jetpack Compose Best Practices:**
   - Hoist state where appropriate.
   - Pass `Modifier` as a parameter for all custom Composables, defaulting to `Modifier = Modifier`.
   - Keep composables modular and reusable.
4. **Permissions:** Ensure proper runtime permissions for Bluetooth (varying by Android version SDK 31+) are checked and granted before hardware interactions.
5. **Adding New Modes:**
   - UI should be modular and list-based. Adding or renaming modes should be straightforward and require minimal boilerplate.
   - Each mode should be a distinct screen component (e.g., `TestPrinterScreen`).

## General Workflow
- Always refer back to these guidelines before making architectural changes.
- Review existing code patterns to maintain consistency across screens and features.
