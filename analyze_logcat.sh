#!/bin/bash

# Ambient Scribe Logcat Analysis Script
# Automatically analyzes adb logcat for app issues, performance, and debugging

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# App package name
PACKAGE_NAME="com.frozo.ambientscribe"

echo -e "${CYAN}🔍 Ambient Scribe Logcat Analyzer${NC}"
echo "=================================="

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}❌ No Android device connected. Please connect a device and enable USB debugging.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Device connected${NC}"

# Function to analyze logcat
analyze_logcat() {
    local duration=${1:-30}
    local output_file="logcat_analysis_$(date +%Y%m%d_%H%M%S).txt"
    
    echo -e "${BLUE}📱 Capturing logcat for ${duration} seconds...${NC}"
    echo "Output file: $output_file"
    
    # Clear logcat buffer
    adb logcat -c
    
    # Capture logcat with timestamp
    timeout $duration adb logcat -v time > "$output_file" 2>&1 &
    local logcat_pid=$!
    
    echo -e "${YELLOW}⏱️  Capturing logs... (${duration}s)${NC}"
    sleep $duration
    
    # Kill logcat process
    kill $logcat_pid 2>/dev/null || true
    wait $logcat_pid 2>/dev/null || true
    
    echo -e "${GREEN}✅ Logcat capture complete${NC}"
    
    # Analyze the captured logs
    analyze_captured_logs "$output_file"
}

# Function to analyze captured logs
analyze_captured_logs() {
    local log_file="$1"
    
    echo -e "\n${PURPLE}📊 ANALYSIS RESULTS${NC}"
    echo "=================="
    
    # Check if log file exists and has content
    if [[ ! -f "$log_file" || ! -s "$log_file" ]]; then
        echo -e "${RED}❌ No logcat data captured${NC}"
        return 1
    fi
    
    local total_lines=$(wc -l < "$log_file")
    echo -e "${CYAN}📈 Total log lines: $total_lines${NC}"
    
    # App-specific logs
    local app_logs=$(grep -c "$PACKAGE_NAME" "$log_file" 2>/dev/null || echo "0")
    echo -e "${CYAN}📱 App logs: $app_logs${NC}"
    
    # Error analysis
    echo -e "\n${RED}🚨 ERROR ANALYSIS${NC}"
    echo "================"
    
    local fatal_errors=$(grep -c "FATAL EXCEPTION" "$log_file" 2>/dev/null || echo "0")
    local android_runtime_errors=$(grep -c "AndroidRuntime.*ERROR" "$log_file" 2>/dev/null || echo "0")
    local app_errors=$(grep -c "$PACKAGE_NAME.*ERROR" "$log_file" 2>/dev/null || echo "0")
    
    echo -e "${RED}💥 Fatal Exceptions: $fatal_errors${NC}"
    echo -e "${RED}⚠️  Android Runtime Errors: $android_runtime_errors${NC}"
    echo -e "${RED}🔴 App Errors: $app_errors${NC}"
    
    # Show recent fatal errors
    if [[ $fatal_errors -gt 0 ]]; then
        echo -e "\n${RED}🔥 RECENT FATAL ERRORS:${NC}"
        grep "FATAL EXCEPTION" "$log_file" | tail -3
    fi
    
    # Native library analysis
    echo -e "\n${BLUE}🔧 NATIVE LIBRARY ANALYSIS${NC}"
    echo "=========================="
    
    local native_errors=$(grep -c "UnsatisfiedLinkError\|No implementation found" "$log_file" 2>/dev/null || echo "0")
    local whisper_logs=$(grep -c "WhisperAndroid\|whisper_android" "$log_file" 2>/dev/null || echo "0")
    local llama_logs=$(grep -c "LLaMAAndroid\|llama_android" "$log_file" 2>/dev/null || echo "0")
    
    echo -e "${BLUE}🔗 Native Link Errors: $native_errors${NC}"
    echo -e "${BLUE}🎤 Whisper Logs: $whisper_logs${NC}"
    echo -e "${BLUE}🤖 LLaMA Logs: $llama_logs${NC}"
    
    # Performance analysis
    echo -e "\n${GREEN}⚡ PERFORMANCE ANALYSIS${NC}"
    echo "====================="
    
    local anr_logs=$(grep -c "ANR in" "$log_file" 2>/dev/null || echo "0")
    local memory_warnings=$(grep -c "GC.*Alloc" "$log_file" 2>/dev/null || echo "0")
    local thermal_warnings=$(grep -c "thermal\|Thermal" "$log_file" 2>/dev/null || echo "0")
    
    echo -e "${GREEN}⏱️  ANR (Application Not Responding): $anr_logs${NC}"
    echo -e "${GREEN}💾 Memory Warnings: $memory_warnings${NC}"
    echo -e "${GREEN}🌡️  Thermal Warnings: $thermal_warnings${NC}"
    
    # Audio analysis
    echo -e "\n${YELLOW}🎵 AUDIO ANALYSIS${NC}"
    echo "==============="
    
    local audio_errors=$(grep -c "AudioRecord\|AudioTrack\|AudioManager.*ERROR" "$log_file" 2>/dev/null || echo "0")
    local microphone_logs=$(grep -c "microphone\|Microphone" "$log_file" 2>/dev/null || echo "0")
    local asr_logs=$(grep -c "ASR\|asr" "$log_file" 2>/dev/null || echo "0")
    
    echo -e "${YELLOW}🎤 Audio Errors: $audio_errors${NC}"
    echo -e "${YELLOW}🎙️  Microphone Logs: $microphone_logs${NC}"
    echo -e "${YELLOW}🗣️  ASR Logs: $asr_logs${NC}"
    
    # Security analysis
    echo -e "\n${PURPLE}🔒 SECURITY ANALYSIS${NC}"
    echo "=================="
    
    local permission_denied=$(grep -c "Permission denied\|SecurityException" "$log_file" 2>/dev/null || echo "0")
    local keystore_logs=$(grep -c "Keystore\|keystore" "$log_file" 2>/dev/null || echo "0")
    local encryption_logs=$(grep -c "encrypt\|Encrypt" "$log_file" 2>/dev/null || echo "0")
    
    echo -e "${PURPLE}🚫 Permission Denied: $permission_denied${NC}"
    echo -e "${PURPLE}🔑 Keystore Logs: $keystore_logs${NC}"
    echo -e "${PURPLE}🔐 Encryption Logs: $encryption_logs${NC}"
    
    # Generate summary
    echo -e "\n${CYAN}📋 SUMMARY${NC}"
    echo "========="
    
    local total_issues=$((fatal_errors + android_runtime_errors + app_errors + native_errors + anr_logs))
    
    if [[ $total_issues -eq 0 ]]; then
        echo -e "${GREEN}✅ No critical issues detected!${NC}"
    elif [[ $total_issues -lt 5 ]]; then
        echo -e "${YELLOW}⚠️  Few issues detected ($total_issues total)${NC}"
    else
        echo -e "${RED}🚨 Multiple issues detected ($total_issues total)${NC}"
    fi
    
    # Show top error patterns
    echo -e "\n${RED}🔍 TOP ERROR PATTERNS:${NC}"
    grep -E "ERROR|FATAL|Exception" "$log_file" | cut -d' ' -f4- | sort | uniq -c | sort -nr | head -5
    
    # Save detailed analysis
    local analysis_file="logcat_analysis_$(date +%Y%m%d_%H%M%S)_detailed.txt"
    {
        echo "Ambient Scribe Logcat Analysis - $(date)"
        echo "========================================"
        echo ""
        echo "Total log lines: $total_lines"
        echo "App logs: $app_logs"
        echo "Fatal exceptions: $fatal_errors"
        echo "Android runtime errors: $android_runtime_errors"
        echo "App errors: $app_errors"
        echo "Native link errors: $native_errors"
        echo "ANR events: $anr_logs"
        echo "Memory warnings: $memory_warnings"
        echo "Thermal warnings: $thermal_warnings"
        echo "Audio errors: $audio_errors"
        echo "Permission denied: $permission_denied"
        echo ""
        echo "TOP ERROR PATTERNS:"
        grep -E "ERROR|FATAL|Exception" "$log_file" | cut -d' ' -f4- | sort | uniq -c | sort -nr | head -10
        echo ""
        echo "RECENT FATAL ERRORS:"
        grep "FATAL EXCEPTION" "$log_file" | tail -5
    } > "$analysis_file"
    
    echo -e "\n${GREEN}📄 Detailed analysis saved to: $analysis_file${NC}"
    echo -e "${GREEN}📄 Raw logcat saved to: $log_file${NC}"
}

