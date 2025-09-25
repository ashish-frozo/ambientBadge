# 🔍 Ambient Scribe Logcat Analysis Tools

Automated logcat analysis tools for debugging and monitoring the Ambient Scribe Android application.

## 🚀 Quick Start

### Prerequisites
- Android device connected via USB
- USB debugging enabled
- ADB (Android Debug Bridge) installed

### Basic Usage

```bash
# Analyze logcat for 30 seconds
./analyze_logcat.sh analyze

# Real-time monitoring
./analyze_logcat.sh monitor

# App-specific logs only
./analyze_logcat.sh app

# Error logs only
./analyze_logcat.sh errors
```

## 🛠️ Available Tools

### 1. Bash Script (`analyze_logcat.sh`)
**Simple, fast analysis with colored output**

```bash
# Analyze for 60 seconds
./analyze_logcat.sh analyze 60

# Real-time monitoring
./analyze_logcat.sh monitor

# Show app logs only
./analyze_logcat.sh app

# Show errors only
./analyze_logcat.sh errors
```

**Features:**
- ✅ Colored output for easy reading
- ✅ Real-time monitoring
- ✅ Error pattern detection
- ✅ Performance analysis
- ✅ Native library analysis
- ✅ Audio system analysis
- ✅ Security analysis

### 2. Python Analyzer (`logcat_analyzer.py`)
**Advanced analysis with JSON export and detailed reporting**

```bash
# Analyze for 30 seconds
python3 logcat_analyzer.py analyze

# Analyze for 60 seconds with custom output
python3 logcat_analyzer.py analyze -d 60 -o my_analysis.json

# Real-time monitoring
python3 logcat_analyzer.py monitor

# Monitor for 120 seconds
python3 logcat_analyzer.py monitor -d 120

# App-specific logs
python3 logcat_analyzer.py app

# Error logs only
python3 logcat_analyzer.py errors
```

**Features:**
- ✅ JSON export for detailed analysis
- ✅ Health scoring system
- ✅ Pattern recognition
- ✅ Categorized issue detection
- ✅ Historical analysis
- ✅ Custom package filtering

## 📊 Analysis Categories

### 🚨 Critical Issues
- **Fatal Exceptions** - App crashes
- **Android Runtime Errors** - System-level errors
- **Native Link Errors** - Missing native libraries

### ⚡ Performance Issues
- **ANR (Application Not Responding)** - UI freezes
- **Memory Warnings** - GC pressure
- **Thermal Warnings** - Device overheating

### 🔧 Native Library Issues
- **UnsatisfiedLinkError** - Missing native methods
- **Whisper Logs** - Speech recognition engine
- **LLaMA Logs** - Language model engine

### 🎵 Audio Issues
- **AudioRecord Errors** - Microphone capture
- **AudioTrack Errors** - Audio playback
- **ASR Logs** - Speech recognition

### 🔒 Security Issues
- **Permission Denied** - Access violations
- **Keystore Errors** - Encryption issues
- **Encryption Logs** - Data protection

## 📈 Health Scoring

The analyzer provides a health score based on detected issues:

- **✅ EXCELLENT** - No issues detected
- **⚠️ GOOD** - Few issues detected (< 5)
- **🟡 FAIR** - Some issues detected (5-15)
- **🚨 POOR** - Many issues detected (> 15)

## 🔍 Pattern Detection

The tools automatically detect and categorize:

### Error Patterns
- `FATAL EXCEPTION` - Critical crashes
- `AndroidRuntime.*ERROR` - System errors
- `UnsatisfiedLinkError` - Native library issues
- `ANR in` - Application not responding
- `Permission denied` - Security violations

### App-Specific Patterns
- `com.frozo.ambientscribe` - App-specific logs
- `WhisperAndroid` - Speech recognition
- `LLaMAAndroid` - Language processing
- `ASR` - Automatic Speech Recognition
- `AudioRecord` - Audio capture

