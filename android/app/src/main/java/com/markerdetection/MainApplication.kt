package com.markerdetection

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import org.opencv.android.OpenCVLoader
import android.util.Log
import com.markerdetection.markerFunctions.MarkerDetectorPackage

class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Packages that cannot be autolinked yet can be added manually here, for example:
          // add(MyReactNativePackage())
          add(MarkerDetectorPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)

    if (!OpenCVLoader.initDebug()) {
        Log.e("OpenCV", "Initialization failed")
    } else {
        Log.d("OpenCV", "OpenCV initialized")
    }
  }
}
