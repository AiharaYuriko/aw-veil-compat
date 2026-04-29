#!/usr/bin/env python3
"""
AW-Veil Compat — Probe Log Correlation Script

Reads AW-side and Veil-side probe TSV logs, aligns them by nanoTime,
and identifies shader program mismatches.

Usage:
    python correlate_probes.py --aw probes/aw-probe.log --veil probes/veil-probe.log
    python correlate_probes.py --dir runs/client/probes/  # auto-find latest logs

Output:
    - Correlation summary with timing deltas
    - Program ID mismatches at AW render points vs Veil shader events
    - Timeline of events leading up to each AW clearRenderState event
"""

import argparse
import csv
import os
import sys
from datetime import datetime


def parse_tsv(filepath):
    """Parse a TSV probe log file, returning list of dicts."""
    events = []
    with open(filepath, 'r') as f:
        reader = csv.reader(f, delimiter='\t')
        for row in reader:
            if not row or row[0].startswith('#'):
                continue
            if len(row) < 3:
                continue
            try:
                events.append({
                    'nano_time': int(row[0]),
                    'event_type': row[1],
                    'data': row[2] if len(row) > 2 else '',
                })
            except ValueError:
                continue
    return events


def parse_data(data_str):
    """Parse 'key=value\tkey=value' string into dict."""
    result = {}
    for pair in data_str.split('\t'):
        if '=' in pair:
            k, v = pair.split('=', 1)
            result[k] = v
    return result


def correlate(aw_events, veil_events):
    """Align AW and Veil events by nanoTime and find mismatches."""
    if not aw_events:
        print("WARNING: No AW-side probe events found.")
        return
    if not veil_events:
        print("WARNING: No Veil-side probe events found.")
        aw_baseline = parse_data(aw_events[0]['data'])
        print(f"AW baseline (no Veil data): program={aw_baseline.get('program', 'N/A')}")
        return

    aw_start = aw_events[0]['nano_time']
    veil_start = veil_events[0]['nano_time']
    timeline_start = min(aw_start, veil_start)

    print("=" * 60)
    print(f"AW-VEIL PROBE CORRELATION REPORT")
    print(f"Generated: {datetime.now().isoformat()}")
    print("=" * 60)
    print(f"\nProbe timeline start: {timeline_start}")
    print(f"AW events: {len(aw_events)}")
    print(f"Veil events: {len(veil_events)}")

    veil_sorted = sorted(veil_events, key=lambda e: e['nano_time'])
    aw_render_events = [e for e in aw_events if e['event_type'] == 'clearRenderState']

    if not aw_render_events:
        print("\nWARNING: No clearRenderState events in AW probe log.")
        print("AW probe may not be firing. Check if AW mod is loaded.")
        return

    print(f"\nAW clearRenderState events: {len(aw_render_events)}")

    for i, aw_evt in enumerate(aw_render_events):
        aw_data = parse_data(aw_evt['data'])
        program = aw_data.get('program', '?')
        mv_loc = aw_data.get('aw_ModelViewMatrix', '?')
        flags_loc = aw_data.get('aw_MatrixFlags', '?')
        tex_loc = aw_data.get('aw_TextureMatrix', '?')

        aw_time = aw_evt['nano_time']
        nearby = [e for e in veil_sorted
                  if abs(e['nano_time'] - aw_time) < 1_000_000]

        print(f"\n  --- AW Event {i + 1} (t={aw_time}) ---")
        print(f"  Program: {program}")
        print(f"  Uniforms: aw_ModelViewMatrix={mv_loc}, aw_MatrixFlags={flags_loc}, aw_TextureMatrix={tex_loc}")

        if program == '0':
            print(f"  STATUS: NO PROGRAM BOUND at AW render point")
        elif mv_loc == '-1':
            print(f"  STATUS: PROBABLE MISMATCH -- AW uniforms not found on program {program}")
        else:
            print(f"  STATUS: AW uniforms FOUND on program {program} (expected behavior)")

        if nearby:
            print(f"  Nearby Veil events ({len(nearby)} within +/-1ms):")
            for ve in nearby:
                delta = ve['nano_time'] - aw_time
                vdata = parse_data(ve['data'])
                print(f"    [{delta:+10d}ns] {ve['event_type']}: {vdata}")
        else:
            print(f"  No nearby Veil events within +/-1ms")

        if i > 0:
            prev_time = aw_render_events[i - 1]['nano_time']
            delta = aw_time - prev_time
            print(f"  Time since last AW event: {delta / 1_000_000:.2f}ms")

    print("\n" + "=" * 60)
    programs_with_uniforms = sum(
        1 for e in aw_render_events
        if parse_data(e['data']).get('aw_ModelViewMatrix', '-1') != '-1'
    )
    mismatches = len(aw_render_events) - programs_with_uniforms
    print(f"SUMMARY: {programs_with_uniforms}/{len(aw_render_events)} AW events had uniforms found")
    print(f"         {mismatches}/{len(aw_render_events)} probable mismatches detected")

    if mismatches > 0:
        print("\nROOT CAUSE HYPOTHESIS: The shader program bound at AW's render point")
        print("is not a vanilla program with AW's uniforms. Veil has likely replaced")
        print("the program before AW's hook fires.")
    else:
        print("\nAll AW uniform lookups succeeded -- no program confusion detected.")
        print("Root cause may be elsewhere (matrix state, VAO binding, etc.)")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description='Correlate AW and Veil probe timelines'
    )
    parser.add_argument('--aw', help='Path to AW probe log')
    parser.add_argument('--veil', help='Path to Veil probe log')
    parser.add_argument('--dir', help='Directory containing probe logs (auto-detect)')
    args = parser.parse_args()

    aw_path = args.aw
    veil_path = args.veil

    if args.dir:
        aw_candidates = [f for f in os.listdir(args.dir) if 'aw-probe' in f]
        veil_candidates = [f for f in os.listdir(args.dir) if 'veil-probe' in f]
        if aw_candidates:
            aw_path = os.path.join(args.dir, sorted(aw_candidates)[-1])
        if veil_candidates:
            veil_path = os.path.join(args.dir, sorted(veil_candidates)[-1])

    if not aw_path or not os.path.exists(aw_path):
        print(f"AW probe log not found: {aw_path}")
        sys.exit(1)
    if not veil_path or not os.path.exists(veil_path):
        print(f"Veil probe log not found: {veil_path}")
        print("Continuing with AW-only analysis...")
        veil_events = []
    else:
        veil_events = parse_tsv(veil_path)

    aw_events = parse_tsv(aw_path)
    correlate(aw_events, veil_events)


if __name__ == '__main__':
    main()
