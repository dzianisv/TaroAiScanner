"""Shared deterministic UI driver for Mystic Tarot demo recordings.

Every tap is resolved from live `uiautomator dump` bounds rather than from
coordinates eyeballed off a screenshot -- two earlier sessions mis-tapped
because of hardcoded pixel coords, and the emulator's layout shifts between
API levels and window insets.

Recording/CFR/speedup/evidence-gating is NOT implemented here on purpose: it
comes from the a-test library (`a_test.recording.record_verified_journey`),
which already solves adb screenrecord's variable-framerate slideshow problem
and its 180s per-segment cap.
"""
import re
import subprocess
import time

PKG = "com.aistudio.mystictarot.qxrptl"
ACT = f"{PKG}/com.example.MainActivity"

# uiautomator dump is an expensive round trip through system_server. Polling it
# once a second drove system_server itself into an ANR on a contended host, so
# every poll loop backs off to this instead.
POLL_SECONDS = 3.0


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)


def dismiss_anr(xml: str = "") -> bool:
    """Clear an 'isn't responding' dialog if one is on screen.

    A stuck ANR dialog steals window focus, so every later dump returns the
    dialog instead of the app and the whole run strands. 'Wait' is chosen over
    'Close app': the app is usually just slow under host load, and closing it
    would abort the journey being recorded.
    """
    xml = xml or _raw_dump()
    if not _is_anr(xml):
        return False
    # resource-id first: locale- and version-stable, unlike the button labels.
    for rid in ("android:id/aerr_wait", "android:id/aerr_close",
                "android:id/aerr_restart"):
        pt = find_id(xml, rid)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  dismissed ANR via {rid}", flush=True)
            time.sleep(2)
            return True
    for choice in (r"^Wait$", r"^Close app$", r"^OK$"):
        pt = find(xml, choice)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  dismissed ANR via {choice}", flush=True)
            time.sleep(2)
            return True
    return False


def _is_anr(xml: str) -> bool:
    return ("isn't responding" in xml
            or "Application Not Responding" in xml
            or "android:id/aerr_" in xml)


def _raw_dump() -> str:
    sh("adb shell rm -f /sdcard/ui.xml")
    sh("adb shell uiautomator dump /sdcard/ui.xml")
    return sh("adb shell cat /sdcard/ui.xml").stdout


def dump() -> str:
    """Current window's UI XML, with any ANR dialog cleared first.

    Retries: under host contention the system throws a fresh ANR almost
    immediately after one is dismissed, and a single dismiss attempt leaves
    the run staring at the dialog for the rest of the journey.
    """
    for _ in range(4):
        xml = _raw_dump()
        if not _is_anr(xml):
            return xml
        if not dismiss_anr(xml):
            return xml
        time.sleep(1.5)
    return _raw_dump()


def _nodes(xml: str):
    for node in re.finditer(r"<node[^>]*>", xml):
        s = node.group(0)
        # Strip: joining text with an empty content-desc yields a trailing
        # space, which silently breaks every anchored pattern ("^Wait$" never
        # matched text="Wait" content-desc="").
        label = " ".join(
            v for v in re.findall(r'(?:text|content-desc)="([^"]*)"', s) if v
        ).strip()
        rid = (re.search(r'resource-id="([^"]*)"', s) or [None, ""])[1] \
            if re.search(r'resource-id="([^"]*)"', s) else ""
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            if x2 > x1 and y2 > y1:
                yield label, rid, (x1, y1, x2, y2)


def find(xml: str, pattern: str):
    """Center (x, y) of the first node whose text/content-desc matches."""
    rx = re.compile(pattern, re.I)
    for label, _rid, (x1, y1, x2, y2) in _nodes(xml):
        if label and rx.search(label):
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def find_id(xml: str, resource_id: str):
    """Center (x, y) of the first node with this resource-id.

    Preferred over text matching for framework dialogs (ANR, permissions):
    resource-ids are stable across locales and Android versions.
    """
    for _label, rid, (x1, y1, x2, y2) in _nodes(xml):
        if rid and rid == resource_id:
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def present(pattern: str) -> bool:
    return find(dump(), pattern) is not None


