# POSPROD GAS POS WebView App

This Android app loads the POSPROD GAS POS system in a WebView and provides a Bluetooth ESC/POS printing bridge.

## Features
- Loads POSPROD GAS POS system in WebView
- JavaScript, DOM storage, file upload, and popups supported
- Android-JavaScript bridge for Bluetooth ESC/POS printing (`printReceipt(data)`)
- Back button navigates WebView history
- Network error handling
- Ready for GitHub Actions CI build

## Getting Started
1. Open the project in Android Studio
2. Build and run on an Android device

## Bluetooth Printing
The app exposes a `printReceipt(data)` function to JavaScript for ESC/POS Bluetooth printing.

## CI/CD
Includes a sample GitHub Actions workflow for building the app with Gradle.
