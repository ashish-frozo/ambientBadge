#!/usr/bin/env python3
"""
Ambient Scribe Advanced Logcat Analyzer
Automatically analyzes adb logcat for app issues, performance, and debugging
"""

import subprocess
import re
import json
import time
import argparse
from datetime import datetime
from collections import Counter, defaultdict
from typing import Dict, List, Tuple, Optional
import sys

class LogcatAnalyzer:
    def __init__(self, package_name: str = "com.frozo.ambientscribe"):
        self.package_name = package_name
        self.patterns = {
            'fatal_exception': r'FATAL EXCEPTION',
            'android_runtime_error': r'AndroidRuntime.*ERROR',
            'app_error': rf'{package_name}.*ERROR',
            'native_link_error': r'UnsatisfiedLinkError|No implementation found',
            'anr': r'ANR in',
            'memory_warning': r'GC.*Alloc',
            'thermal_warning': r'thermal|Thermal',
            'audio_error': r'AudioRecord|AudioTrack|AudioManager.*ERROR',
            'permission_denied': r'Permission denied|SecurityException',
            'whisper_logs': r'WhisperAndroid|whisper_android',
            'llama_logs': r'LLaMAAndroid|llama_android',
            'asr_logs': r'ASR|asr',
            'keystore_logs': r'Keystore|keystore',
            'encryption_logs': r'encrypt|Encrypt'
        }
        
    def check_device_connected(self) -> bool:
        """Check if Android device is connected"""
        try:
            result = subprocess.run(['adb', 'devices'], capture_output=True, text=True)
            return 'device' in result.stdout
        except FileNotFoundError:
            print("❌ ADB not found. Please install Android SDK platform tools.")
            return False
    
    def capture_logcat(self, duration: int = 30) -> str:
        """Capture logcat for specified duration"""
        print(f"📱 Capturing logcat for {duration} seconds...")
        
        # Clear logcat buffer
        subprocess.run(['adb', 'logcat', '-c'], capture_output=True)
        
        # Capture logcat
        start_time = time.time()
        process = subprocess.Popen(['adb', 'logcat', '-v', 'time'], 
                                 stdout=subprocess.PIPE, 
                                 stderr=subprocess.PIPE, 
                                 text=True)
        
        logs = []
        while time.time() - start_time < duration:
            line = process.stdout.readline()
            if line:
                logs.append(line.strip())
            else:
                time.sleep(0.1)
        
        process.terminate()
        process.wait()
        
        return '\n'.join(logs)
    
    def analyze_logs(self, logs: str) -> Dict:
        """Analyze captured logs"""
        analysis = {
            'timestamp': datetime.now().isoformat(),
            'total_lines': len(logs.split('\n')),
            'app_logs': len(re.findall(self.package_name, logs)),
            'errors': {},
            'patterns': {},
            'top_errors': [],
            'critical_issues': [],
            'performance_issues': [],
            'security_issues': [],
            'native_issues': [],
            'audio_issues': []
        }
        
        # Count error patterns
        for pattern_name, pattern in self.patterns.items():
            matches = re.findall(pattern, logs, re.IGNORECASE)
            analysis['patterns'][pattern_name] = len(matches)
            
            if matches:
                analysis['errors'][pattern_name] = matches
        
        # Extract critical issues
        fatal_exceptions = re.findall(r'FATAL EXCEPTION.*', logs)
        analysis['critical_issues'] = fatal_exceptions[-5:]  # Last 5 fatal errors
        
        # Extract top error patterns
        error_lines = re.findall(r'.*(ERROR|FATAL|Exception).*', logs)
        error_texts = [line.split(':', 3)[-1].strip() for line in error_lines if ':' in line]
        analysis['top_errors'] = Counter(error_texts).most_common(10)
        
        # Categorize issues
        if analysis['patterns']['fatal_exception'] > 0:
            analysis['critical_issues'].extend(['Fatal exceptions detected'])
        
        if analysis['patterns']['anr'] > 0:
            analysis['performance_issues'].append('ANR (Application Not Responding) detected')
        
        if analysis['patterns']['native_link_error'] > 0:
            analysis['native_issues'].append('Native library linking errors')
        
        if analysis['patterns']['audio_error'] > 0:
            analysis['audio_issues'].append('Audio system errors')
        
        if analysis['patterns']['permission_denied'] > 0:
            analysis['security_issues'].append('Permission denied errors')
        
        return analysis
    
    def print_analysis(self, analysis: Dict):
        """Print formatted analysis results"""
        print("\n" + "="*50)
        print("🔍 AMBIENT SCRIBE LOGCAT ANALYSIS")
        print("="*50)
        
        print(f"\n📊 OVERVIEW")
        print(f"Total log lines: {analysis['total_lines']}")
        print(f"App-specific logs: {analysis['app_logs']}")
        
        print(f"\n🚨 ERROR SUMMARY")
        for pattern, count in analysis['patterns'].items():
            if count > 0:
                status = "❌" if count > 5 else "⚠️" if count > 0 else "✅"
                print(f"{status} {pattern.replace('_', ' ').title()}: {count}")
        
        if analysis['critical_issues']:
            print(f"\n🔥 CRITICAL ISSUES")
            for issue in analysis['critical_issues']:
                print(f"💥 {issue}")
        
        if analysis['performance_issues']:
            print(f"\n⚡ PERFORMANCE ISSUES")
            for issue in analysis['performance_issues']:
                print(f"⚠️ {issue}")
        
        if analysis['native_issues']:
            print(f"\n🔧 NATIVE LIBRARY ISSUES")
            for issue in analysis['native_issues']:
                print(f"🔗 {issue}")
        
        if analysis['audio_issues']:
            print(f"\n🎵 AUDIO ISSUES")
            for issue in analysis['audio_issues']:
                print(f"🎤 {issue}")
        
        if analysis['security_issues']:
            print(f"\n🔒 SECURITY ISSUES")
            for issue in analysis['security_issues']:
                print(f"🚫 {issue}")
        
        if analysis['top_errors']:
            print(f"\n🔍 TOP ERROR PATTERNS")
            for error, count in analysis['top_errors'][:5]:
                print(f"📈 {count}x: {error[:80]}...")
        
        # Overall health score
        total_issues = sum(analysis['patterns'].values())
        if total_issues == 0:
            print(f"\n✅ HEALTH STATUS: EXCELLENT - No issues detected!")
        elif total_issues < 5:
            print(f"\n⚠️ HEALTH STATUS: GOOD - Few issues detected ({total_issues} total)")
        elif total_issues < 15:
            print(f"\n🟡 HEALTH STATUS: FAIR - Some issues detected ({total_issues} total)")
        else:
            print(f"\n🚨 HEALTH STATUS: POOR - Many issues detected ({total_issues} total)")
    
    def save_analysis(self, analysis: Dict, filename: Optional[str] = None):
        """Save analysis to JSON file"""
        if filename is None:
            filename = f"logcat_analysis_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        
        with open(filename, 'w') as f:
            json.dump(analysis, f, indent=2)
        
        print(f"\n💾 Analysis saved to: {filename}")
    
    def monitor_realtime(self, duration: int = 0):
        """Monitor logcat in real-time"""
        print("🔄 Real-time Logcat Monitor")
        print("Press Ctrl+C to stop")
        print("-" * 30)
        
        try:
            subprocess.run(['adb', 'logcat', '-c'])
            cmd = ['adb', 'logcat', '-v', 'time']
            
            if duration > 0:
                # Use timeout for limited duration
                process = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True)
                start_time = time.time()
                
                for line in process.stdout:
                    if time.time() - start_time > duration:
                        break
                    if any(pattern in line for pattern in [self.package_name, 'ERROR', 'FATAL', 'Exception']):
                        print(line.strip())
            else:
                # Continuous monitoring
                process = subprocess.Popen(cmd, stdout=subprocess.PIPE, text=True)
                for line in process.stdout:
                    if any(pattern in line for pattern in [self.package_name, 'ERROR', 'FATAL', 'Exception']):
                        print(line.strip())
                        
        except KeyboardInterrupt:
            print("\n⏹️ Monitoring stopped by user")
        finally:
            if 'process' in locals():
                process.terminate()