# Function to monitor real-time
monitor_realtime() {
    echo -e "${CYAN}🔄 Real-time Logcat Monitor${NC}"
    echo "=========================="
    echo "Press Ctrl+C to stop monitoring"
    echo ""
    
    adb logcat -c
    adb logcat | grep -E "($PACKAGE_NAME|AndroidRuntime|FATAL|ERROR|WhisperAndroid|LLaMAAndroid|ASR|AudioRecord)" --line-buffered --color=always
}

# Function to show app-specific logs only
show_app_logs() {
    echo -e "${CYAN}📱 App-specific Logs${NC}"
    echo "==================="
    
    adb logcat -c
    adb logcat | grep "$PACKAGE_NAME" --line-buffered --color=always
}

# Function to show errors only
show_errors() {
    echo -e "${RED}🚨 Error Logs Only${NC}"
    echo "=================="
    
    adb logcat -c
    adb logcat | grep -E "(ERROR|FATAL|Exception|ANR)" --line-buffered --color=always
}

# Main menu
case "${1:-menu}" in
    "analyze")
        analyze_logcat "${2:-30}"
        ;;
    "monitor")
        monitor_realtime
        ;;
    "app")
        show_app_logs
        ;;
    "errors")
        show_errors
        ;;
    "menu"|*)
        echo -e "${CYAN}Ambient Scribe Logcat Analyzer${NC}"
        echo "================================"
        echo ""
        echo "Usage: $0 [command] [options]"
        echo ""
        echo "Commands:"
        echo "  analyze [seconds]  - Capture and analyze logcat (default: 30s)"
        echo "  monitor           - Real-time monitoring"
        echo "  app               - Show app-specific logs only"
        echo "  errors            - Show error logs only"
        echo "  menu              - Show this menu"
        echo ""
        echo "Examples:"
        echo "  $0 analyze 60     - Analyze for 60 seconds"
        echo "  $0 monitor        - Real-time monitoring"
        echo "  $0 app            - App logs only"
        echo "  $0 errors         - Error logs only"
        ;;
esac
