package app.olauncher.listener

import android.app.admin.DeviceAdminReceiver

// Only needed so that the device admin can be activated to lock the screen on Android 8 and below
class DeviceAdmin : DeviceAdminReceiver()
