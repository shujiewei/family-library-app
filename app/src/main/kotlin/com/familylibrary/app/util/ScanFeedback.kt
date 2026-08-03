package com.familylibrary.app.util

import android.view.HapticFeedbackConstants
import android.view.View

object ScanFeedback {
    fun onIsbnScanned(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun onSuccess(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun onError(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}
