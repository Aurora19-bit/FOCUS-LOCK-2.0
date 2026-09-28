package com.vidhya.focuslock

import android.app.admin.DeviceAdminReceiver

/**
 * Enabling this as a Device Admin means the user has to explicitly deactivate
 * admin rights (Settings > Security > Device admin apps) before they can
 * uninstall FocusLock. Since Settings itself is blocked while a session is
 * active (see FocusAccessibilityService), this can't be done mid-session.
 */
class SessionAdminReceiver : DeviceAdminReceiver()
