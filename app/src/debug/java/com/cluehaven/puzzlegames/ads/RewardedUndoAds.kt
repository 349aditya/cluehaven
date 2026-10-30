package com.cluehaven.puzzlegames.ads

import android.app.Activity
import com.cluehaven.puzzlegames.data.AgeGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** Uses Google's sample rewarded unit in debug builds. A reward is granted only by its callback. */
class RewardedUndoAds(private val activity: Activity) {
    private var requestId = 0
    val isAvailable: Boolean = true

    fun cancel() { requestId++ }

    fun show(
        ageGroup: AgeGroup,
        onReward: () -> Unit,
        onUnavailable: () -> Unit,
        onFinished: () -> Unit,
    ) {
        val thisRequest = ++requestId
        try {
            val treatment = when (ageGroup) {
                AgeGroup.UNDER_13 -> AgeRestrictedTreatment.CHILD
                AgeGroup.AGE_13_TO_17 -> AgeRestrictedTreatment.TEEN
                AgeGroup.AGE_18_PLUS -> AgeRestrictedTreatment.UNSPECIFIED
            }
            MobileAds.setRequestConfiguration(
                MobileAds.getRequestConfiguration().toBuilder()
                    .setAgeRestrictedTreatment(treatment)
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                    .build(),
            )
            MobileAds.initialize(activity) {
                activity.runOnUiThread {
                    if (thisRequest != requestId) return@runOnUiThread
                    RewardedAd.load(
                        activity,
                        TEST_REWARDED_UNIT,
                        AdRequest.Builder().build(),
                        object : RewardedAdLoadCallback() {
                            override fun onAdLoaded(ad: RewardedAd) {
                                if (thisRequest != requestId) return
                                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                    override fun onAdDismissedFullScreenContent() {
                                        if (thisRequest == requestId) onFinished()
                                    }

                                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                        if (thisRequest == requestId) {
                                            onUnavailable()
                                            onFinished()
                                        }
                                    }
                                }
                                ad.show(activity) {
                                    if (thisRequest == requestId) onReward()
                                }
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                if (thisRequest == requestId) {
                                    onUnavailable()
                                    onFinished()
                                }
                            }
                        },
                    )
                }
            }
        } catch (_: Exception) {
            if (thisRequest == requestId) {
                onUnavailable()
                onFinished()
            }
        }
    }

    private companion object {
        const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"
    }
}
