"""Android CUA test: TaroAiScanner FULL core journey, deliberately paced.

Guest sign-in -> Dashboard (dwell) -> Single Card Draw -> camera preview
(dwell, showing the live emulated camera feed animate) -> capture -> loading
(dwell) -> AI reading result (dwell, reading the text) -> Draw Another Card
(returns to dashboard).

This test is paced with deliberate multi-second waits at every stage so a
PARALLEL continuous `adb shell screenrecord` capture shows real, distinct
motion at every journey step -- not just a slideshow of tap-moments. Do NOT
rush; waiting on a screen to let the recording show it clearly is required
and correct behavior for this run.
"""
from a_test import TestCase, run_case

case = TestCase(
    name="tarot_scan_reading_full",
    package="com.aistudio.mystictarot.qxrptl",
    instruction=(
        "This is the Mystic Tarot app, freshly launched (cold start, fresh install, "
        "full free-reading quota available). Your task: complete the FULL core user "
        "journey end to end, at a deliberately unhurried pace so every stage is "
        "clearly visible on screen for several seconds -- a screen recorder is "
        "capturing this run and needs real dwell time on each screen, not instant taps. "
        "Steps: "
        "1. You should see an onboarding/auth screen titled 'MYSTIC TAROT'. Wait 2 "
        "seconds looking at it, then tap the 'Continue as Guest' button (outlined "
        "button below 'Sign in with Google'). "
        "2. You should land on the Dashboard screen. Wait 3 seconds. Then, using a "
        "swipe gesture, scroll down slightly to reveal the 'Scan Physical Card' "
        "section if it's not already visible, and wait 2 more seconds so the "
        "dashboard is clearly shown. "
        "3. Tap the 'Single Card' button (a button related to drawing a single tarot "
        "card, likely inside a 'Scan Physical Card' section near the bottom). If a "
        "'Mystic Premium' upsell popup or paywall screen appears instead of the "
        "camera, tap its close/X button or press back, then retry tapping 'Single "
        "Card' once -- do not give up, this app has a fresh full quota so it should "
        "eventually open the camera. "
        "4. Camera permission should already be granted, so you should land on a live "
        "camera viewfinder with a gold-bordered card outline and a gold button "
        "reading 'REVEAL COSMIC TRUTH' at the bottom. Wait 6 full seconds here, doing "
        "nothing but observing the live camera preview animate (the emulator's "
        "virtual camera shows a moving synthetic scene) -- this dwell is required so "
        "the recording clearly shows the live camera stage, not just a flash. "
        "5. After the 6 second wait, tap the 'REVEAL COSMIC TRUTH' capture button. "
        "6. A loading state should appear reading 'Invoking Gemini Oracle...'. This "
        "calls a real backend AI service and can legitimately take up to 60 seconds "
        "on a loaded system -- WAIT. Issue repeated 'wait 10 seconds' actions, one "
        "after another, for as long as needed, up to 60 total seconds, until the "
        "loading indicator is gone and a new screen has appeared. "
        "ABSOLUTE RULE, NO EXCEPTIONS: while this loading indicator is visible, do "
        "NOT press back, do NOT press home, do NOT tap anywhere on the screen, do "
        "NOT navigate away for ANY reason, even if it seems slow or stuck. Pressing "
        "back during this loading state is a known way to corrupt the app's "
        "navigation state and will incorrectly fail this test. If 60 seconds pass "
        "with no change, wait 10 more seconds at a time (up to 120 seconds total) "
        "before considering it stuck. Patience here is mandatory and correct. "
        "7. You should land on a Reading Result screen showing AI-generated tarot "
        "reading text (a card name plus an interpretive paragraph). Wait 5 seconds "
        "here so the recording clearly captures the reading result screen and its "
        "text content. "
        "8. Find and tap the 'Draw Another Card' button at the bottom of the Reading "
        "Result screen. This should return you to the Dashboard. "
        "9. Once back on the Dashboard, wait 2 more seconds, then report TEST_PASSED. "
        "Report TEST_FAILED only if the app crashes, shows an Android 'App has "
        "stopped' / ANR system dialog, or you cannot progress past a screen after "
        "many retries."
    ),
    successCriteria=[
        "The full journey completes: guest sign-in, dashboard, camera capture, "
        "AI reading result screen with content, and returning to dashboard via "
        "'Draw Another Card'",
        "The app did not crash at any point in the journey",
    ],
    failureCriteria=[
        "The app crashes or an 'App has stopped' / ANR system dialog appears",
        "The journey gets stuck on one screen with no progress after many retries",
    ],
    maxSteps=40,
)

if __name__ == "__main__":
    result = run_case(case, output_dir="/tmp/a-test-output/tarot_scan_reading_full")
    print(f"Verdict: {result['verdict']} -- {result.get('reason', '')}")
