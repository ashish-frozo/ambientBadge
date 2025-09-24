#!/usr/bin/env python3
"""
PHI Linter - Detects and blocks PHI strings in code
Prevents accidental inclusion of sensitive data in logs and code
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Tuple, Dict
import argparse

class PHILinter:
    def __init__(self):
        self.violations = []
        self.phi_patterns = self._load_phi_patterns()
        self.excluded_dirs = {
            '.git', '.gradle', 'build', 'node_modules', '.idea', 
            'venv', '__pycache__', '.pytest_cache', 'target'
        }
        self.excluded_files = {
            'phi_linter.py', 'phi_violations.txt', '.gitignore'
        }
        
    def _load_phi_patterns(self) -> Dict[str, List[str]]:
        """Load PHI detection patterns"""
        return {
            'phone_numbers': [
                r'\b[6-9]\d{9}\b',  # Indian phone numbers
                r'\+91[6-9]\d{9}\b',  # Indian phone with country code
                r'\b91[6-9]\d{9}\b',  # Indian phone with 91 prefix
                r'\(\d{3}\)\s*\d{3}-\d{4}',  # US phone format
                r'\d{3}-\d{3}-\d{4}',  # US phone format
                r'\d{10}',  # Generic 10-digit number
            ],
            'email_addresses': [
                r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b',
            ],
            'names': [
                r'\b(?:Dr\.?|Doctor)\s+[A-Z][a-z]+\s+[A-Z][a-z]+\b',  # Doctor names
                r'\b(?:Patient|Pt\.?)\s+[A-Z][a-z]+\s+[A-Z][a-z]+\b',  # Patient names
                r'\b[A-Z][a-z]+\s+[A-Z][a-z]+\s+[A-Z][a-z]+\b',  # Full names
            ],
            'medical_ids': [
                r'\bMRN\s*:?\s*[A-Za-z0-9-]+\b',  # Medical Record Numbers
                r'\bPatient\s*ID\s*:?\s*[A-Za-z0-9-]+\b',  # Patient IDs
                r'\bEncounter\s*ID\s*:?\s*[A-Za-z0-9-]+\b',  # Encounter IDs
                r'\bClinic\s*ID\s*:?\s*[A-Za-z0-9-]+\b',  # Clinic IDs
            ],
            'addresses': [
                r'\b\d+\s+[A-Za-z\s]+(?:Street|Road|Avenue|Lane|Colony|Nagar|Pur|Pura)\b',
                r'\b[A-Za-z\s]+,\s*[A-Za-z\s]+,\s*\d{6}\b',  # Indian address with PIN
            ],
            'medical_terms': [
                r'\b(?:Patient|Pt\.?)\s*:?\s*[A-Za-z0-9\s]+',  # Patient references
                r'\b(?:Diagnosis|Dx\.?)\s*:?\s*[A-Za-z0-9\s]+',  # Diagnosis
                r'\b(?:Symptoms|Sx\.?)\s*:?\s*[A-Za-z0-9\s]+',  # Symptoms
                r'\b(?:Medication|Meds\.?)\s*:?\s*[A-Za-z0-9\s]+',  # Medications
            ],
            'dates_with_context': [
                r'\b(?:DOB|Birth)\s*:?\s*\d{1,2}[/-]\d{1,2}[/-]\d{2,4}',  # Birth dates
                r'\b(?:Admission|Discharge)\s*:?\s*\d{1,2}[/-]\d{1,2}[/-]\d{2,4}',  # Medical dates
            ],
            'ssn_patterns': [
                r'\b\d{3}-\d{2}-\d{4}\b',  # SSN format
                r'\b\d{9}\b',  # 9-digit number (potential SSN)
            ],
            'credit_cards': [
                r'\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b',  # Credit card numbers
            ],
            'common_phi_terms': [
                r'\b(?:PHI|PII|Personal|Health|Medical)\s*[A-Za-z0-9\s]*\b',
                r'\b(?:Confidential|Sensitive|Private)\s*[A-Za-z0-9\s]*\b',
            ]
        }
    
    def scan_file(self, file_path: Path) -> List[Tuple[int, str, str]]:
        """Scan a single file for PHI patterns"""
        violations = []
        
        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()
                
            for line_num, line in enumerate(lines, 1):
                for category, patterns in self.phi_patterns.items():
                    for pattern in patterns:
                        matches = re.finditer(pattern, line, re.IGNORECASE)
                        for match in matches:
                            # Check if this is a false positive
                            if self._is_false_positive(match.group(), line, file_path):
                                continue
                                
                            violations.append((
                                line_num,
                                category,
                                f"Potential {category}: {match.group()}"
                            ))
                            
        except Exception as e:
            print(f"Error scanning {file_path}: {e}")
            
        return violations
    
    def _is_false_positive(self, match: str, line: str, file_path: Path) -> bool:
        """Check if a match is a false positive"""
        # Skip if it's in a comment or documentation
        if line.strip().startswith('//') or line.strip().startswith('#'):
            return True
            
        # Skip if it's in a test file and looks like test data
        if 'test' in file_path.name.lower() and any(word in match.lower() for word in ['test', 'example', 'sample']):
            return True
            
        # Skip if it's in a configuration file and looks like configuration
        if file_path.suffix in ['.yml', '.yaml', '.json', '.xml'] and any(word in match.lower() for word in ['config', 'example', 'template']):
            return True
            
        # Skip if it's clearly a pattern or regex
        if '\\b' in line or '\\d' in line or '\\w' in line:
            return True
            
        # Skip if it's in a string literal that's clearly a pattern
        if 'pattern' in line.lower() or 'regex' in line.lower() or 'format' in line.lower():
            return True
            
        return False
    
    def scan_directory(self, root_dir: Path) -> None:
        """Scan directory for PHI patterns"""
        print(f"Scanning directory: {root_dir}")
        
        for file_path in root_dir.rglob('*'):
            if file_path.is_file():
                # Skip excluded directories
                if any(excluded in str(file_path) for excluded in self.excluded_dirs):
                    continue
                    
                # Skip excluded files
                if file_path.name in self.excluded_files:
                    continue
                    
                # Only scan certain file types
                if file_path.suffix not in ['.kt', '.java', '.xml', '.json', '.md', '.txt', '.yml', '.yaml']:
                    continue
                    
                violations = self.scan_file(file_path)
                if violations:
                    for line_num, category, message in violations:
                        violation = f"{file_path}:{line_num} - {message}"
                        self.violations.append(violation)
                        print(f"⚠️  {violation}")
    
    def generate_report(self, output_file: str = 'phi_violations.txt') -> None:
        """Generate PHI violation report"""
        with open(output_file, 'w') as f:
            if self.violations:
                f.write("PHI Violations Detected:\n")
                f.write("=" * 50 + "\n\n")
                for violation in self.violations:
                    f.write(f"{violation}\n")
                f.write(f"\nTotal violations: {len(self.violations)}\n")
            else:
                f.write("No PHI violations detected.\n")
    
    def run(self, root_dir: str = '.') -> int:
        """Run PHI linter"""
        print("🔍 Starting PHI Linter...")
        print("=" * 50)
        
        root_path = Path(root_dir)
        if not root_path.exists():
            print(f"❌ Directory not found: {root_dir}")
            return 1
            
        self.scan_directory(root_path)
        self.generate_report()
        
        if self.violations:
            print(f"\n❌ Found {len(self.violations)} PHI violations")
            print("Please review and fix these violations before committing.")
            return 1
        else:
            print("\n✅ No PHI violations detected")
            return 0

def main():
    parser = argparse.ArgumentParser(description='PHI Linter - Detect PHI in code')
    parser.add_argument('--dir', default='.', help='Directory to scan (default: current directory)')
    parser.add_argument('--output', default='phi_violations.txt', help='Output file for violations')
    
    args = parser.parse_args()
    
    linter = PHILinter()
    exit_code = linter.run(args.dir)
    sys.exit(exit_code)

if __name__ == '__main__':
    main()
