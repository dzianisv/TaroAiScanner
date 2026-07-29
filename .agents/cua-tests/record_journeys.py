#!/usr/bin/env python3
"""Record the three NEW Mystic Tarot demo journeys as YouTube Shorts.

    python record_journeys.py paywall|spread|chat

Each run produces, under release-artifacts/demo/:
    taro_<journey>_raw.mp4     full-length, constant-framerate 30fps
    taro_<journey>_short.mp4   sped up to ~30s, vertical, h264, <60s

Recording, CFR re-encoding, segment concatenation and the motion-distinctness
evidence gate all come from the a-test library -- none of it is reimplemented
here (see a_test/recording.py and a_test/evidence.py).

Why raw is kept: the speedup factor is derived from the raw duration, and a
re-cut (different target length, different trim) must never require a re-run
on the emulator.
"""
import json
import re
import subprocess
import sys
import time
from pathlib import Path

from a_test.recording import SegmentedRecorder
from a_test.evidence import verify_video_evidence

from taro_driver import (
    PKG, sh, tap_text, wait_text, scroll_to_tap, cold_start, back,
    dwell, slow_scroll, present, find, dump, focused_package, scroll_to_top,
)

OUT = Path(__file__).resolve().parents[2] / "release-artifacts" / "demo"
WORK = Path("/tmp/taro-journeys")
TARGET_SHORT_SECONDS = 30.0


# --------------------------------------------------------------------------
# Journeys
# --------------------------------------------------------------------------

def journey_paywall():
    """Dashboard -> Upgrade to Premium -> Mystic Premium paywall -> SUBSCRIBE.

    The build under test is a debug-signed sideload on an emulator with no
    signed-in Play account, so Google Play Billing cannot serve ProductDetails:
    the price row renders "Loading price..." rather than the real $4.99/mo, and
    launchBillingFlow() has nothing to launch. That is recorded and reported
    honestly -- the run asserts on the FOCUSED WINDOW's package to decide
    whether a genuine Play sheet appeared, because the paywall's own text
    contains both "SUBSCRIBE" and "Google Play" and matching on text alone
    produces a false positive.
    """
    # The dashboard is a long scroller whose top / middle / bottom are
    # genuinely different views. The paywall, by contrast, fits on one screen
    # and does not scroll -- so dwelling on it is dead air that collapses the
    # distinct-screen count. Spend the runtime on the scroller, not the
    # static sheet.
    dwell(5, "dashboard top - profile + Upgrade to Premium")
    slow_scroll(1)
    dwell(5, "dashboard middle - Chat / Draw Virtual Card")
    slow_scroll(1)
    dwell(5, "dashboard bottom - Scan Physical Card + spreads")

    ok_paywall = scroll_to_tap(r"Upgrade to Premium", label="Upgrade to Premium")
    time.sleep(2)
    ok_paywall = ok_paywall and wait_text(r"MYSTIC PREMIUM", timeout=20)
    print(f"  paywall shown: {ok_paywall}", flush=True)
    dwell(7, "MYSTIC PREMIUM offer: benefits, price row, SUBSCRIBE")

    # What the price row actually says is the evidence for whether Play
    # Billing served ProductDetails at all.
    xml = dump()
    if find(xml, r"Loading price"):
        price_seen = "Loading price… (Play Billing served no ProductDetails)"
    elif find(xml, r"/ month"):
        price_seen = "localized ProductDetails price rendered"
    else:
        price_seen = "price row not found"

    tapped_subscribe = tap_text(r"SUBSCRIBE", timeout=20, label="SUBSCRIBE")
    # Deliberately short: with Play Billing unavailable nothing changes on
    # screen, so a long wait here is dead air.
    dwell(5, "waiting for the Play purchase sheet")

    # Only the Play Store owning the focused window counts as a real sheet.
    focus = focused_package()
    play_sheet = focus in ("com.android.vending", "com.google.android.finsky")
    stop_point = (
        f"Play purchase sheet appeared (focus={focus})" if play_sheet
        else f"stopped at the in-app paywall; no Play sheet (focus={focus})"
    )
    print(f"  {stop_point}", flush=True)

    # End on the dashboard's spread options -- a meaningful screen, not a
    # motionless paywall.
    tap_text(r"^Close$|Dismiss", timeout=4, label="close")
    back()
    dwell(3, "back on dashboard")
    slow_scroll(2)
    dwell(5, "dashboard bottom - spread options")

    return {
        "paywall_shown": ok_paywall,
        "subscribe_tapped": tapped_subscribe,
        "price_row": price_seen,
        "focused_package_after_subscribe": focus,
        "play_sheet": play_sheet,
        "stop_point": stop_point,
    }