def main():
    parser = argparse.ArgumentParser(description='Ambient Scribe Logcat Analyzer')
    parser.add_argument('command', nargs='?', default='analyze', 
                       choices=['analyze', 'monitor', 'app', 'errors'],
                       help='Command to execute')
    parser.add_argument('-d', '--duration', type=int, default=30,
                       help='Duration for logcat capture (seconds)')
    parser.add_argument('-o', '--output', type=str,
                       help='Output file for analysis results')
    parser.add_argument('-p', '--package', type=str, default='com.frozo.ambientscribe',
                       help='Package name to filter')
    
    args = parser.parse_args()
    
    analyzer = LogcatAnalyzer(args.package)
    
    if not analyzer.check_device_connected():
        print("❌ No Android device connected. Please connect a device and enable USB debugging.")
        sys.exit(1)
    
    if args.command == 'analyze':
        logs = analyzer.capture_logcat(args.duration)
        analysis = analyzer.analyze_logs(logs)
        analyzer.print_analysis(analysis)
        analyzer.save_analysis(analysis, args.output)
    
    elif args.command == 'monitor':
        analyzer.monitor_realtime(args.duration if args.duration > 0 else 0)
    
    elif args.command == 'app':
        print("📱 App-specific logs (press Ctrl+C to stop):")
        subprocess.run(['adb', 'logcat', '-c'])
        subprocess.run(['adb', 'logcat'], stdout=subprocess.PIPE, text=True, 
                      universal_newlines=True, bufsize=1)
    
    elif args.command == 'errors':
        print("🚨 Error logs only (press Ctrl+C to stop):")
        subprocess.run(['adb', 'logcat', '-c'])
        subprocess.run(['adb', 'logcat'], stdout=subprocess.PIPE, text=True,
                      universal_newlines=True, bufsize=1)

if __name__ == "__main__":
    main()
