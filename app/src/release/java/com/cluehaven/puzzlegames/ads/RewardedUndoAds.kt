package com.cluehaven.puzzlegames.ads

import android.app.Activity
import com.cluehaven.puzzlegames.data.AgeGroup

/** Production ads stay disabled until real IDs and privacy flow are configured. */
class RewardedUndoAds(activity: Activity) {
    val isAvailable: Boolean = false
    fun cancel() = Unit

    fun show(
        ageGroup: AgeGroup,
        onReward: () -> Unit,
        onUnavailable: () -> Unit,
        onFinished: () -> Unit,
    ) {
        onUnavailable()
        onFinished()
    }
}
