package com.markerdetection.markerFunctions

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.ViewManager

class MarkerDetectorPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext) =
        listOf(MarkerDetectorModule(reactContext))

    override fun createViewManagers(reactContext: ReactApplicationContext) =
        emptyList<ViewManager<*, *>>()
}