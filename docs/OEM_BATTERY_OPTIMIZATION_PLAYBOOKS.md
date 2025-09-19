# OEM Battery Optimization Playbooks

This document provides guidance for preventing aggressive battery optimization and app killing on various Android OEM devices. These optimizations can interfere with Ambient Scribe's continuous audio recording and transcription capabilities.

## Detected OEM Types

The `OEMKillerWatchdog` automatically detects the following OEM types:

- Xiaomi (MIUI)
- Huawei (EMUI)
- OPPO (ColorOS)
- Vivo (FuntouchOS)
- Samsung (OneUI)
- OnePlus (OxygenOS)
- Realme (RealmeUI)

## OEM-Specific Guidance

### Xiaomi (MIUI)

1. Go to **Settings** > **Battery & Performance**
2. Select **App Battery Saver**
3. Find **Ambient Scribe**
4. Select **No restrictions**
5. Additionally, enable **Autostart** for the app in **Settings** > **Apps** > **Manage Apps** > **Ambient Scribe** > **Autostart**

### Huawei (EMUI)

1. Go to **Settings** > **Battery**
2. Select **App Launch**
3. Find **Ambient Scribe**
4. Select **Manage Manually**
5. Enable **Auto-launch**, **Secondary launch**, and **Run in background**

### OPPO (ColorOS)

1. Go to **Settings** > **Battery**
2. Select **Power Optimization**
3. Find **Ambient Scribe**
4. Select **Don't Optimize**
5. Also go to **Settings** > **Additional Settings** > **Application Management** > **Ambient Scribe** > **Battery** > **Allow background activity**

### Vivo (FuntouchOS)

1. Go to **Settings** > **Battery**
2. Select **Background Power Management**
3. Find **Ambient Scribe**
4. Select **Allow Background Running**
5. Also go to **Settings** > **Apps** > **Ambient Scribe** > **Auto-start** and enable it

### Samsung (OneUI)

1. Go to **Settings** > **Device Care** > **Battery**
2. Select **App Power Management**
3. Find **Ambient Scribe**
4. Select **Don't Optimize**
5. Also go to **Settings** > **Apps** > **Ambient Scribe** > **Battery** > **Allow background activity**

### OnePlus (OxygenOS)

1. Go to **Settings** > **Battery**
2. Select **Battery Optimization**
3. Find **Ambient Scribe**
4. Select **Don't Optimize**
5. Also go to **Settings** > **Apps** > **Ambient Scribe** > **Battery** > **Advanced** > **Allow background activity**

### Realme (RealmeUI)

1. Go to **Settings** > **Battery**
2. Select **App Battery Management**
3. Find **Ambient Scribe**
4. Select **Don't Restrict**
5. Also go to **Settings** > **Apps** > **Ambient Scribe** > **Battery** > **Allow background activity**

## Technical Implementation

The `OEMKillerWatchdog` class implements the following features:

1. **Heartbeat Mechanism**: Records periodic timestamps to detect if the app was killed
2. **OEM Detection**: Identifies the device manufacturer to provide specific guidance
3. **Auto-restart**: Automatically restarts the app after detecting abnormal termination
4. **Metrics Collection**: Logs termination events for analysis
5. **Lifecycle Integration**: Monitors app foreground/background state

## Testing OEM Killer Detection

To test the OEM killer detection:

1. Start the app and let it run for at least one minute (to record a heartbeat)
2. Force-kill the app using the system app manager
3. Restart the app
4. The app should detect the abnormal termination and show guidance

## Additional Resources

- [Don't kill my app!](https://dontkillmyapp.com/) - Comprehensive guide to battery optimization issues
- [Android Developer Guide: Background Optimizations](https://developer.android.com/topic/performance/background-optimization)
