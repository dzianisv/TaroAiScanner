"""On-device proof for the "no silent mock reading" fix.

Runs the virtual Cosmic Draw journey twice against the SAME build:

  1. blocked  -- the saved proxy endpoint is repointed at a dead local port, so
                 the Gemini call fails. The
                 screen must show the honest "Cosmic Disconnection" error. This
                 is exactly where the canned getMockReading() text used to be
                 rendered as if it were the AI's answer.
  2. restored -- the real proxy is put back, and the real reading renders as
                 before.

Both states are captured as PNGs. Nothing here re-implements tapping: it all
comes from taro_driver, the same deterministic uiautomator-bounds driver used
by record_journeys.py.
"""
import os
import sqlite3
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import taro_driver as d

SERIAL = "emulator-5560"
OUT = Path(__file__).resolve().parents[2] / "release-artifacts" / "trust-proof"

# taro_driver shells out to a bare `adb`; pin it to the demo AVD so a second
# attached emulator (owned by another agent) is never touched.
_sh = d.sh
d.sh = lambda cmd, **kw: _sh(cmd.replace("adb ", f"adb -s {SERIAL} ", 1), **kw)


DB = "/data/data/com.aistudio.mystictarot.qxrptl/databases/tarot_database"
LIVE_PROXY = "https://securegeminiproxy-248382356220.us-central1.run.app"
# Discard port on loopback: nothing listens, so the proxy host is unreachable
# and the Gemini call fails fast, exactly as a dropped network would. Chosen
# over airplane mode / `svc wifi disable`, which killed the emulator outright
# on this host. https:// (not http://) so the failure is a real connection
# failure rather than Android's cleartext-traffic policy rejecting the URL.
DEAD_PROXY = "https://127.0.0.1:9/gemini"


def set_proxy(url: str):
    """Point the app's saved proxy endpoint at `url` (blocks or restores it).

    Edits the Room settings row directly: the alternative is driving the
    Settings dialog's text field through the soft keyboard, which is far more
    fragile and is not what is under test here.

    The write is checkpointed into the main database file and the on-device
    -wal/-shm are deleted. Pushing a stale -wal back alongside a patched main
    file silently loses the edit -- Room replays the newer WAL frame on open,
    which is exactly why the first attempt at this still saw a live proxy.
    """
    d.sh(f"adb shell am force-stop {d.PKG}")
    time.sleep(2)
    local = Path("/tmp/tarot_settings.db")
    for suffix in ("", "-wal", "-shm"):
        Path(f"{local}{suffix}").unlink(missing_ok=True)
        _sh(f"adb -s {SERIAL} exec-out run-as {d.PKG} cat {DB}{suffix} > "
            f"{local}{suffix} 2>/dev/null")
        f = Path(f"{local}{suffix}")
        if f.exists() and f.stat().st_size == 0:
            f.unlink()

    con = sqlite3.connect(local)
    changed = con.execute(
        "UPDATE tarot_settings SET proxyUrl = ?", (url,)
    ).rowcount
    con.commit()
    con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    con.commit()
    con.close()
    if changed < 1:
        raise RuntimeError("no tarot_settings row to patch -- launch the app first")

    _sh(f"adb -s {SERIAL} push {local} /data/local/tmp/settings.db")
    _sh(f"adb -s {SERIAL} shell run-as {d.PKG} cp /data/local/tmp/settings.db {DB}")
    for suffix in ("-wal", "-shm"):
        _sh(f"adb -s {SERIAL} shell run-as {d.PKG} rm -f {DB}{suffix}")
    print(f"  proxy set to {url} ({changed} row)", flush=True)


def read_proxy() -> str:
    """Read the proxy back off the device, so the run can prove it took."""
    local = Path("/tmp/tarot_verify.db")
    for suffix in ("", "-wal", "-shm"):
        Path(f"{local}{suffix}").unlink(missing_ok=True)
        _sh(f"adb -s {SERIAL} exec-out run-as {d.PKG} cat {DB}{suffix} > "
            f"{local}{suffix} 2>/dev/null")
        f = Path(f"{local}{suffix}")
        if f.exists() and f.stat().st_size == 0:
            f.unlink()
    con = sqlite3.connect(local)
    value = con.execute("SELECT proxyUrl FROM tarot_settings LIMIT 1").fetchone()[0]
    con.close()
    return value


def screenshot(name):
    OUT.mkdir(parents=True, exist_ok=True)
    dest = OUT / f"{os.environ.get('PROOF_PREFIX', '')}{name}.png"
    d.sh("adb shell screencap -p /sdcard/proof.png")
    d.sh(f"adb pull /sdcard/proof.png {dest}")
    print(f"  saved {dest}", flush=True)
    return dest


