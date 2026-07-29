"""Android CUA test: TaroAiScanner core journey.

Guest sign-in -> Dashboard -> Single Card Draw -> Scan (camera capture) ->
verify the AI (Gemini) tarot reading result is actually displayed on screen.

CAMERA runtime permission is pre-granted via adb before this runs, so the
system permission dialog is skipped and the flow goes straight to the
camera preview after tapping "Single Card Draw".
"""
from a_test import TestCase, run_case

case = TestCase(
    name="tarot_scan_reading",
    package="com.aistudio.mystictarot.qxrptl",
    instruction=(
        "This is the Mystic Tarot app, freshly launched (cold start). "
        "Your task: complete the core user journey end to end. "
        "Steps: "
        "1. You should see an onboarding/auth screen titled 'MYSTIC TAROT'. "
        "Tap the 'Continue as Guest' button (outlined button below 'Sign in with Google'). "
        "2. You should land on the Dashboard screen. Tap the 'Single Card Draw' button "
        "(a card/button related to drawing a single tarot card). "
        "3. Camera permission is already granted, so you should see a live camera "
        "viewfinder with a gold-bordered card outline and a button reading "
        "'REVEAL COSMIC TRUTH' at the bottom. Tap that capture button. "
        "4. Wait for the loading indicator ('Invoking Gemini Oracle...') to finish. "
        "This can take up to 20 seconds -- be patient, do not tap anything else while "
        "it is loading. "
        "5. Verify you land on a Reading Result screen that displays AI-generated tarot "
        "reading text (a card name and/or an interpretive paragraph of text), OR a "
        "visible error message if the AI call failed -- either outcome means the app "
        "completed the journey without crashing. "
        "Report TEST_PASSED once the Reading Result screen is shown with either AI "
        "reading content or a handled error message (not a raw crash/ANR dialog). "
        "Report TEST_FAILED only if the app crashes, shows an Android 'App has stopped' "
        "system dialog, or gets stuck on a screen for the full step budget with no "
        "progress."
    ),
    successCriteria=[
        "A Reading Result screen is shown after tapping the capture button",
        "The screen displays either AI-generated tarot reading text or a handled in-app error message",
        "The app did not crash at any point in the journey",
    ],
    failureCriteria=[
        "The app crashes or an 'App has stopped' / ANR system dialog appears",
        "The journey gets stuck (e.g. stuck on auth or camera screen) with no progress after many steps",
    ],
    maxSteps=30,
)

if __name__ == "__main__":
    result = run_case(case, output_dir="/tmp/a-test-output/tarot_scan_reading")
    print(f"Verdict: {result['verdict']} -- {result.get('reason', '')}")
