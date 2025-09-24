#!/usr/bin/env python3
"""
SBOM Validator - Validates CycloneDX SBOM files
"""

import json
import sys
import argparse
from pathlib import Path
from typing import Dict, List, Any, Optional

class SBOMValidator:
    def __init__(self):
        self.errors = []
        self.warnings = []
        
    def validate_sbom(self, sbom_file: str) -> bool:
        """Validate SBOM file"""
        print(f"🔍 Validating SBOM file: {sbom_file}")
        
        try:
            with open(sbom_file, 'r') as f:
                sbom_data = json.load(f)
            
            # Validate required fields
            self.validate_required_fields(sbom_data)
            
            # Validate metadata
            self.validate_metadata(sbom_data)
            
            # Validate components
            self.validate_components(sbom_data)
            
            # Validate dependencies
            self.validate_dependencies(sbom_data)
            
            # Print results
            self.print_validation_results(sbom_file)
            
            return len(self.errors) == 0
            
        except FileNotFoundError:
            print(f"❌ SBOM file not found: {sbom_file}")
            return False
        except json.JSONDecodeError as e:
            print(f"❌ Invalid JSON in SBOM file: {e}")
            return False
        except Exception as e:
            print(f"❌ Error validating SBOM file: {e}")
            return False
    
    def validate_required_fields(self, sbom_data: Dict[str, Any]) -> None:
        """Validate required SBOM fields"""
        required_fields = [
            "bomFormat",
            "specVersion",
            "serialNumber",
            "version",
            "metadata",
            "components"
        ]
        
        for field in required_fields:
            if field not in sbom_data:
                self.errors.append(f"Missing required field: {field}")
    
    def validate_metadata(self, sbom_data: Dict[str, Any]) -> None:
        """Validate SBOM metadata"""
        metadata = sbom_data.get("metadata", {})
        
        # Validate metadata required fields
        metadata_required = ["timestamp", "tools"]
        for field in metadata_required:
            if field not in metadata:
                self.errors.append(f"Missing required metadata field: {field}")
        
        # Validate timestamp format
        if "timestamp" in metadata:
            timestamp = metadata["timestamp"]
            if not self.is_valid_timestamp(timestamp):
                self.errors.append(f"Invalid timestamp format: {timestamp}")
        
        # Validate tools
        if "tools" in metadata:
            tools = metadata["tools"]
            if not isinstance(tools, list) or len(tools) == 0:
                self.errors.append("Metadata tools must be a non-empty array")
            else:
                for tool in tools:
                    if not isinstance(tool, dict) or "name" not in tool:
                        self.errors.append("Each tool must have a name")
    
    def validate_components(self, sbom_data: Dict[str, Any]) -> None:
        """Validate SBOM components"""
        components = sbom_data.get("components", [])
        
        if not isinstance(components, list):
            self.errors.append("Components must be an array")
            return
        
        if len(components) == 0:
            self.warnings.append("No components found in SBOM")
            return
        
        for i, component in enumerate(components):
            self.validate_component(component, i)
    
    def validate_component(self, component: Dict[str, Any], index: int) -> None:
        """Validate individual component"""
        component_required = ["type", "name", "version"]
        
        for field in component_required:
            if field not in component:
                self.errors.append(f"Component {index}: Missing required field '{field}'")
        
        # Validate component type
        if "type" in component:
            valid_types = [
                "application", "framework", "library", "container", "platform",
                "operating-system", "device", "firmware", "file"
            ]
            if component["type"] not in valid_types:
                self.warnings.append(f"Component {index}: Unknown type '{component['type']}'")
        
        # Validate version
        if "version" in component:
            version = component["version"]
            if not self.is_valid_version(version):
                self.warnings.append(f"Component {index}: Invalid version format '{version}'")
        
        # Validate purl (Package URL)
        if "purl" in component:
            purl = component["purl"]
            if not self.is_valid_purl(purl):
                self.warnings.append(f"Component {index}: Invalid purl format '{purl}'")
        
        # Validate licenses
        if "licenses" in component:
            licenses = component["licenses"]
            if not isinstance(licenses, list):
                self.errors.append(f"Component {index}: Licenses must be an array")
            else:
                for j, license_info in enumerate(licenses):
                    self.validate_license(license_info, index, j)
    
    def validate_license(self, license_info: Dict[str, Any], component_index: int, license_index: int) -> None:
        """Validate license information"""
        if "id" not in license_info and "name" not in license_info:
            self.errors.append(f"Component {component_index}, License {license_index}: Must have either 'id' or 'name'")
    
    def validate_dependencies(self, sbom_data: Dict[str, Any]) -> None:
        """Validate SBOM dependencies"""
        dependencies = sbom_data.get("dependencies", [])
        
        if not isinstance(dependencies, list):
            self.errors.append("Dependencies must be an array")
            return
        
        for i, dependency in enumerate(dependencies):
            self.validate_dependency(dependency, i)
    
    def validate_dependency(self, dependency: Dict[str, Any], index: int) -> None:
        """Validate individual dependency"""
        if "ref" not in dependency:
            self.errors.append(f"Dependency {index}: Missing required field 'ref'")
        
        if "dependsOn" in dependency:
            depends_on = dependency["dependsOn"]
            if not isinstance(depends_on, list):
                self.errors.append(f"Dependency {index}: 'dependsOn' must be an array")
    
    def is_valid_timestamp(self, timestamp: str) -> bool:
        """Check if timestamp is valid ISO 8601 format"""
        try:
            from datetime import datetime
            datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            return True
        except ValueError:
            return False
    
    def is_valid_version(self, version: str) -> bool:
        """Check if version follows semantic versioning"""
        import re
        # Basic semantic versioning pattern
        pattern = r'^\d+\.\d+\.\d+(-[a-zA-Z0-9.-]+)?(\+[a-zA-Z0-9.-]+)?$'
        return bool(re.match(pattern, version))
    
    def is_valid_purl(self, purl: str) -> bool:
        """Check if purl is valid Package URL format"""
        import re
        # Basic purl pattern
        pattern = r'^pkg:[a-zA-Z0-9][a-zA-Z0-9._-]*/[a-zA-Z0-9][a-zA-Z0-9._-]*(@[a-zA-Z0-9][a-zA-Z0-9._-]*)?(#[a-zA-Z0-9][a-zA-Z0-9._-]*)?$'
        return bool(re.match(pattern, purl))
    
    def print_validation_results(self, sbom_file: str) -> None:
        """Print validation results"""
        print(f"\n📊 Validation Results for {sbom_file}")
        print("=" * 50)
        
        if self.errors:
            print(f"❌ Errors ({len(self.errors)}):")
            for error in self.errors:
                print(f"  - {error}")
        
        if self.warnings:
            print(f"⚠️  Warnings ({len(self.warnings)}):")
            for warning in self.warnings:
                print(f"  - {warning}")
        
        if not self.errors and not self.warnings:
            print("✅ No issues found")
        elif not self.errors:
            print("✅ Validation passed with warnings")
        else:
            print("❌ Validation failed")

def main():
    parser = argparse.ArgumentParser(description='Validate CycloneDX SBOM files')
    parser.add_argument('sbom_file', help='SBOM file to validate')
    parser.add_argument('--strict', action='store_true', help='Treat warnings as errors')
    
    args = parser.parse_args()
    
    validator = SBOMValidator()
    is_valid = validator.validate_sbom(args.sbom_file)
    
    if args.strict and validator.warnings:
        print("\n❌ Validation failed in strict mode due to warnings")
        return 1
    
    return 0 if is_valid else 1

if __name__ == '__main__':
    sys.exit(main())
