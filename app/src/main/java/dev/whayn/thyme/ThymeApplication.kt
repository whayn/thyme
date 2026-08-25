package dev.whayn.thyme

import android.app.Application
import dev.whayn.thyme.alert.AlarmScheduler
import dev.whayn.thyme.alert.AlertChannels

/**
 * The app's first Application class, added because the alert system needs work
 * done at process start with no Activity in sight.
 *
 * Deliberately thin: no DI container, no service locator. The existing
 * `.get(context)` singletons already cover what needs sharing.
 */
class ThymeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Channels must exist before anything can post, and a receiver firing
        // at 08:00 has no Activity to have created them.
        AlertChannels.ensure(this)
        // Cheap insurance. The alarm should already be armed from boot or from
        // the last write, but a process start is a free moment to be certain.
        AlarmScheduler.get(this).requestRearm("app-start")
    }
}