### Performance Patterns
- `GC.*Alloc` - Memory allocation warnings
- `thermal` - Thermal management
- `AudioManager.*ERROR` - Audio system issues

## 📁 Output Files

### Bash Script Output
- `logcat_analysis_YYYYMMDD_HHMMSS.txt` - Raw logcat data
- `logcat_analysis_YYYYMMDD_HHMMSS_detailed.txt` - Detailed analysis

### Python Analyzer Output
- `logcat_analysis_YYYYMMDD_HHMMSS.json` - Structured analysis data

## 🎯 Use Cases

### 1. **Development Debugging**
```bash
# Capture logs during app testing
./analyze_logcat.sh analyze 60

# Monitor real-time during development
./analyze_logcat.sh monitor
```

### 2. **Production Monitoring**
```bash
# Long-term analysis
python3 logcat_analyzer.py analyze -d 300 -o production_analysis.json

# Continuous monitoring
python3 logcat_analyzer.py monitor
```

### 3. **Issue Investigation**
```bash
# Focus on errors only
./analyze_logcat.sh errors

# App-specific debugging
./analyze_logcat.sh app
```

### 4. **Performance Analysis**
```bash
# Check for ANR and memory issues
python3 logcat_analyzer.py analyze -d 120
```

## 🔧 Troubleshooting

### Common Issues

**1. "No Android device connected"**
```bash
# Check device connection
adb devices

# Enable USB debugging on device
# Settings > Developer Options > USB Debugging
```

**2. "ADB not found"**
```bash
# Install Android SDK platform tools
# Or add ADB to PATH
export PATH=$PATH:/path/to/android-sdk/platform-tools
```

**3. "Permission denied"**
```bash
# Make scripts executable
chmod +x analyze_logcat.sh
chmod +x logcat_analyzer.py
```

### Advanced Usage

**Custom Package Filtering**
```bash
# Analyze different package
python3 logcat_analyzer.py analyze -p com.example.app -d 30
```

**Continuous Monitoring with Logging**
```bash
# Monitor and save to file
./analyze_logcat.sh monitor > monitoring.log 2>&1 &
```

**Integration with CI/CD**
```bash
# Automated testing
python3 logcat_analyzer.py analyze -d 60 -o test_results.json
if [ $? -eq 0 ]; then
    echo "App health check passed"
else
    echo "App health check failed"
    exit 1
fi
```

## 📋 Best Practices

### 1. **Regular Monitoring**
- Run analysis during development
- Monitor production deployments
- Check for regressions after updates

### 2. **Issue Tracking**
- Save analysis results for comparison
- Track health scores over time
- Correlate issues with app versions

### 3. **Performance Optimization**
- Monitor ANR and memory warnings
- Check thermal management
- Optimize native library usage

### 4. **Security Auditing**
- Review permission denials
- Check encryption/keystore logs
- Monitor security exceptions

## 🎉 Success Stories

### Fixed Issues Using These Tools:

1. **Native Library Loading** ✅
   - Detected `UnsatisfiedLinkError` for `initializeNativeModel`
   - Fixed NDK configuration in `build.gradle.kts`
   - Resolved native library integration

2. **Runtime Crashes** ✅
   - Identified `NullPointerException` in `AudioProcessingConfig`
   - Fixed initialization order issues
   - Eliminated startup crashes

3. **Performance Issues** ✅
   - Detected ANR warnings
   - Optimized memory usage
   - Improved thermal management

4. **Audio System** ✅
   - Fixed microphone permission issues
   - Resolved audio capture problems
   - Improved speech recognition reliability

## 🚀 Future Enhancements

- [ ] Machine learning-based anomaly detection
- [ ] Real-time alerting system
- [ ] Integration with crash reporting tools
- [ ] Performance benchmarking
- [ ] Automated issue categorization
- [ ] Historical trend analysis

---

**Happy Debugging!** 🔍✨

For questions or issues, please check the main project documentation or create an issue in the repository.
