#!/usr/bin/env python3
"""
SBOM Summary Generator - Creates summary reports for SBOM files
"""

import json
import sys
from pathlib import Path
from typing import Dict, List, Any, Optional
from collections import Counter

class SBOMSummaryGenerator:
    def __init__(self):
        self.summaries = []
        
    def generate_summary(self, sbom_files: List[str]) -> str:
        """Generate summary for multiple SBOM files"""
        print("📊 Generating SBOM summary...")
        
        summary_parts = []
        summary_parts.append("SBOM Summary Report")
        summary_parts.append("=" * 50)
        summary_parts.append("")
        
        for sbom_file in sbom_files:
            if Path(sbom_file).exists():
                file_summary = self.analyze_sbom_file(sbom_file)
                summary_parts.append(file_summary)
                summary_parts.append("")
            else:
                summary_parts.append(f"⚠️  File not found: {sbom_file}")
                summary_parts.append("")
        
        # Generate overall summary
        overall_summary = self.generate_overall_summary(sbom_files)
        summary_parts.append(overall_summary)
        
        return "\n".join(summary_parts)
    
    def analyze_sbom_file(self, sbom_file: str) -> str:
        """Analyze individual SBOM file"""
        try:
            with open(sbom_file, 'r') as f:
                sbom_data = json.load(f)
            
            # Extract basic information
            bom_format = sbom_data.get("bomFormat", "Unknown")
            spec_version = sbom_data.get("specVersion", "Unknown")
            serial_number = sbom_data.get("serialNumber", "Unknown")
            version = sbom_data.get("version", "Unknown")
            
            # Extract metadata
            metadata = sbom_data.get("metadata", {})
            timestamp = metadata.get("timestamp", "Unknown")
            tools = metadata.get("tools", [])
            
            # Extract components
            components = sbom_data.get("components", [])
            component_count = len(components)
            
            # Analyze components
            component_types = Counter(comp.get("type", "unknown") for comp in components)
            component_licenses = Counter()
            component_vulnerabilities = 0
            
            for comp in components:
                # Count licenses
                licenses = comp.get("licenses", [])
                for license_info in licenses:
                    license_id = license_info.get("id", "unknown")
                    component_licenses[license_id] += 1
                
                # Count vulnerabilities
                vulnerabilities = comp.get("vulnerabilities", [])
                component_vulnerabilities += len(vulnerabilities)
            
            # Extract dependencies
            dependencies = sbom_data.get("dependencies", [])
            dependency_count = len(dependencies)
            
            # Generate file summary
            summary_parts = []
            summary_parts.append(f"📁 {sbom_file}")
            summary_parts.append("-" * 30)
            summary_parts.append(f"BOM Format: {bom_format}")
            summary_parts.append(f"Spec Version: {spec_version}")
            summary_parts.append(f"Serial Number: {serial_number}")
            summary_parts.append(f"Version: {version}")
            summary_parts.append(f"Generated: {timestamp}")
            summary_parts.append(f"Tools: {len(tools)}")
            summary_parts.append("")
            
            summary_parts.append("Components:")
            summary_parts.append(f"  Total: {component_count}")
            for comp_type, count in component_types.most_common():
                summary_parts.append(f"  {comp_type}: {count}")
            summary_parts.append("")
            
            if component_licenses:
                summary_parts.append("Licenses:")
                for license_id, count in component_licenses.most_common(10):
                    summary_parts.append(f"  {license_id}: {count}")
                summary_parts.append("")
            
            if component_vulnerabilities > 0:
                summary_parts.append(f"⚠️  Vulnerabilities: {component_vulnerabilities}")
                summary_parts.append("")
            
            summary_parts.append(f"Dependencies: {dependency_count}")
            summary_parts.append("")
            
            return "\n".join(summary_parts)
            
        except Exception as e:
            return f"❌ Error analyzing {sbom_file}: {e}"
    
    def generate_overall_summary(self, sbom_files: List[str]) -> str:
        """Generate overall summary across all SBOM files"""
        summary_parts = []
        summary_parts.append("Overall Summary")
        summary_parts.append("=" * 50)
        
        total_components = 0
        total_dependencies = 0
        all_component_types = Counter()
        all_licenses = Counter()
        total_vulnerabilities = 0
        
        for sbom_file in sbom_files:
            if not Path(sbom_file).exists():
                continue
                
            try:
                with open(sbom_file, 'r') as f:
                    sbom_data = json.load(f)
                
                # Count components
                components = sbom_data.get("components", [])
                total_components += len(components)
                
                # Count dependencies
                dependencies = sbom_data.get("dependencies", [])
                total_dependencies += len(dependencies)
                
                # Aggregate component types
                for comp in components:
                    comp_type = comp.get("type", "unknown")
                    all_component_types[comp_type] += 1
                
                # Aggregate licenses
                for comp in components:
                    licenses = comp.get("licenses", [])
                    for license_info in licenses:
                        license_id = license_info.get("id", "unknown")
                        all_licenses[license_id] += 1
                
                # Count vulnerabilities
                for comp in components:
                    vulnerabilities = comp.get("vulnerabilities", [])
                    total_vulnerabilities += len(vulnerabilities)
                    
            except Exception as e:
                summary_parts.append(f"⚠️  Error processing {sbom_file}: {e}")
        
        summary_parts.append(f"Total Components: {total_components}")
        summary_parts.append(f"Total Dependencies: {total_dependencies}")
        summary_parts.append(f"Total Vulnerabilities: {total_vulnerabilities}")
        summary_parts.append("")
        
        if all_component_types:
            summary_parts.append("Component Types:")
            for comp_type, count in all_component_types.most_common():
                summary_parts.append(f"  {comp_type}: {count}")
            summary_parts.append("")
        
        if all_licenses:
            summary_parts.append("Top Licenses:")
            for license_id, count in all_licenses.most_common(10):
                summary_parts.append(f"  {license_id}: {count}")
            summary_parts.append("")
        
        # Security recommendations
        summary_parts.append("Security Recommendations:")
        if total_vulnerabilities > 0:
            summary_parts.append("  ⚠️  Review and update vulnerable components")
        else:
            summary_parts.append("  ✅ No known vulnerabilities detected")
        
        summary_parts.append("  ✅ Regular SBOM updates recommended")
        summary_parts.append("  ✅ Monitor for new vulnerabilities")
        summary_parts.append("  ✅ Keep dependencies up to date")
        
        return "\n".join(summary_parts)
    
    def save_summary(self, summary: str, output_file: str = "sbom-summary.txt") -> None:
        """Save summary to file"""
        with open(output_file, 'w') as f:
            f.write(summary)
        print(f"✅ Summary saved to: {output_file}")

def main():
    # Default SBOM files to analyze
    default_sbom_files = [
        "sbom-java.json",
        "sbom-gradle.json",
        "sbom-android.json"
    ]
    
    # Check which files exist
    existing_files = [f for f in default_sbom_files if Path(f).exists()]
    
    if not existing_files:
        print("❌ No SBOM files found to analyze")
        return 1
    
    generator = SBOMSummaryGenerator()
    
    try:
        # Generate summary
        summary = generator.generate_summary(existing_files)
        
        # Save summary
        generator.save_summary(summary)
        
        # Print summary to console
        print("\n" + summary)
        
        print("✅ SBOM summary generation completed successfully")
        return 0
        
    except Exception as e:
        print(f"❌ Error generating SBOM summary: {e}")
        return 1

if __name__ == '__main__':
    sys.exit(main())
