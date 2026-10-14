package com.safecomp.ui.focus

import androidx.compose.ui.graphics.Color
import com.safecomp.focus.FocusMode
import com.safecomp.focus.FocusStatus
import com.safecomp.ui.theme.Sc

/**
 * On-screen narration of what Focus Mode is doing and why. Spelled out on purpose: the
 * employee should always know whether their phone is restricted and how to get out, and it
 * makes a demo on the emulator easy to follow.
 */
fun FocusMode.title(): String = when (this) {
    FocusMode.PINNING -> "Screen pinning · SafeComp only"
    FocusMode.TEST_OWNER -> "Lock Task · your work apps"
    FocusMode.MANAGED -> "Lock Task · organisation's apps"
}

fun FocusMode.explanation(): String = when (this) {
    FocusMode.PINNING ->
        "This phone isn't managed by a company, so Android only lets SafeComp pin itself: " +
            "while Focus Mode is on, SafeComp is the only app you can use. Android asks you " +
            "first, and you can unpin at any time. Pinning covers one app, so SafeComp doesn't " +
            "open other apps while pinned: your work-app list below is saved for an enrolled phone."
    FocusMode.TEST_OWNER ->
        "Debug build: SafeComp was made a TEST device owner with adb, so Android enforces " +
            "your work-app list. Other apps can't open while Focus Mode is on. You can still " +
            "stop it from SafeComp or from the notification at any time."
    FocusMode.MANAGED ->
        "Your organisation's device management app allowed SafeComp to use Lock Task. The " +
            "apps that can open are set by your IT admin, so the list below isn't used."
}

fun FocusStatus.pill(): Pair<String, Color> = when {
    active -> "ON" to Sc.Violet
    starting -> "STARTING…" to Sc.Unknown
    !inside -> "OFF · NOT AT OFFICE" to Sc.TextTertiary
    startFailed -> "NOT STARTED" to Sc.Unknown
    stoppedThisVisit -> "OFF · YOUR CHOICE" to Sc.TextSecondary
    !autoStart -> "AUTO-START OFF" to Sc.TextSecondary
    else -> "READY" to Sc.Inside
}

fun FocusStatus.narration(workAppCount: Int): String = when {
    active -> when (mode) {
        FocusMode.PINNING -> "SafeComp is pinned, so other apps are blocked until you leave the " +
            "office. To exit, tap Stop, or use Android's unpin gesture (swipe up and hold, or " +
            "hold Back and Overview)."
        FocusMode.TEST_OWNER -> "Lock Task is on: only SafeComp and your $workAppCount work " +
            "app${if (workAppCount == 1) "" else "s"} can open. Leaving the office ends it automatically."
        FocusMode.MANAGED -> "Lock Task is on with your organisation's app list. Leaving the " +
            "office ends it automatically."
    }
    starting -> if (mode == FocusMode.PINNING) {
        "Android is asking whether to pin SafeComp. Accept to start Focus Mode, or choose " +
            "No thanks to say no. Take your time: it waits for you."
    } else {
        "Starting Lock Task…"
    }
    !inside -> "Focus Mode can only run while you're confirmed inside the office, and leaving " +
        "always ends it."
    startFailed -> "Focus Mode didn't start: the request to pin SafeComp wasn't accepted. " +
        "Tap Start Focus Mode to try again."
    stoppedThisVisit -> "You stopped or declined Focus Mode. It won't start again by itself " +
        "until you next arrive at the office."
    !autoStart -> "Automatic start is off. You can still start Focus Mode here while you're at the office."
    else -> "Focus Mode starts by itself when you're at the office and SafeComp is open."
}