def patient_cold_start():
    """cold_start(), but wait for the app to actually own the window first.

    The stock 6s wait in taro_driver is tuned for a warm host; on a freshly
    rebooted emulator the first draw takes far longer, and every later lookup
    then MISSes because the launcher still owns the window.
    """
    d.sh(f"adb shell pm grant {d.PKG} android.permission.CAMERA")
    d.sh(f"adb shell am force-stop {d.PKG}")
    time.sleep(2)
    d.sh(f"adb shell am start -n {d.ACT}")
    deadline = time.time() + 120
    while time.time() < deadline:
        if d.focused_package() == d.PKG:
            break
        time.sleep(3)
    print(f"  app focused: {d.focused_package()}", flush=True)
    time.sleep(5)
    # A persistent guest session skips onboarding, so a MISS here is fine.
    d.tap_text(r"Continue as Guest", timeout=40, label="Continue as Guest")
    d.wait_text(r"Draw Virtual Card|Single Card Draw", timeout=90)
    time.sleep(3)


def draw_virtual_card():
    """Dashboard -> Draw Virtual Card -> pick a card -> DRAW CELESTIAL CARD."""
    patient_cold_start()
    d.scroll_to_tap(r"Draw Virtual Card", label="Draw Virtual Card")
    time.sleep(3)
    print(f"  sanctuary: {d.wait_text(r'Celestial Sanctuary|Flicker of Fate', timeout=25)}",
          flush=True)
    if not d.tap_text(r"Cosmic Tarot Card Back", timeout=20, label="card in fan"):
        d.sh("adb shell input tap 540 1250")
    time.sleep(3)
    print(f"  draw tapped: {d.tap_text(r'DRAW CELESTIAL CARD', timeout=25)}", flush=True)


def run_failure_case():
    print("[1/2] proxy BLOCKED -- expecting an honest error, not canned text",
          flush=True)
    # The app must already have a settings row before it can be patched.
    patient_cold_start()
    set_proxy(DEAD_PROXY)
    assert read_proxy() == DEAD_PROXY, f"proxy patch did not stick: {read_proxy()}"
    print("  verified on-device proxy is blocked", flush=True)
    draw_virtual_card()

    # The AI call must resolve to the error state, never to a rendered reading.
    got_error = d.wait_text(r"Cosmic Disconnection|ethereal link|Celestial disturbance",
                            timeout=120)
    time.sleep(2)
    xml = d.dump()
    shot = screenshot("virtual_draw_offline_error")

    # The tell-tale of the old bug: mock prose rendered under a normal reading
    # layout with no error and no offline label.
    rendered_reading = ("GENERAL INTERPRETATION" in xml.upper()
                        and "COSMIC DISCONNECTION" not in xml.upper())
    print(f"  honest error shown: {got_error}", flush=True)
    print(f"  silent mock reading rendered: {rendered_reading}", flush=True)
    return {"error_shown": got_error, "silent_mock": rendered_reading, "shot": shot}


def run_happy_case():
    print("[2/2] proxy RESTORED -- expecting the real AI reading", flush=True)
    set_proxy(LIVE_PROXY)
    assert read_proxy() == LIVE_PROXY
    draw_virtual_card()

    got_reading = d.wait_text(r"GENERAL INTERPRETATION|INTERPRETATION|KEYWORDS",
                              timeout=150)
    time.sleep(2)
    xml = d.dump()
    shot = screenshot("virtual_draw_happy_path")
    labelled_offline = "OFFLINE SAMPLE READING" in xml.upper()
    print(f"  reading rendered: {got_reading}", flush=True)
    print(f"  offline banner present (should be False online): {labelled_offline}",
          flush=True)
    return {"reading": got_reading, "offline_banner": labelled_offline, "shot": shot}


def main():
    # `--failure-only` captures just the blocked-network state. Used to
    # photograph the PRE-FIX build, which rendered canned getMockReading()
    # text on this exact screen.
    failure_only = "--failure-only" in sys.argv
    try:
        fail = run_failure_case()
        happy = None if failure_only else run_happy_case()
    finally:
        set_proxy(LIVE_PROXY)

    if failure_only:
        print("\n=== FAILURE-PATH CAPTURE ===")
        print(f"  honest error shown:           {fail['error_shown']}")
        print(f"  silent mock reading rendered: {fail['silent_mock']}")
        print(f"  screenshot: {fail['shot']}")
        return 0

    ok = (fail["error_shown"] and not fail["silent_mock"]
          and happy["reading"] and not happy["offline_banner"])
    print("\n=== VERDICT ===")
    print(f"  failure path -> honest error, no silent mock: "
          f"{fail['error_shown'] and not fail['silent_mock']}")
    print(f"  happy path   -> real AI reading:              {happy['reading']}")
    print(f"  screenshots: {fail['shot']}\n               {happy['shot']}")
    print("PASS" if ok else "FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
