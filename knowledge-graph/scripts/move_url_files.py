#!/usr/bin/env python3
"""
Script to move URL files to metadata folders.
Run this script to reorganize existing URL files.
"""

import os
import shutil

def move_url_files():
    """Move *_urls.txt files to metadata subfolders."""
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    raw_dir = os.path.join(base_dir, "data", "raw")
    
    moved_count = 0
    
    for source in ["vnexpress", "genk", "dantri"]:
        source_dir = os.path.join(raw_dir, source)
        if not os.path.exists(source_dir):
            print(f"  ⚠ {source} directory not found")
            continue
        
        metadata_dir = os.path.join(source_dir, "metadata")
        
        # Create metadata directory if not exists
        os.makedirs(metadata_dir, exist_ok=True)
        
        # Find and move URL files
        for filename in os.listdir(source_dir):
            if filename.endswith("_urls.txt"):
                src_file = os.path.join(source_dir, filename)
                dst_file = os.path.join(metadata_dir, filename)
                
                # Skip if already in metadata
                if os.path.exists(dst_file):
                    print(f"  ✓ {source}/{filename} already in metadata")
                    continue
                
                try:
                    shutil.move(src_file, dst_file)
                    print(f"  ✓ Moved: {source}/{filename} -> {source}/metadata/{filename}")
                    moved_count += 1
                except Exception as e:
                    print(f"  ❌ Error moving {source}/{filename}: {e}")
    
    print(f"\n{'='*50}")
    print(f"Total files moved: {moved_count}")
    print(f"{'='*50}")
    
    return moved_count


if __name__ == "__main__":
    print("Moving URL files to metadata folders...")
    print(f"{'='*50}")
    move_url_files()