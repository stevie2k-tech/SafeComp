package com.safecomp

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.safecomp.focus.FocusCommand
import com.safecomp.ui.navigation.SafeCompNavHost
import com.safecomp.ui.theme.SafeCompTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The app's only activity. Every screen is a Compose destination inside it, so the window
 * flags set here protect the whole app.
 */
// FragmentActivity (a ComponentActivity) because Android's fingerprint/PIN prompt needs it.
class MainActivity : FragmentActivity() {

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
        runFocusCommands(container)
        setContent {
            SafeCompTheme {
                SafeCompNavHost(container = container)
            }
        }
    }

    /** Android's "Pin app?" dialog covers the whole screen, so a touch reaching SafeComp means
     *  it has closed. That's how Focus Mode tells "still reading it" from "No thanks". */
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as SafeCompApp).container.focusController.onUserInteraction()
    }

    /**
     * Focus Mode: only an activity can call startLockTask()/stopLockTask(), so the controller
     * sends commands here. While visible, we also re-check Android's Lock Task state every
     * second: that's how we notice the employee unpinned the app (there's no callback).
     */
    private fun runFocusCommands(container: AppContainer) {
        val focus = container.focusController
        // Android only accepts startLockTask() from a resumed activity, so commands are carried
        // out between onResume and onPause. A command sent before that simply waits.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                focus.pendingCommand.filterNotNull().collect { pending ->
                    // On an un-enrolled phone startLockTask() asks "Pin SafeComp?" and the
                    // user can say no. Either call can throw if the state just changed;
                    // the next sync() sees what really happened.
                    runCatching {
                        when (pending.command) {
                            FocusCommand.START -> startLockTask()
                            FocusCommand.STOP -> stopLockTask()
                        }
                    }
                    focus.commandHandled(pending.id)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    focus.sync()
                    delay(1_000)
                }
            }
        }
    }
}