def journey_spread():
    """Dashboard -> Draw Virtual Card -> Celestial Sanctuary fan -> AI reading.

    This is the virtual Cosmic Draw flow (no camera): a fan of card backs is
    shown, one is selected, and DRAW CELESTIAL CARD sends it for the real
    Gemini interpretation.
    """
    dwell(4, "dashboard")
    slow_scroll(1)

    scroll_to_tap(r"Draw Virtual Card", label="Draw Virtual Card")
    time.sleep(3)
    in_sanctuary = wait_text(r"Celestial Sanctuary|Flicker of Fate", timeout=25)
    print(f"  celestial sanctuary: {in_sanctuary}", flush=True)
    dwell(6, "cosmic card fan")

    # Pick a card out of the fan. The card backs carry the content-desc
    # "Cosmic Tarot Card Back"; tapping one selects it ("Destiny Selected").
    picked = tap_text(r"Cosmic Tarot Card Back", timeout=20, label="card in fan")
    if not picked:
        # Fallback: tap the middle of the fan area.
        sh("adb shell input tap 540 1250")
        print("  tapped fan centre (fallback)", flush=True)
    time.sleep(3)
    dwell(4, "card selected / flip animation")

    drew = tap_text(r"DRAW CELESTIAL CARD", timeout=25, label="DRAW CELESTIAL CARD")
    print(f"  draw tapped: {drew}", flush=True)

    # Real backend AI call. Never navigate away while this runs -- pressing
    # back during the loading state corrupts the nav stack.
    reading = wait_text(
        r"GENERAL INTERPRETATION|INTERPRETATION|KEYWORDS|Upright|Reversed",
        timeout=120,
    )
    print(f"  reading rendered: {reading}", flush=True)

    if reading:
        dwell(6, "reading result")
        slow_scroll(2)
        dwell(5, "scrolled reading text")
    else:
        dwell(6, "no reading -- holding final state")

    return {"sanctuary": in_sanctuary, "draw_tapped": drew, "reading": reading}


def journey_chat():
    """Dashboard -> Chat with Tarot Master -> ask a question -> AI response."""
    dwell(4, "dashboard top")
    slow_scroll(1)
    dwell(3, "dashboard - Chat with Tarot Master card")

    scroll_to_tap(r"Chat with Tarot Master", label="Chat with Tarot Master")
    time.sleep(3)
    in_chat = wait_text(r"Tarot Master|Ask the Tarot Master", timeout=25)
    print(f"  chat open: {in_chat}", flush=True)
    dwell(5, "chat screen - Enter the Sacred Sanctuary")

    question = "What does The Star card mean for my career?"
    replied = False
    answer = ""
    for attempt in range(1, 3):
        tap_text(r"Ask the Tarot Master", timeout=15, label="chat input")
        time.sleep(2)
        sh(f"adb shell input text '{question.replace(' ', '%s')}'")

        # `input text` types character by character through the IME and is slow
        # on a loaded emulator. The first run tapped Send while only "What does
        # the Sta" had landed, so a truncated prompt was sent. Wait until the
        # field actually holds the whole question before sending.
        typed = False
        for _ in range(12):
            if find(dump(), re.escape(question[-12:])):
                typed = True
                break
            time.sleep(2)
        print(f"  full question typed: {typed} (attempt {attempt})", flush=True)
        dwell(3, "question visible in the composer")

        if not tap_text(r"^Send$", timeout=15, label="Send"):
            sh("adb shell input keyevent KEYCODE_ENTER")
            print("  pressed ENTER (Send button not found)", flush=True)
        time.sleep(2)
        sh("adb shell input keyevent KEYCODE_BACK")  # hide the IME
        time.sleep(2)

        # Wait for a real assistant answer. Two things must NOT be counted as
        # an AI response: the app's own error bubble ("The ethereal connection
        # was interrupted"), and any long static copy that belongs to another
        # screen -- a previous run reported success after matching the
        # dashboard's "Interact in real-time with our wise Tarot Master..."
        # blurb because the journey never reached the chat at all.
        STATIC_COPY = (
            "Interact in real-time",
            "Ask about your fate",
            "No physical deck handy",
            "Let the AI analyze",
            "The tarot is a mirror",
        )
        deadline = time.time() + 150
        while time.time() < deadline:
            xml = dump()
            if find(xml, r"ethereal connection was interrupted|interrupted: timeout"):
                answer = "backend error bubble"
                print(f"  backend error on attempt {attempt}", flush=True)
                break
            on_chat_screen = bool(find(xml, r"Ask the Tarot Master"))
            bodies = [
                t for t in re.findall(r'text="([^"]{120,})"', xml)
                if question[:20] not in t
                and not any(c in t for c in STATIC_COPY)
            ]
            if on_chat_screen and bodies:
                replied = True
                answer = bodies[0][:200]
                break
            time.sleep(4)
        if replied:
            break
        # Clear the failed exchange and retry once.
        tap_text(r"Clear Chat History", timeout=6, label="clear chat")
        time.sleep(2)
        tap_text(r"^Clear$|^OK$|^Delete$", timeout=5, label="confirm clear")
        time.sleep(2)

    print(f"  AI replied: {replied}", flush=True)
    dwell(7, "AI response on screen")
    slow_scroll(1)
    dwell(5, "scrolled response")

    return {"chat_open": in_chat, "question": question,
            "ai_replied": replied, "answer_excerpt": answer}


