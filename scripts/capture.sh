#!/usr/bin/env python3
"""Capture game windows by title and crop them out of a full-screen shot."""
import subprocess
import sys
import time

import Quartz


def find_windows(title_part):
    wins = Quartz.CGWindowListCopyWindowInfo(
        Quartz.kCGWindowListOptionOnScreenOnly | Quartz.kCGWindowListExcludeDesktopElements,
        Quartz.kCGNullWindowID)
    found = []
    for w in wins:
        name = w.get('kCGWindowName', '') or ''
        owner = w.get('kCGWindowOwnerName', '') or ''
        if title_part in name or ('java' in owner.lower() and name):
            bounds = w.get('kCGWindowBounds')
            if bounds and bounds.get('Width', 0) > 500:
                found.append((w['kCGWindowNumber'], w.get('kCGWindowOwnerPID'), dict(bounds)))
    return found


def capture(out_path):
    subprocess.run(['screencapture', '-x', '-t', 'png', out_path], check=True)


def crop(src, out, x, y, w, h):
    # sips crops around the center, so we pad to a full canvas first
    import struct
    with open(src, 'rb') as f:
        data = f.read()
    # read PNG IHDR for width/height
    total_w, total_h = struct.unpack('>II', data[16:24])
    px = int(x + w / 2)
    py = int(y + h / 2)
    subprocess.run(['sips', '-c', str(int(h)), str(int(w)),
                    '--cropOffset', str(int(total_h - py)), str(int(px)),
                    src, '--out', out], check=True,
                   capture_output=True)


if __name__ == '__main__':
    title = sys.argv[1] if len(sys.argv) > 1 else 'Local FIFA'
    wins = find_windows(title)
    for wid, pid, b in wins:
        print(f'wid={wid} pid={pid} bounds={b}')