def tap_text(pattern, timeout=25, label=None) -> bool:
    """Poll for a node matching `pattern`, tap its center."""
    label = label or pattern
    deadline = time.time() + timeout
    while time.time() < deadline:
        pt = find(dump(), pattern)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  tapped {label} @ {pt}", flush=True)
            return True
        time.sleep(POLL_SECONDS)
    print(f"  MISS {label}", flush=True)
    return False


def wait_text(pattern, timeout=60) -> bool:
    """Poll until `pattern` appears on screen."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if find(dump(), pattern):
            return True
        time.sleep(POLL_SECONDS)
    return False


def scroll_to_top(times=6):
    """Fling the dashboard back to the top.

    Required before hunting for anything in the upper part of the screen:
    the dashboard keeps its scroll position across navigation, and a previous
    run stranded itself at the bottom because it only ever scrolled down.
    """
    for _ in range(times):
        sh("adb shell input swipe 540 800 540 2000 200")
        time.sleep(0.6)
    time.sleep(1.5)


def scroll_to_tap(pattern, label=None, max_scrolls=8, direction="down",
                  from_top=True) -> bool:
    """Scroll the dashboard until `pattern` is visible, then tap it.

    `from_top=True` first flings back to the top, so the search always starts
    from a known position -- otherwise a target that lives ABOVE the current
    viewport can never be reached by scrolling down and the run strands at the
    bottom of the list (this exact bug killed the first paywall recording).
    """
    label = label or pattern
    if from_top:
        scroll_to_top()
    swipe = ("adb shell input swipe 540 1900 540 900 400" if direction == "down"
             else "adb shell input swipe 540 900 540 1900 400")
    for i in range(max_scrolls):
        pt = find(dump(), pattern)
        if pt:
            sh(f"adb shell input tap {pt[0]} {pt[1]}")
            print(f"  tapped {label} @ {pt} (after {i} scrolls)", flush=True)
            return True
        sh(swipe)
        time.sleep(1.5)
    print(f"  MISS {label}", flush=True)
    return False


def cold_start(grant_camera=True):
    """Force-stop and relaunch the app, landing on auth or dashboard."""
    if grant_camera:
        sh(f"adb shell pm grant {PKG} android.permission.CAMERA")
    sh(f"adb shell am force-stop {PKG}")
    time.sleep(1)
    sh(f"adb shell am start -n {ACT}")
    time.sleep(6)
    # A persistent guest session usually skips onboarding. Match the full
    # button label: a bare /Guest/ also matches the "Guest Seeker" profile row
    # on the dashboard and taps the wrong element.
    tap_text(r"Continue as Guest", timeout=8, label="Continue as Guest")
    time.sleep(4)


def back():
    sh("adb shell input keyevent KEYCODE_BACK")
    time.sleep(1.5)


def dwell(seconds, why=""):
    """Hold on the current screen so the recording captures it clearly.

    Dwelling is required, not wasteful: screenrecord emits frames on change,
    and a screen that is only on-camera for one frame vanishes entirely after
    the 10x speedup.
    """
    if why:
        print(f"  dwell {seconds}s ({why})", flush=True)
    time.sleep(seconds)


def slow_scroll(times=2, up=True):
    """Gentle scrolls that give the encoder real, distinct motion to capture."""
    for _ in range(times):
        if up:
            sh("adb shell input swipe 540 1700 540 1000 700")
        else:
            sh("adb shell input swipe 540 1000 540 1700 700")
        time.sleep(2.5)


def focused_package() -> str:
    """Package owning the focused window, from dumpsys.

    Used to tell a REAL Google Play purchase sheet (com.android.vending) from
    our own paywall: matching on visible text is not sufficient, because the
    paywall itself contains the strings "SUBSCRIBE" and "Google Play".
    """
    out = sh("adb shell dumpsys window | grep -i mCurrentFocus").stdout
    m = re.search(r"u0\s+([A-Za-z0-9_.]+)/", out)
    return m.group(1) if m else ""
