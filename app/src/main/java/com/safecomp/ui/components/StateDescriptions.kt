package com.safecomp.ui.components

import com.safecomp.security.StateReason

/** User-facing explanation of why the vault is in its current state. */
fun StateReason.description(): String = when (this) {
    StateReason.CHECKING -> "Checking your location…"
    StateReason.NOT_CONFIGURED -> "No office location is set yet. Set it up on the home screen."
    StateReason.INSIDE_CONFIRMED -> "You're in the office."
    StateReason.CONFIRMING_INSIDE -> "You seem to be in the office. Confirming before unlocking…"
    StateReason.OUTSIDE_FIX, StateReason.OUTSIDE_GEOFENCE -> "You're outside the office area."
    StateReason.PERMISSION_MISSING -> "Location permission is off, so SafeComp can't confirm where you are."
    StateReason.LOCATION_OFF -> "Location services are off, so SafeComp can't confirm where you are."
    StateReason.MOCK_LOCATION -> "A fake (mock) location was detected. SafeComp doesn't trust it."
    StateReason.POOR_ACCURACY -> "The location signal is too weak to be sure where you are."
    StateReason.NO_FIX -> "No location fix is available right now."
}
