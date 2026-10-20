#!/usr/bin/env python3
"""Screenshot orchestrator v2: log-driven scheduling so shots line up with
actual game states regardless of process startup delays."""
import os
import subprocess
import time

import Quartz

JAVA = '/opt/homebrew/opt/openjdk@17/bin/java'
JAR = '../desktop/build/libs/desktop-1.0.jar'
OUT = 'docs/screenshots'

procs = {}
logs = {}


def launch(name, props):
    cmd = [JAVA, '-XstartOnFirstThread']
    for k, v in props.items():
        cmd.append(f'-D{k}={v}')
    cmd += ['-jar', JAR]
    log = f'/tmp/shot-{name}.log'
    logs[name] = log
    procs[name] = subprocess.Popen(cmd, cwd='assets', stdout=open(log, 'w'),
                                   stderr=subprocess.STDOUT)


def log_has(name, needle):
    try:
        with open(logs[name]) as f:
            return needle in f.read()
    except FileNotFoundError:
        return False


def wait_log(name, needle, timeout):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if log_has(name, needle):
            return True
        time.sleep(0.25)
    return False


def windows():
    wins = Quartz.CGWindowListCopyWindowInfo(
        Quartz.kCGWindowListOptionOnScreenOnly | Quartz.kCGWindowListExcludeDesktopElements,
        Quartz.kCGNullWindowID)
    result = []
    for w in wins:
        name = w.get('kCGWindowName', '') or ''
        if 'Local FIFA' in name:
            result.append((w['kCGWindowNumber'], w.get('kCGWindowOwnerPID')))
    return result


def shoot(name, filename, wait_window=30):
    deadline = time.time() + wait_window
    wid = None
    while time.time() < deadline:
        for w, wpid in windows():
            if wpid == procs[name].pid:
                wid = w
                break
        if wid:
            break
        time.sleep(0.5)
    if wid is None:
        print(f'  !! no window for {name} ({filename})')
        return
    subprocess.run(['screencapture', '-x', '-o', '-t', 'png', f'-l{wid}',
                    os.path.join(OUT, filename)])
    print(f'  shot {filename}')


def main():
    os.makedirs(OUT, exist_ok=True)

    print('phase A: main menu')
    launch('menu', {})
    time.sleep(14)
    shoot('menu', 'menu.png', wait_window=5)
    procs['menu'].terminate()
    time.sleep(2)

    print('phase B: multiplayer pair (goal 12s after kickoff)')
    launch('host', {'localfifa.role': 'host', 'localfifa.bot': '1',
                    'localfifa.goalAt': '12', 'localfifa.exitAfter': '120'})
    time.sleep(4)
    launch('join', {'localfifa.role': 'join', 'localfifa.bot': '1',
                    'localfifa.botPark': '1', 'localfifa.exitAfter': '120'})

    print('  waiting for match start...')
    wait_log('host', 'constructor mode=HOST', 60)
    time.sleep(6)  # introduction lineup
    shoot('host', 'match-intro-host.png')
    shoot('join', 'match-intro-client.png')

    print('  waiting for INPLAY...')
    if not wait_log('host', 'state=INPLAY', 90):
        print('  !! never reached INPLAY')
        return
    time.sleep(4)  # let the bots fight for the ball a bit
    shoot('host', 'match-inplay-host.png')
    shoot('join', 'match-inplay-client.png')

    # goal fires at 12s of play; GOAL banner lasts ~3s
    time.sleep(8)
    shoot('host', 'goal-host.png')
    shoot('join', 'goal-client.png')

    print('  waiting for auto exit...')
    for name in ('host', 'join'):
        procs[name].wait(timeout=150)
    print('done')


if __name__ == '__main__':
    try:
        main()
    finally:
        for p in procs.values():
            if p.poll() is None:
                p.terminate()
