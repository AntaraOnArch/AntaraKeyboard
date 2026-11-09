package com.example.antarakeyboard.ui.tutorial

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.example.antarakeyboard.R
import com.example.antarakeyboard.data.TutorialPrefs

/**
 * Setup guide on the main screen. While the screen is visible a [KeyboardStatusWatcher] always
 * watches the keyboard: not enabled → the Enable button is spotlighted, enabled but not the active
 * keyboard → the Choose button, both → no spotlight. It moves on as soon as the system reports the
 * change. While the tutorial is pending (first launch, or Help → Show the tutorial) a ready keyboard
 * opens the practice ([TutorialActivity]). Skip hides the spotlight while the user stays on the
 * screen (rotation included, until the status changes) and ends the tutorial; leaving the app and
 * coming back shows it again.
 */
class SetupGuide(private val activity: Activity, private val host: Host) {

    interface Host {
        val enableButton: View
        val chooseButton: View
        /** Brings the Enable / Choose buttons into view. */
        fun scrollToTop()
    }

    private companion object {
        const val STATE_PRACTICE = "setup_guide_practice"
        const val STATE_SKIPPED = "setup_guide_skipped"
    }

    private val watcher = KeyboardStatusWatcher(activity) { onStatus(it) }
    private var overlay: SpotlightOverlay? = null
    private var shownFor: View? = null

    /** The practice opens once the keyboard is ready. */
    private var practicePending = false
    /** Status the user skipped the spotlight for; a different one shows it again. */
    private var skipped: KeyboardStatusWatcher.Status? = null

    /** First launch, or the state before a recreate. */
    fun restore(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            practicePending = !TutorialPrefs.isDone(activity)
            return
        }
        practicePending = savedInstanceState.getBoolean(STATE_PRACTICE)
        skipped = savedInstanceState.getIntArray(STATE_SKIPPED)
            ?.let { KeyboardStatusWatcher.Status(it[0] == 1, it[1] == 1) }
    }

    fun save(outState: Bundle) {
        outState.putBoolean(STATE_PRACTICE, practicePending)
        skipped?.let { outState.putIntArray(STATE_SKIPPED, intArrayOf(if (it.enabled) 1 else 0, if (it.active) 1 else 0)) }
    }

    /** Help → Show the tutorial: setup spotlight if needed, then the practice. */
    fun start() {
        practicePending = true
        skipped = null
        host.scrollToTop()
        watcher.start()
    }

    /** Lifecycle: watch while the screen is visible; [KeyboardStatusWatcher.start] also re-checks at once. */
    fun onStart() = watcher.start()

    fun onStop() {
        watcher.stop()
        if (!activity.isChangingConfigurations) skipped = null
    }

    /** The input method picker gives the focus back without a settings notification on some devices. */
    fun refresh() = watcher.refresh()

    private fun onStatus(status: KeyboardStatusWatcher.Status) {
        if (status.enabled && status.active) {
            removeOverlay()
            skipped = null
            if (practicePending) {
                practicePending = false
                activity.startActivity(Intent(activity, TutorialActivity::class.java))
            }
            return
        }
        if (status == skipped) return
        skipped = null
        if (!status.enabled) {
            spotlight(status, host.enableButton, R.string.tutorial_enable_title,
                activity.getString(R.string.tutorial_enable_text, activity.getString(R.string.main_enable)))
        } else {
            spotlight(status, host.chooseButton, R.string.tutorial_choose_title,
                activity.getString(R.string.tutorial_choose_text, activity.getString(R.string.main_choose)))
        }
    }

    private fun spotlight(status: KeyboardStatusWatcher.Status, target: View, titleRes: Int, text: String) {
        if (shownFor == target) return
        removeOverlay()
        shownFor = target
        host.scrollToTop()
        overlay = SpotlightOverlay(activity, target, activity.getString(titleRes), text) {
            removeOverlay()
            skipped = status
            practicePending = false
            TutorialPrefs.setDone(activity)
        }.also {
            activity.findViewById<ViewGroup>(android.R.id.content).addView(
                it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun removeOverlay() {
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
        shownFor = null
    }
}
