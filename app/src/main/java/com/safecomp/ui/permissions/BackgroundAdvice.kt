package com.safecomp.ui.permissions

/**
 * Some phone brands add their own background-app killer on top of Android's battery
 * optimisation. For those, SafeComp shows one extra, plain-text step. Text only: the brands'
 * hidden settings screens change between versions, so we don't try to open them directly.
 */
object BackgroundAdvice {

    /** [manufacturer] is `Build.MANUFACTURER`. Null when the brand needs nothing extra. */
    fun forManufacturer(manufacturer: String): String? = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" ->
            "On Xiaomi phones also open Settings › Apps › SafeComp: turn on Autostart and set " +
                "Battery saver to \"No restrictions\"."
        "samsung" ->
            "On Samsung phones also check Settings › Battery › Background usage limits, and make " +
                "sure SafeComp isn't in \"Sleeping apps\" or \"Deep sleeping apps\"."
        "huawei", "honor" ->
            "On Huawei and Honor phones also open Settings › Battery › App launch, find SafeComp " +
                "and choose \"Manage manually\" with all switches on."
        "oppo", "realme", "oneplus", "vivo", "iqoo" ->
            "On this phone also open SafeComp's app info › Battery and allow background activity " +
                "and auto-launch."
        else -> null
    }
}
