/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.applicationContext
import com.android.systemui.display.domain.interactor.displayStateInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import com.android.systemui.statusbar.chips.bluetooth.ui.viewmodel.bluetoothChipViewModel
import com.android.systemui.statusbar.chips.call.ui.viewmodel.callChipViewModel
import com.android.systemui.statusbar.chips.casttootherdevice.ui.viewmodel.castToOtherDeviceChipViewModel
import com.android.systemui.statusbar.chips.download.ui.viewmodel.downloadChipViewModel
import com.android.systemui.statusbar.chips.hotspot.ui.viewmodel.hotspotChipViewModel
import com.android.systemui.statusbar.chips.media.ui.viewmodel.mediaChipViewModel
import com.android.systemui.statusbar.chips.navigation.ui.viewmodel.navigationChipViewModel
import com.android.systemui.statusbar.chips.notification.ui.viewmodel.notifChipsViewModel
import com.android.systemui.statusbar.chips.privacy.ui.viewmodel.privacyChipViewModel
import com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel.screenRecordChipViewModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.shareToAppChipViewModel
import com.android.systemui.statusbar.chips.statusBarChipsLogger
import com.android.systemui.statusbar.chips.timer.ui.viewmodel.timerChipViewModel

val Kosmos.ongoingActivityChipsViewModel: OngoingActivityChipsViewModel by
    Kosmos.Fixture {
        OngoingActivityChipsViewModel(
            context = applicationContext,
            scope = testScope.backgroundScope,
            privacyChipViewModel = privacyChipViewModel,
            screenRecordChipViewModel = screenRecordChipViewModel,
            shareToAppChipViewModel = shareToAppChipViewModel,
            castToOtherDeviceChipViewModel = castToOtherDeviceChipViewModel,
            callChipViewModel = callChipViewModel,
            downloadChipViewModel = downloadChipViewModel,
            mediaChipViewModel = mediaChipViewModel,
            navigationChipViewModel = navigationChipViewModel,
            timerChipViewModel = timerChipViewModel,
            hotspotChipViewModel = hotspotChipViewModel,
            bluetoothChipViewModel = bluetoothChipViewModel,
            notifChipsViewModel = notifChipsViewModel,
            displayStateInteractor = displayStateInteractor,
            screenCaptureRecordFeaturesInteractor = screenCaptureRecordFeaturesInteractor,
            chipsRefiners = chipsRefinerSet,
            logger = statusBarChipsLogger,
        )
    }

val Kosmos.chipsRefinerSet: MutableSet<OngoingActivityChipsRefiner> by
    Kosmos.Fixture { mutableSetOf() }
