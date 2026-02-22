package de.haberland.meicaller

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MeiCallerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Crashlytics initialisieren
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        
        // Optional: Custom Keys für besseres Debugging
        FirebaseCrashlytics.getInstance().setCustomKey("app_version", BuildConfig.VERSION_NAME)
    }
}
