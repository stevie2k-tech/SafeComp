package com.safecomp.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAdviceTest {

    @Test fun `brands with their own background killer get an extra step`() {
        for (brand in listOf("Xiaomi", "Redmi", "POCO", "samsung", "HUAWEI", "honor", "OPPO", "realme", "OnePlus", "vivo")) {
            assertNotNull(brand, BackgroundAdvice.forManufacturer(brand))
        }
    }

    @Test fun `matching ignores case and spaces`() {
        assertEquals(BackgroundAdvice.forManufacturer("xiaomi"), BackgroundAdvice.forManufacturer("  XIAOMI "))
    }

    @Test fun `other brands need nothing extra`() {
        assertNull(BackgroundAdvice.forManufacturer("Google"))
        assertNull(BackgroundAdvice.forManufacturer("unknown"))
        assertNull(BackgroundAdvice.forManufacturer(""))
    }
}

class PermissionStepsTest {

    private val all = PermissionStatus(preciseLocation = true, backgroundLocation = true, notifications = true)

    @Test fun `the battery step comes last`() {
        assertEquals(PermissionStep.BATTERY, PermissionStep.entries.last())
        assertEquals(PermissionStep.PRECISE_LOCATION, PermissionStep.entries.first())
    }

    @Test fun `the battery step is needed only with background location and battery saving on`() {
        assertFalse(all.copy(batteryUnrestricted = false).satisfies(PermissionStep.BATTERY))
        assertTrue(all.copy(batteryUnrestricted = true).satisfies(PermissionStep.BATTERY))
    }

    @Test fun `without background location the battery step is skipped`() {
        // Nothing runs in the background to protect, so don't ask.
        val foregroundOnly = all.copy(backgroundLocation = false, batteryUnrestricted = false)
        assertTrue(foregroundOnly.satisfies(PermissionStep.BATTERY))
    }

    @Test fun `the screen lock step comes after notifications and before battery`() {
        val steps = PermissionStep.entries
        assertEquals(steps.indexOf(PermissionStep.NOTIFICATIONS) + 1, steps.indexOf(PermissionStep.SCREEN_LOCK))
        assertEquals(steps.indexOf(PermissionStep.SCREEN_LOCK) + 1, steps.indexOf(PermissionStep.BATTERY))
    }

    @Test fun `the screen lock step is needed until a screen lock is set`() {
        assertFalse(all.copy(screenLock = false).satisfies(PermissionStep.SCREEN_LOCK))
        assertTrue(all.satisfies(PermissionStep.SCREEN_LOCK))
    }

    @Test fun `battery saving doesn't count as a missing permission`() {
        assertTrue(all.copy(batteryUnrestricted = false).allGranted)
    }
}
