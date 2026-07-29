#!/usr/bin/env python3
"""Deterministic CUA demo driver for Mystic Tarot.

Drives the real scan -> Gemini reading journey on a connected emulator while
`adb shell screenrecord` captures the screen, then pulls the raw recording.

Why not screenshot-pixel coords: two earlier sessions mis-tapped because the
coords were eyeballed off a screenshot. This resolves every tap from
`uiautomator dump` bounds, which is the only reliable source on this device.

Why one process: `screenrecord --time-limit` counts wall-clock, and each agent
tool-call round trip costs real seconds -- driving the flow across several
invocations let the recording self-terminate before the AI reading rendered.
Everything therefore runs inside this single process.
"""
import re
import subprocess
import sys
import time

PKG = "com.aistudio.mystictarot.qxrptl"
ACT = f"{PKG}/com.example.MainActivity"
DEV_MP4 = "/sdcard/taro_demo.mp4"


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)


def dump():
    """Return the current window's UI XML, or '' if the dump failed."""
    sh(f"adb shell rm -f /sdcard/ui.xml")
    sh(f"adb shell uiautomator dump /sdcard/ui.xml")
    return sh("adb shell cat /sdcard/ui.xml").stdout


def find(xml, pattern):
    """Center (x, y) of the first node whose text/desc matches `pattern`."""
    rx = re.compile(pattern, re.I)
    for node in re.finditer(r"<node[^>]*>", xml):
        s = node.group(0)
        label = " ".join(re.findall(r'(?:text|content-desc)="([^"]*)"', s))
        if label.strip() and rx.search(label):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                if x2 > x1 and y2 > y1:
                    return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap_text(pattern, timeout=25, label=None):
    """Poll for a node matching `pattern`, tap its center. True if tapped."""
    label = label or pattern
    deadline = time.time() + timeout
    while time.time() < deadline:
        pt = find(dump(), pattern)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  tapped {label} @ {pt}", flush=True)
            return True
        time.sleep(1)
    print(f"  MISS {label}", flush=True)
    return False


def wait_text(pattern, timeout=60):
    """Poll until `pattern` appears on screen. True if it showed up."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if find(dump(), pattern):
            return True
        time.sleep(1.5)
    return False


def scroll_to_tap(pattern, label=None, max_scrolls=6):
    """Scroll the dashboard until `pattern` is visible, then tap it.

    The dashboard is a long scroller and "Scan Physical Card" sits below the
    fold; a single fixed-distance swipe is not enough on every screen size.
    """
    label = label or pattern
    for i in range(max_scrolls):
        pt = find(dump(), pattern)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  tapped {label} @ {pt} (after {i} scrolls)", flush=True)
            return True
        sh("adb shell input swipe 540 1900 540 900 400")
        time.sleep(1.5)
    print(f"  MISS {label}", flush=True)
    return False


def main():
    sh(f"adb shell pm grant {PKG} android.permission.CAMERA")
    sh(f"adb shell am force-stop {PKG}")
    sh(f"adb shell rm -f {DEV_MP4}")
    time.sleep(1)

    # 720x1600 instead of native 1080x2400: the emulator's encoder cannot keep
    # up at native size under load (a previous run captured 67 frames over 112s
    # -- an unusable slideshow), and dropped frames also cost us the tail.
    rec = subprocess.Popen(
        f"adb shell screenrecord --size 720x1600 --bit-rate 4000000 "
        f"--time-limit 180 {DEV_MP4}",
        shell=True,
    )
    time.sleep(2)

    sh(f"adb shell am start -n {ACT}")
    time.sleep(6)

    # The app keeps a persistent guest session, so onboarding usually does not
    # appear. Match the button's full label -- a bare /Guest/ also matches the
    # "Guest Seeker" profile row on the dashboard and taps the wrong thing.
    tap_text(r"Continue as Guest", timeout=6, label="Continue as Guest")
    time.sleep(3)

    scroll_to_tap(r"Single Card", label="Single Card (scan entry)")
    time.sleep(4)

    # Runtime permission dialog, if the grant above did not suppress it.
    tap_text(r"While using this app|^Allow$|WHILE USING", timeout=6, label="camera allow")
    time.sleep(3)

    tap_text(r"REVEAL COSMIC TRUTH", timeout=30, label="capture")

    ok = wait_text(r"GENERAL INTERPRETATION|INTERPRETATION|Upright|Reversed", timeout=75)
    print(f"  reading rendered: {ok}", flush=True)

    if ok:
        # Hold on the result, then scroll it so the reading text is unambiguously
        # on camera. The previous run stopped recording ~1s after the reading
        # appeared and the encoder never flushed those frames -- the finished mp4
        # ended back on the camera screen. Dwell generously instead.
        time.sleep(6)
        sh("adb shell input swipe 540 1700 540 900 600")
        time.sleep(5)
        sh("adb shell input swipe 540 1700 540 1100 600")
        time.sleep(6)
    else:
        time.sleep(2)

    sh("adb shell pkill -SIGINT screenrecord")
    try:
        rec.wait(timeout=40)
    except subprocess.TimeoutExpired:
        rec.kill()
    # screenrecord finalizes the moov atom after SIGINT; pulling too early
    # yields a file whose tail is missing.
    time.sleep(8)

    sh(f"adb pull {DEV_MP4} /tmp/taro_demo_raw.mp4")
    print(sh("ls -la /tmp/taro_demo_raw.mp4").stdout, flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