JOURNEYS = {
    "paywall": ("taro_paywall", journey_paywall),
    "spread": ("taro_spread", journey_spread),
    "chat": ("taro_chat", journey_chat),
}


# --------------------------------------------------------------------------
# Recording + cut
# --------------------------------------------------------------------------

def probe_duration(path) -> float:
    r = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "csv=p=0", str(path)],
        capture_output=True, text=True, timeout=60,
    )
    try:
        return float(r.stdout.strip())
    except ValueError:
        return 0.0


def cut_short(raw: Path, short: Path, speedup: float, fps: int = 30) -> bool:
    """Speed up the raw capture into a vertical, CFR, h264 Short.

    setpts alone leaves a variable-framerate stream that decodes as a
    slideshow; -r plus fps= forces a genuine constant 30fps output, which is
    what the evidence gate's frame-count check is designed to catch.
    """
    vf = (f"setpts=PTS/{speedup},fps={fps},"
          f"scale=1080:-2:flags=lanczos,pad=1080:2400:0:(oh-ih)/2:black")
    cmd = [
        "ffmpeg", "-y", "-v", "error", "-i", str(raw),
        "-vf", vf, "-an",
        "-c:v", "libx264", "-preset", "slow", "-crf", "20",
        "-pix_fmt", "yuv420p", "-profile:v", "high", "-level", "4.1",
        "-r", str(fps), "-vsync", "cfr",
        "-movflags", "+faststart", str(short),
    ]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    if r.returncode != 0:
        print(f"  ffmpeg cut failed: {r.stderr[-400:]}", flush=True)
        return False
    return short.exists() and short.stat().st_size > 0


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in JOURNEYS:
        print(f"usage: {sys.argv[0]} {'|'.join(JOURNEYS)}")
        return 2
    key = sys.argv[1]
    name, fn = JOURNEYS[key]

    OUT.mkdir(parents=True, exist_ok=True)
    work = WORK / key
    work.mkdir(parents=True, exist_ok=True)

    # Cold-start and sign in BEFORE the recorder starts: the Android boot
    # splash is a near-black screen that both wastes Short runtime and trips
    # the evidence gate's blank-frame check.
    print(f"== preparing {name} ==", flush=True)
    cold_start()

    print(f"== recording {name} ==", flush=True)
    rec = SegmentedRecorder(name, str(work),
                            size="720x1600", bitrate=6_000_000).start()
    try:
        facts = fn()
    finally:
        # speedup=1.0: this pass produces the archival full-length raw. The
        # Short is cut from it afterwards so a re-cut never needs a re-run.
        raw_tmp = rec.stop_and_finalize(fps=30, speedup=1.0)

    if not raw_tmp:
        print("FAIL: no video captured")
        return 1

    raw = OUT / f"{name}_raw.mp4"
    subprocess.run(["cp", raw_tmp, str(raw)], check=True)

    raw_dur = probe_duration(raw)
    speedup = max(1.0, min(12.0, raw_dur / TARGET_SHORT_SECONDS)) if raw_dur else 1.0
    short = OUT / f"{name}_short.mp4"
    print(f"  raw {raw_dur:.1f}s -> speedup {speedup:.2f}x", flush=True)
    if not cut_short(raw, short, speedup):
        return 1

    report = verify_video_evidence(
        str(short), frames=8, min_distinct=4, blank_mean=6.0,
        report_path=str(OUT / f"{name}_evidence.json"),
    )
    summary = {
        "journey": key,
        "raw": str(raw),
        "short": str(short),
        "raw_duration": round(raw_dur, 2),
        "speedup": round(speedup, 2),
        "facts": facts,
        "evidence": report,
    }
    (OUT / f"{name}_summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2), flush=True)
    print(f"EVIDENCE_GATE={report['verdict'].upper()}", flush=True)
    return 0 if report["verdict"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
