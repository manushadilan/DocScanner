package com.example.docscanner

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Loads the native OpenCV library bundled in the AAR.
        if (!OpenCVLoader.initLocal()) Log.e("DocScanner", "OpenCV failed to load")
    }
}
