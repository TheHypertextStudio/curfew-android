package studio.hypertext.curfew.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import studio.hypertext.curfew.checkin.CameraAvailabilityChecker

/** Checks whether this installation has both camera hardware and permission to use it. */
internal class AndroidCameraAvailabilityChecker(private val context: Context) : CameraAvailabilityChecker {
    override fun isCameraReady(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
}
