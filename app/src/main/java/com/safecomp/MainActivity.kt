package com.safecomp

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.safecomp.ui.navigation.SafeCompNavHost
import com.safecomp.ui.theme.SafeCompTheme

/**
 * The app's only activity. Every screen is a Compose destination inside it, so the window
 * flags set here protect the whole app.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // SECURITY: FLAG_SECURE is set before super.onCreate() and setContent(), so no frame
        // is ever drawn without it. It blocks screenshots and screen recording/casting (the
        // window shows black), and blanks the thumbnail in the recent-apps screen.
        // It stays on for the app's whole life rather than per screen: toggling it during
        // navigation could leave a gap where a sensitive frame gets captured.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Belt and braces for Android 13+: explicitly opt out of the recents snapshot.
            setRecentsScreenshotEnabled(false)
        }
        super.onCreate(savedInstanceState)
        // Always-dark UI: light status/navigation bar icons on a transparent bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val container = (application as SafeCompApp).container
        setContent {
            SafeCompTheme {
                SafeCompNavHost(container = container)
            }
        }
    }
}
