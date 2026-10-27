package com.android.systemui.statusbar.phone.island.compose

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Smartphone
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.android.systemui.res.R
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Deprecated("Use DynamicIsland instead", ReplaceWith("DynamicIsland(mediaState, batteryState, config, isExpanded, onToggleExpand, onPlayPause, onNext, onPrevious, onOpenMusicApp, onSeek, onToggleTorch, onStopRecording, onEndCall, onCancelDownload, modifier)"))
@Composable
fun RealDynamicIsland(
    mediaState: DynamicIslandMediaState,
    batteryState: DynamicIslandBatteryState,
    config: IslandConfig,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenMusicApp: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    DynamicIsland(
        mediaState = mediaState,
        batteryState = batteryState,
        config = config,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onOpenMusicApp = onOpenMusicApp,
        onSeek = onSeek,
        onToggleTorch = onToggleTorch,
        onStopRecording = onStopRecording,
        onEndCall = onEndCall,
        onCancelDownload = onCancelDownload,
        modifier = modifier
    )
}
@Composable
fun DynamicIsland(
    mediaState: DynamicIslandMediaState,
    batteryState: DynamicIslandBatteryState,
    config: IslandConfig,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenMusicApp: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onToggleHotspot: () -> Unit = {},
    onDisconnectCast: () -> Unit = {},
    onCollapseCompleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val targetLocale = remember(config.languageTag) {
        if (config.languageTag.isBlank()) {
            java.util.Locale.getDefault()
        } else {
            java.util.Locale.forLanguageTag(config.languageTag)
        }
    }
    val currentContext = androidx.compose.ui.platform.LocalContext.current
    val localizedContext = remember(targetLocale, currentContext) {
        val overrideConfig = android.content.res.Configuration(currentContext.resources.configuration)
        overrideConfig.setLocale(targetLocale)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            overrideConfig.setLocales(android.os.LocaleList(targetLocale))
        }
        currentContext.createConfigurationContext(overrideConfig)
    }

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        androidx.compose.ui.platform.LocalConfiguration provides localizedContext.resources.configuration
    ) {
        DynamicIslandInternal(
            mediaState = mediaState,
            batteryState = batteryState,
            config = config,
            isExpanded = isExpanded,
            onToggleExpand = onToggleExpand,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onOpenMusicApp = onOpenMusicApp,
            onSeek = onSeek,
            onToggleTorch = onToggleTorch,
            onStopRecording = onStopRecording,
            onEndCall = onEndCall,
            onCancelDownload = onCancelDownload,
            onToggleHotspot = onToggleHotspot,
            onDisconnectCast = onDisconnectCast,
            onCollapseCompleted = onCollapseCompleted,
            modifier = modifier
        )
    }
}

@Composable
private fun DynamicIslandInternal(
    mediaState: DynamicIslandMediaState,
    batteryState: DynamicIslandBatteryState,
    config: IslandConfig,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpenMusicApp: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onToggleHotspot: () -> Unit = {},
    onDisconnectCast: () -> Unit = {},
    onCollapseCompleted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val banner by DynamicIslandManager.transientBanner.collectAsState()
    val torchState by DynamicIslandManager.torchState.collectAsState()
    val screenRecordState by DynamicIslandManager.screenRecordState.collectAsState()
    val callState by DynamicIslandManager.callState.collectAsState()
    val downloadState by DynamicIslandManager.downloadState.collectAsState()
    val hotspotState by DynamicIslandManager.hotspotState.collectAsState()
    val navigationState by DynamicIslandManager.navigationState.collectAsState()
    val timerState by DynamicIslandManager.timerState.collectAsState()
    val castState by DynamicIslandManager.castState.collectAsState()
    val privacyState by DynamicIslandManager.privacyState.collectAsState()
    val isClipboardCardActive by DynamicIslandManager.isClipboardCardActive.collectAsState()

    val isShowingBanner = banner.type != TransientBannerType.NONE
    val isMediaActive = mediaState.hasActiveMedia
    val hasContent = isClipboardCardActive ||
            isMediaActive ||
            isShowingBanner ||
            torchState.isOn ||
            screenRecordState.isRecording ||
            callState.isActive ||
            navigationState.isActive ||
            timerState.isActive ||
            castState.isCasting ||
            privacyState.hasAnyPrivacyActive ||
            downloadState.isDownloading ||
            downloadState.isCompleted ||
            downloadState.isFailed ||
            downloadState.isCancelled ||
            hotspotState.isEnabled

    val appearanceAnim = remember { Animatable(0f) }
    LaunchedEffect(hasContent) {
        if (hasContent) {
            appearanceAnim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = config.springDamping.coerceIn(0.35f, 2.0f),
                    stiffness = config.springStiffness
                )
            )
        } else {
            appearanceAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 320f
                )
            )
        }
    }
    val appearanceProgress = appearanceAnim.value

    if (appearanceProgress <= 0.005f && !hasContent) {
        return
    }

    val activeArt = mediaState.albumArt ?: mediaState.appIcon ?: DynamicIslandManager.getAppIcon(mediaState.packageName)
    val palette = remember(activeArt) {
        ColorExtractor.extractPalette(activeArt)
    }
    val accentColor = palette.primary

    val interactionSource = remember { MutableInteractionSource() }

    var currentPositionMs by remember {
        mutableLongStateOf(mediaState.positionMs)
    }

    var basePositionMs by remember { mutableLongStateOf(mediaState.positionMs) }
    var baseTimestampMs by remember { mutableLongStateOf(if (mediaState.lastUpdateTimeMs > 0) mediaState.lastUpdateTimeMs else SystemClock.elapsedRealtime()) }
    var isUserSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(mediaState.positionMs, mediaState.lastUpdateTimeMs) {
        if (!isUserSeeking) {
            basePositionMs = mediaState.positionMs
            baseTimestampMs = if (mediaState.lastUpdateTimeMs > 0) mediaState.lastUpdateTimeMs else SystemClock.elapsedRealtime()
            currentPositionMs = mediaState.positionMs
        }
    }

    LaunchedEffect(mediaState.title, mediaState.artist, mediaState.isPlaying, mediaState.durationMs) {
        if (mediaState.isPlaying && mediaState.durationMs > 0) {
            while (isActive) {
                if (!isUserSeeking) {
                    val speed = if (mediaState.playbackSpeed > 0f) mediaState.playbackSpeed else 1f
                    val elapsed = ((SystemClock.elapsedRealtime() - baseTimestampMs) * speed).toLong()
                    currentPositionMs = (basePositionMs + elapsed).coerceIn(0L, mediaState.durationMs)
                }
                delay(60)
            }
        } else {
            if (!isUserSeeking) {
                currentPositionMs = mediaState.positionMs
            }
        }
    }

    val animProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = config.springDamping.coerceIn(0.35f, 2.0f),
            stiffness = config.springStiffness
        ),
        finishedListener = { finalVal ->
            if (finalVal == 0f) {
                onCollapseCompleted()
            }
        },
        label = "islandMorphProgress"
    )

    val notchSizeDp = 22.dp

    val notchCenterYDp = 22.5f + config.yOffsetDp

    val currentActivityType = when {
        isShowingBanner -> IslandActivityType.TRANSIENT_BANNER
        callState.isActive -> IslandActivityType.CALL
        screenRecordState.isRecording -> IslandActivityType.SCREEN_RECORD
        navigationState.isActive -> IslandActivityType.NAVIGATION
        timerState.isActive -> IslandActivityType.TIMER
        torchState.isOn -> IslandActivityType.TORCH
        hotspotState.isEnabled -> IslandActivityType.HOTSPOT
        downloadState.isDownloading || downloadState.isCompleted || downloadState.isFailed || downloadState.isCancelled -> IslandActivityType.DOWNLOAD
        castState.isCasting -> IslandActivityType.CAST
        privacyState.hasAnyPrivacyActive -> IslandActivityType.PRIVACY
        isClipboardCardActive -> IslandActivityType.TRANSIENT_BANNER
        isMediaActive -> IslandActivityType.MEDIA
        else -> IslandActivityType.NONE
    }

    var lastActiveActivityType by remember { mutableStateOf(IslandActivityType.NONE) }
    var lastPillWidthDp by remember { mutableFloatStateOf(config.pillWidthDp) }

    val rawTargetPillWidth = when {
        isShowingBanner -> banner.targetWidthDp
        callState.isActive -> 185f
        navigationState.isActive -> 220f
        timerState.isActive -> 205f
        castState.isCasting -> 215f
        privacyState.hasAnyPrivacyActive -> 210f
        screenRecordState.isRecording -> 180f
        torchState.isOn -> 175f
        downloadState.isCompleted -> 180f
        downloadState.isCancelled -> 180f
        downloadState.isFailed -> 180f
        downloadState.isDownloading -> {
            if (downloadState.speedText.isNotBlank()) 195f else 180f
        }
        hotspotState.isEnabled -> if (hotspotState.showIntroBanner) 310f else 180f
        isMediaActive -> config.pillWidthDp
        else -> lastPillWidthDp
    }

    LaunchedEffect(hasContent, currentActivityType, rawTargetPillWidth) {
        if (hasContent && currentActivityType != IslandActivityType.NONE) {
            lastActiveActivityType = currentActivityType
            lastPillWidthDp = rawTargetPillWidth
        }
    }

    val animatedPillWidthDp by animateDpAsState(
        targetValue = rawTargetPillWidth.dp,
        animationSpec = spring(
            dampingRatio = config.springDamping.coerceIn(0.35f, 2.0f),
            stiffness = config.springStiffness
        ),
        label = "pillSidewaysMorph"
    )

    val bounceAnimProgress = animProgress.coerceAtLeast(0f)
    val safeProgress = appearanceProgress.coerceIn(0f, 1f)

    val circleSizeDp = config.pillHeightDp.dp
    val safePillWidthDp = animatedPillWidthDp.coerceAtLeast(circleSizeDp)

    val targetOpenWidth = lerp(safePillWidthDp, 362.dp, bounceAnimProgress).coerceAtLeast(circleSizeDp)
    val isClipboard = isClipboardCardActive || banner.type == TransientBannerType.CLIPBOARD
    val targetExpandedHeight = DynamicIslandManager.getExpandedCardHeightDp(
        currentActivity = currentActivityType,
        bannerType = banner.type,
        isClipboardActive = isClipboard
    ).dp
    val targetOpenHeight = lerp(circleSizeDp, targetExpandedHeight, bounceAnimProgress).coerceAtLeast(circleSizeDp)
    val closedTopYDp = (notchCenterYDp - (config.pillHeightDp / 2f)).coerceAtLeast(0f)
    val expandedTopYDp = maxOf(closedTopYDp, notchCenterYDp + (config.pillHeightDp / 2f) + 6f)
    val openTopY = lerp(closedTopYDp.dp, expandedTopYDp.dp, bounceAnimProgress.coerceIn(0f, 1f)).coerceAtLeast(0.dp)

    val currentWidth: Dp
    val currentHeight: Dp
    val currentY: Dp

    if (safeProgress >= 0.25f) {
        val f1 = ((safeProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
        currentWidth = lerp(circleSizeDp, targetOpenWidth, f1).coerceAtLeast(circleSizeDp)
        currentHeight = lerp(circleSizeDp, targetOpenHeight, f1).coerceAtLeast(circleSizeDp)
        currentY = lerp(closedTopYDp.dp, openTopY, f1).coerceAtLeast(0.dp)
    } else {
        val f2 = (safeProgress / 0.25f).coerceIn(0f, 1f)
        val d = lerp(0.dp, circleSizeDp, f2).coerceAtLeast(0.dp)
        currentWidth = d
        currentHeight = d
        currentY = (notchCenterYDp - (d.value / 2f)).coerceAtLeast(0f).dp
    }

    val cornerFraction = (bounceAnimProgress * safeProgress).coerceIn(0f, 1f)
    val currentCornerRadius = lerp((currentHeight / 2).coerceAtLeast(0.dp), 32.dp, cornerFraction).coerceAtLeast(0.dp)

    val pillContentAlpha = if (!hasContent) 0f else (((safeProgress - 0.28f) / 0.72f).coerceIn(0f, 1f)) *
            ((1f - (bounceAnimProgress / 0.32f)).coerceIn(0f, 1f))
    val expandedAlpha = if (!hasContent) 0f else (((bounceAnimProgress - 0.38f) / 0.62f).coerceIn(0f, 1f)) * safeProgress

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Default)
    ) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        Surface(
            modifier = Modifier
                .offset(x = config.xOffsetDp.dp)
                .padding(top = currentY.coerceAtLeast(0.dp))
                .width(currentWidth.coerceAtLeast(0.dp))
                .height(currentHeight.coerceAtLeast(0.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isExpanded) {
                            if (!isClipboardCardActive) {
                                onToggleExpand()
                            }
                        } else if (isShowingBanner) {
                            when (banner.type) {
                                TransientBannerType.CLIPBOARD -> {
                                    DynamicIslandManager.openClipboardCard()
                                    onToggleExpand()
                                }
                                else -> {
                                    DynamicIslandManager.dismissTransientBanner()
                                }
                            }
                        } else if (hasContent) {
                            onToggleExpand()
                        }
                    }
                ),
            shape = RoundedCornerShape(currentCornerRadius.coerceAtLeast(0.dp)),
            color = PixelTokens.IslandBlack,
            shadowElevation = lerp(3.dp, 14.dp, bounceAnimProgress.coerceIn(0f, 1f)).coerceAtLeast(0.dp),
            border = BorderStroke(
                width = 0.85.dp,
                color = Color.White.copy(alpha = (0.25f - (0.05f * bounceAnimProgress.coerceIn(0f, 1f))).coerceIn(0f, 1f))
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                val isShowingMediaExpanded = hasContent && animProgress > 0.05f &&
                        mediaState.hasActiveMedia &&
                        !isClipboardCardActive &&
                        !isShowingBanner &&
                        (currentActivityType == IslandActivityType.MEDIA || (isMediaActive && !callState.isActive && !navigationState.isActive && !timerState.isActive && !castState.isCasting && !privacyState.hasAnyPrivacyActive && !torchState.isOn && !screenRecordState.isRecording && !downloadState.isDownloading && !downloadState.isCompleted && !downloadState.isFailed && !downloadState.isCancelled && !hotspotState.isEnabled)) &&
                        !callState.isActive &&
                        !navigationState.isActive &&
                        !timerState.isActive &&
                        !castState.isCasting &&
                        !privacyState.hasAnyPrivacyActive &&
                        !torchState.isOn &&
                        !screenRecordState.isRecording &&
                        !downloadState.isDownloading &&
                        !downloadState.isCompleted &&
                        !downloadState.isFailed &&
                        !downloadState.isCancelled &&
                        !hotspotState.isEnabled

                if (isShowingMediaExpanded) {
                    FireworksBackground(
                        palette = palette,
                        isPlaying = mediaState.isPlaying,
                        intensity = config.fireworksIntensity,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(currentCornerRadius.coerceAtLeast(0.dp)))
                            .alpha(expandedAlpha)
                    )
                }

                if (pillContentAlpha > 0f && hasContent) {
                    val activeActivity = if (currentActivityType != IslandActivityType.NONE) currentActivityType else IslandActivityType.NONE
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(pillContentAlpha),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isShowingBanner || activeActivity == IslandActivityType.TRANSIENT_BANNER) {
                            val bannerTitle = if (banner.titleResId != 0) stringResource(banner.titleResId) else banner.title
                            when (banner.type) {
                                TransientBannerType.CHARGING -> {
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                             Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                IslandIcon(
                                                    type = if (banner.subtitle.contains("90") || banner.subtitle.contains("67")) IslandIconType.CHARGING_SUPER else IslandIconType.CHARGING,
                                                    contentDescription = stringResource(R.string.charging_label),
                                                    tint = PixelTokens.Green,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Text(
                                                    text = banner.subtitle,
                                                    color = PixelTokens.Green,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = PixelTokens.Green,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.BATTERY_LOW -> {
                                    val alertColor = Color(0xFFFF3B30)
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_power_low),
                                                    contentDescription = null,
                                                    tint = alertColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = banner.subtitle,
                                                    color = alertColor,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = alertColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.SILENT_MODE, TransientBannerType.DND -> {
                                    val bannerIconType = when (banner.iconName) {
                                        "silent" -> IslandIconType.SILENT
                                        "vibrate" -> IslandIconType.VIBRATE
                                        "sound" -> IslandIconType.SOUND
                                        "dnd_on" -> IslandIconType.DND
                                        else -> IslandIconType.SOUND
                                    }
                                            val fontSize = if (bannerTitle.length > 15) 10.5.sp else 11.5.sp
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            IslandIcon(
                                                type = bannerIconType,
                                                contentDescription = bannerTitle,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = Color(banner.tintColor),
                                                fontSize = fontSize,
                                                letterSpacing = (-0.2).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.CLIPBOARD -> {
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IslandIcon(
                                                    type = IslandIconType.CLIPBOARD,
                                                    contentDescription = stringResource(R.string.clipboard),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.copied),
                                                    color = Color(banner.tintColor),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        rightContent = {
                                            Text(
                                                text = banner.subtitle,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.HEADPHONES -> {
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IslandIcon(
                                                    type = IslandIconType.HEADPHONES,
                                                    contentDescription = bannerTitle,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Text(
                                                    text = bannerTitle,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        rightContent = {
                                            Text(
                                                text = if (banner.subtitleResId != 0) stringResource(banner.subtitleResId) else banner.subtitle,
                                                color = Color(banner.tintColor),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.HOTSPOT -> {
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            IslandIcon(
                                                type = IslandIconType.HOTSPOT,
                                                contentDescription = bannerTitle,
                                                tint = Color(banner.tintColor),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = Color(banner.tintColor),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                TransientBannerType.TORCH -> {
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            IslandIcon(
                                                type = IslandIconType.TORCH,
                                                contentDescription = bannerTitle,
                                                tint = Color(banner.tintColor),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = Color(banner.tintColor),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                                else -> {
                                    val fallbackIconType = when {
                                        banner.title.contains("carg", ignoreCase = true) || banner.title.contains("charg", ignoreCase = true) -> IslandIconType.CHARGING
                                        banner.title.contains("zona", ignoreCase = true) || banner.title.contains("hotspot", ignoreCase = true) -> IslandIconType.HOTSPOT
                                        banner.title.contains("linterna", ignoreCase = true) || banner.title.contains("torch", ignoreCase = true) -> IslandIconType.TORCH
                                        else -> IslandIconType.SOUND
                                    }
                                    PillNotchScaffold(
                                        notchMarginDp = config.notchMarginDp,
                                        showGuide = config.showGuide,
                                        leftContent = {
                                            IslandIcon(
                                                type = fallbackIconType,
                                                contentDescription = bannerTitle,
                                                tint = Color(banner.tintColor),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        rightContent = {
                                            Text(
                                                text = bannerTitle,
                                                color = Color(banner.tintColor),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    )
                                }
                            }
                        } else if (callState.isActive || activeActivity == IslandActivityType.CALL) {
                            val callTimer = rememberRunningTimer(callState.startTimeMs)
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    IslandIcon(
                                        type = IslandIconType.CALL,
                                        contentDescription = "Llamada",
                                        tint = PixelTokens.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                rightContent = {
                                    Text(
                                        text = callTimer,
                                        color = PixelTokens.Green,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            )
                        } else if (torchState.isOn || activeActivity == IslandActivityType.TORCH) {
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    Icon(
                                        imageVector = Icons.Default.FlashlightOn,
                                        contentDescription = stringResource(R.string.torch),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                rightContent = {
                                    Text(
                                        text = stringResource(R.string.torch),
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            )
                        } else if (screenRecordState.isRecording || activeActivity == IslandActivityType.SCREEN_RECORD) {
                            val recTimer = rememberRunningTimer(screenRecordState.startTimeMs)
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    PulsingRecDot(color = Color(0xFFFF3B30))
                                },
                                rightContent = {
                                    Text(
                                        text = recTimer,
                                        color = Color(0xFFFF3B30),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            )
                        } else if (downloadState.isDownloading || downloadState.isCompleted || downloadState.isFailed || downloadState.isCancelled || activeActivity == IslandActivityType.DOWNLOAD) {
                            if (downloadState.isCancelled) {
                                PillNotchScaffold(
                                    notchMarginDp = config.notchMarginDp,
                                    showGuide = config.showGuide,
                                    leftContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.cancelled),
                                                    tint = Color(0xFFFF3B30),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = downloadState.fileName.ifBlank { stringResource(R.string.download) },
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    rightContent = {
                                        Text(
                                            text = stringResource(R.string.cancelled),
                                            color = Color(0xFFFF3B30),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                )
                            } else if (downloadState.isFailed) {
                                PillNotchScaffold(
                                    notchMarginDp = config.notchMarginDp,
                                    showGuide = config.showGuide,
                                    leftContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = stringResource(R.string.failed),
                                                    tint = Color(0xFFFF3B30),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = downloadState.fileName.ifBlank { stringResource(R.string.download) },
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    rightContent = {
                                        Text(
                                            text = stringResource(R.string.failed),
                                            color = Color(0xFFFF3B30),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                )
                            } else if (downloadState.isCompleted) {
                                PillNotchScaffold(
                                    notchMarginDp = config.notchMarginDp,
                                    showGuide = config.showGuide,
                                    leftContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = stringResource(R.string.completed),
                                                tint = PixelTokens.Green,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = downloadState.fileName.ifBlank { stringResource(R.string.download) },
                                                color = Color.White,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    rightContent = {
                                        Text(
                                            text = stringResource(R.string.completed),
                                            color = PixelTokens.Green,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                )
                            } else {

                                PillNotchScaffold(
                                    notchMarginDp = config.notchMarginDp,
                                    showGuide = config.showGuide,
                                    leftContent = {
                                        if (downloadState.appIcon != null) {
                                            Image(
                                                bitmap = downloadState.appIcon!!.asImageBitmap(),
                                                contentDescription = "App",
                                                modifier = Modifier
                                                    .size(19.dp)
                                                    .clip(RoundedCornerShape(5.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Descarga",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    rightContent = {
                                        val displaySpeed = if (downloadState.speedText.isNotBlank() && downloadState.speedText != "0 B" && !downloadState.speedText.startsWith("0 B")) {
                                            downloadState.speedText
                                        } else if (downloadState.totalSizeText.isNotBlank() && downloadState.totalSizeText != "0 B" && !downloadState.totalSizeText.startsWith("0 B")) {
                                            downloadState.totalSizeText
                                        } else {
                                            ""
                                        }
                                        val progressText = if (displaySpeed.isNotBlank()) {
                                            if (displaySpeed.endsWith("%")) displaySpeed else "$displaySpeed ${downloadState.progress}%"
                                        } else if (downloadState.progress > 0) {
                                            "${downloadState.progress}%"
                                        } else {
                                            stringResource(R.string.downloading)
                                        }
                                        Text(
                                            text = progressText,
                                            color = PixelTokens.Blue,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                )
                            }
                        } else if (navigationState.isActive || activeActivity == IslandActivityType.NAVIGATION) {

                            val maneuverIcon = when (navigationState.maneuver) {
                                "TURN_LEFT" -> Icons.Default.TurnLeft
                                "STRAIGHT" -> Icons.Default.Navigation
                                "UTURN" -> Icons.Default.TurnLeft
                                else -> Icons.Default.TurnRight
                            }
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = maneuverIcon,
                                            contentDescription = "Giro",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = navigationState.distanceText.ifBlank { stringResource(R.string.nav_route) },
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                },
                                rightContent = {
                                    Text(
                                        text = navigationState.etaText.ifBlank { stringResource(R.string.nav_on_route) },
                                        color = PixelTokens.Blue,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            )
                        } else if (timerState.isActive || activeActivity == IslandActivityType.TIMER) {

                            val isTimer = timerState.type == TimerType.TIMER
                            val isStopwatch = timerState.type == TimerType.STOPWATCH
                            val isAlarm = timerState.type == TimerType.ALARM

                            val timerColor = when {
                                isAlarm -> PixelTokens.Red
                                isStopwatch -> PixelTokens.Green
                                else -> PixelTokens.Orange
                            }
                            val timerIcon = when {
                                isAlarm -> Icons.Default.Alarm
                                isStopwatch -> Icons.Default.Timer
                                else -> Icons.Default.HourglassTop
                            }
                            val displayTime = when {
                                isAlarm -> "07:00"
                                isStopwatch -> {
                                    val sec = (timerState.elapsedTimeMs / 1000) % 60
                                    val min = (timerState.elapsedTimeMs / 1000) / 60
                                    String.format("%02d:%02d", min, sec)
                                }
                                else -> {
                                    val min = timerState.remainingTimeSeconds / 60
                                    val sec = timerState.remainingTimeSeconds % 60
                                    String.format("%02d:%02d", min, sec)
                                }
                            }
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IslandIcon(
                                            type = IslandIconType.TIMER,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = if (isTimer) stringResource(R.string.timer) else if (isStopwatch) stringResource(R.string.stopwatch) else stringResource(R.string.alarm),
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                },
                                rightContent = {
                                    Text(
                                        text = displayTime,
                                        color = timerColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            )
                        } else if (castState.isCasting || activeActivity == IslandActivityType.CAST) {

                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IslandIcon(
                                            type = IslandIconType.CAST,
                                            contentDescription = stringResource(R.string.cast),
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.cast),
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                },
                                rightContent = {
                                    Text(
                                        text = castState.deviceName.ifBlank { stringResource(R.string.wireless_display) },
                                        color = PixelTokens.Blue,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        } else if (privacyState.hasAnyPrivacyActive || activeActivity == IslandActivityType.PRIVACY) {
                            val sensorColor = when {
                                privacyState.isCameraInUse -> PixelTokens.Green
                                privacyState.isMicInUse -> PixelTokens.Orange
                                else -> PixelTokens.Cyan
                            }
                            val sensorName = when {
                                privacyState.isCameraInUse -> stringResource(R.string.camera)
                                privacyState.isMicInUse -> stringResource(R.string.microphone)
                                else -> stringResource(R.string.location)
                            }
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (privacyState.isCameraInUse) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = stringResource(R.string.camera),
                                                tint = PixelTokens.Green,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        if (privacyState.isMicInUse) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = stringResource(R.string.microphone),
                                                tint = PixelTokens.Orange,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                        if (privacyState.isLocationInUse) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = stringResource(R.string.location),
                                                tint = PixelTokens.Cyan,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                },
                                rightContent = {
                                    Text(
                                        text = privacyState.appName.ifBlank { sensorName },
                                        color = sensorColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        } else if (hotspotState.isEnabled || activeActivity == IslandActivityType.HOTSPOT) {
                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                startPadding = if (hotspotState.showIntroBanner) 10.dp else 12.dp,
                                endPadding = if (hotspotState.showIntroBanner) 10.dp else 12.dp,
                                leftContent = {
                                    if (hotspotState.showIntroBanner) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IslandIcon(
                                                type = IslandIconType.HOTSPOT,
                                                contentDescription = stringResource(R.string.hotspot),
                                                tint = PixelTokens.SkyBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.hotspot),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    } else {
                                        IslandIcon(
                                            type = IslandIconType.HOTSPOT,
                                            contentDescription = stringResource(R.string.hotspot),
                                            tint = PixelTokens.SkyBlue,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                },
                                rightContent = {
                                    if (hotspotState.showIntroBanner) {
                                        Text(
                                            text = stringResource(R.string.active),
                                            color = PixelTokens.SkyBlue,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Link,
                                                contentDescription = null,
                                                tint = PixelTokens.SkyBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "${hotspotState.connectedDevices}",
                                                color = PixelTokens.SkyBlue,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            )
                        } else if (hasContent && (isMediaActive || activeActivity == IslandActivityType.MEDIA)) {

                            PillNotchScaffold(
                                notchMarginDp = config.notchMarginDp,
                                showGuide = config.showGuide,
                                leftAlignment = Alignment.Center,
                                rightAlignment = Alignment.Center,
                                leftContent = {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FlippingAlbumArt(
                                            bitmap = activeArt,
                                            trackKey = mediaState.title + mediaState.artist,
                                            size = 24.dp,
                                            cornerRadius = 6.5.dp,
                                            onClick = null
                                        )
                                    }
                                },
                                rightContent = {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        WaveformBars(
                                            isPlaying = mediaState.isPlaying,
                                            barCount = 4,
                                            colors = listOf(palette.primary, palette.secondary),
                                            maxHeight = 17.dp
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (expandedAlpha > 0f) {
                    val activeActivities = remember(
                        callState.isActive,
                        navigationState.isActive,
                        timerState.isActive,
                        isMediaActive,
                        downloadState.isDownloading,
                        downloadState.isCompleted,
                        downloadState.isFailed,
                        downloadState.isCancelled,
                        castState.isCasting,
                        hotspotState.isEnabled,
                        screenRecordState.isRecording,
                        torchState.isOn,
                        privacyState.hasAnyPrivacyActive
                    ) {
                        DynamicIslandManager.getActiveActivities()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(expandedAlpha)
                            .padding(vertical = 10.dp, horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                banner.type == TransientBannerType.CHARGING -> {
                                    ExpandedChargingCard(batteryState = batteryState)
                                }
                                banner.type == TransientBannerType.BATTERY_LOW -> {
                                    ExpandedLowBatteryCard(
                                        banner = banner,
                                        onEnableBatterySaver = {
                                            DynamicIslandManager.enableBatterySaver()
                                        }
                                    )
                                }
                                banner.type == TransientBannerType.HEADPHONES -> {
                                    ExpandedHeadphonesCard(banner = banner)
                                }
                                banner.type == TransientBannerType.SILENT_MODE || banner.type == TransientBannerType.DND -> {
                                    ExpandedSoundDndCard(banner = banner)
                                }
                                currentActivityType == IslandActivityType.CALL || callState.isActive -> {
                                    ExpandedCallCard(
                                        callState = callState,
                                        onEndCall = onEndCall
                                    )
                                }
                                currentActivityType == IslandActivityType.SCREEN_RECORD || screenRecordState.isRecording -> {
                                    ExpandedScreenRecordCard(
                                        screenRecordState = screenRecordState,
                                        onStopRecording = onStopRecording
                                    )
                                }
                                currentActivityType == IslandActivityType.NAVIGATION || navigationState.isActive -> {
                                    ExpandedNavigationCard(
                                        navigationState = navigationState
                                    )
                                }
                                currentActivityType == IslandActivityType.TIMER || timerState.isActive -> {
                                    ExpandedTimerCard(
                                        timerState = timerState
                                    )
                                }
                                currentActivityType == IslandActivityType.TORCH || torchState.isOn -> {
                                    ExpandedTorchCard(
                                        onToggleTorch = onToggleTorch
                                    )
                                }
                                currentActivityType == IslandActivityType.HOTSPOT || hotspotState.isEnabled -> {
                                    ExpandedHotspotCard(
                                        hotspotState = hotspotState,
                                        onToggleHotspot = {
                                            onToggleHotspot()
                                            DynamicIslandManager.updateHotspot(isEnabled = false, connectedDevices = 0)
                                        }
                                    )
                                }
                                currentActivityType == IslandActivityType.DOWNLOAD || downloadState.isDownloading || downloadState.isCompleted || downloadState.isFailed || downloadState.isCancelled -> {
                                    ExpandedDownloadCard(
                                        downloadState = downloadState,
                                        onCancelDownload = onCancelDownload
                                    )
                                }
                                currentActivityType == IslandActivityType.CAST || castState.isCasting -> {
                                    ExpandedCastCard(
                                        castState = castState,
                                        onDisconnect = {
                                            onDisconnectCast()
                                            DynamicIslandManager.updateCast(isCasting = false)
                                        }
                                    )
                                }
                                currentActivityType == IslandActivityType.PRIVACY || privacyState.hasAnyPrivacyActive -> {
                                    ExpandedPrivacyCard(
                                        privacyState = privacyState
                                    )
                                }
                                isClipboardCardActive || banner.type == TransientBannerType.CLIPBOARD -> {
                                    val clipboardList by DynamicIslandManager.clipboardHistory.collectAsState()
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    ExpandedClipboardCard(
                                        latestCopiedText = banner.subtitle.ifBlank { clipboardList.firstOrNull()?.text ?: "" },
                                        copiedHistory = clipboardList,
                                        onCopyAgain = { text ->
                                            DynamicIslandManager.copyToClipboard(context, text)
                                        },
                                        onClose = {
                                            DynamicIslandManager.closeClipboardCard()
                                        }
                                    )
                                }
                                currentActivityType == IslandActivityType.MEDIA || isMediaActive -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onOpenMusicApp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    Box(
                                        modifier = Modifier.size(54.dp),
                                        contentAlignment = Alignment.BottomEnd
                                    ) {
                                        FlippingAlbumArt(
                                            bitmap = activeArt,
                                            trackKey = mediaState.title + mediaState.artist,
                                            size = 54.dp,
                                            cornerRadius = 13.dp,
                                            placeholderBrush = Brush.linearGradient(
                                                listOf(palette.primary, palette.secondary)
                                            ),
                                            onClick = onOpenMusicApp
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xCC000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = onOpenMusicApp
                                            ),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = mediaState.title.ifBlank { stringResource(R.string.now_playing) },
                                            color = Color.White,
                                            fontSize = 16.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = mediaState.artist.ifBlank { stringResource(R.string.unknown_artist) },
                                            color = PixelTokens.TextSecondary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    DynamicIslandManager.openMediaOutput()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cast,
                                            contentDescription = stringResource(R.string.island_cast),
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Anterior",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onPrevious
                                        )
                                )

                                Spacer(modifier = Modifier.width(54.dp))

                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onPlayPause
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (mediaState.isPlaying) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(6.dp)
                                                    .height(26.dp)
                                                    .background(Color.White, RoundedCornerShape(3.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(6.dp)
                                                    .height(26.dp)
                                                    .background(Color.White, RoundedCornerShape(3.dp))
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Reproducir",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .offset(x = 1.5.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(54.dp))

                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Siguiente",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onNext
                                        )
                                )
                            }

                            var isDragging by remember { mutableStateOf(false) }
                            var dragFraction by remember { mutableFloatStateOf(0f) }

                            val rawProgress = if (isDragging) {
                                dragFraction
                            } else if (mediaState.durationMs > 0) {
                                (currentPositionMs.toFloat() / mediaState.durationMs).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            val animatedProgress by animateFloatAsState(
                                targetValue = rawProgress,
                                animationSpec = if (isDragging) snap() else tween(durationMillis = 80, easing = LinearEasing),
                                label = "smoothMusicProgress"
                            )
                            val progress = if (isDragging) rawProgress else animatedProgress

                            val displayPosMs = if (isDragging && mediaState.durationMs > 0) {
                                (dragFraction * mediaState.durationMs).toLong()
                            } else {
                                currentPositionMs
                            }

                            val posSec = displayPosMs / 1000
                            val durSec = mediaState.durationMs / 1000

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = String.format("%02d:%02d", posSec / 60, posSec % 60),
                                    color = PixelTokens.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .pointerInput(mediaState.durationMs) {
                                            if (mediaState.durationMs <= 0) return@pointerInput
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                val totalW = size.width.toFloat()
                                                if (totalW > 0) {
                                                    isDragging = true
                                                    isUserSeeking = true
                                                    dragFraction = (down.position.x / totalW).coerceIn(0f, 1f)
                                                    val targetPos = (dragFraction * mediaState.durationMs).toLong()
                                                    currentPositionMs = targetPos
                                                    basePositionMs = targetPos
                                                    baseTimestampMs = SystemClock.elapsedRealtime()
                                                }

                                                val pointerId = down.id
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val pointer = event.changes.firstOrNull { it.id == pointerId } ?: break
                                                    if (pointer.pressed) {
                                                        pointer.consume()
                                                        if (totalW > 0) {
                                                            dragFraction = (pointer.position.x / totalW).coerceIn(0f, 1f)
                                                            val targetPos = (dragFraction * mediaState.durationMs).toLong()
                                                            currentPositionMs = targetPos
                                                            basePositionMs = targetPos
                                                            baseTimestampMs = SystemClock.elapsedRealtime()
                                                        }
                                                    } else {
                                                        pointer.consume()
                                                        if (totalW > 0) {
                                                            val finalFraction = (pointer.position.x / totalW).coerceIn(0f, 1f)
                                                            val targetMs = (finalFraction * mediaState.durationMs).toLong()
                                                            currentPositionMs = targetMs
                                                            basePositionMs = targetMs
                                                            baseTimestampMs = SystemClock.elapsedRealtime()
                                                            onSeek(targetMs)
                                                        }
                                                        break
                                                    }
                                                }
                                                isDragging = false
                                                isUserSeeking = false
                                            }
                                        },
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val totalWidth = maxWidth
                                    val activeWidth = totalWidth * progress

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val activeWidthPx = activeWidth.toPx()
                                        val centerY = size.height / 2

                                        if (activeWidthPx > 2f) {

                                            val glowRadius = 15.dp.toPx()
                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.65f),
                                                        Color.White.copy(alpha = 0.28f),
                                                        Color.White.copy(alpha = 0.06f),
                                                        Color.Transparent
                                                    ),
                                                    center = Offset(activeWidthPx, centerY),
                                                    radius = glowRadius
                                                ),
                                                center = Offset(activeWidthPx, centerY),
                                                radius = glowRadius
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.5.dp)
                                            .clip(RoundedCornerShape(3.25.dp))
                                            .background(Color(0x38FFFFFF))
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(activeWidth)
                                            .height(6.5.dp)
                                            .clip(RoundedCornerShape(3.25.dp))
                                            .background(Color.White)
                                    )

                                    if (activeWidth > 2.dp) {
                                        val dotDiameter = if (isDragging) 15.dp else 12.dp
                                        val dotOffset = activeWidth - (dotDiameter / 2)

                                        Box(
                                            modifier = Modifier
                                                .offset(x = dotOffset)
                                                .size(dotDiameter),
                                            contentAlignment = Alignment.Center
                                        ) {

                                            Box(
                                                modifier = Modifier
                                                    .size(dotDiameter)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.radialGradient(
                                                            listOf(
                                                                Color.White,
                                                                Color.White.copy(alpha = 0.50f),
                                                                Color.Transparent
                                                            )
                                                        )
                                                    )
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .size(if (isDragging) 8.5.dp else 7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (durSec > 0) String.format("%02d:%02d", durSec / 60, durSec % 60) else "--:--",
                                    color = PixelTokens.TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }


    }
}
}
}
}
}

@Composable
fun FlippingAlbumArt(
    bitmap: Bitmap?,
    trackKey: String,
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    placeholderBrush: Brush = Brush.linearGradient(listOf(PixelTokens.Purple, PixelTokens.Blue)),
    onClick: (() -> Unit)? = null
) {
    var lastObservedKey by remember { mutableStateOf(trackKey) }
    var displayedBitmap by remember { mutableStateOf(bitmap) }
    var currentRotationY by remember { mutableFloatStateOf(0f) }
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(trackKey) {
        if (trackKey.isNotBlank() && lastObservedKey.isNotBlank() && trackKey != lastObservedKey) {
            lastObservedKey = trackKey
            try {

                rotationAnim.snapTo(0f)
                rotationAnim.animateTo(
                    targetValue = 90f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) {
                    currentRotationY = this.value
                }

                displayedBitmap = bitmap

                rotationAnim.snapTo(-90f)
                currentRotationY = -90f
                rotationAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) {
                    currentRotationY = this.value
                }
            } finally {

                currentRotationY = 0f
                rotationAnim.snapTo(0f)
                displayedBitmap = bitmap
            }
        } else {
            lastObservedKey = trackKey
            displayedBitmap = bitmap
            currentRotationY = 0f
            rotationAnim.snapTo(0f)
        }
    }

    LaunchedEffect(bitmap) {
        if (currentRotationY == 0f) {
            displayedBitmap = bitmap
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationY = currentRotationY
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val effectiveArt = displayedBitmap ?: remember(trackKey) {
            DynamicIslandManager.getAppIcon(DynamicIslandManager.mediaState.value.packageName)
        }
        if (effectiveArt != null) {
            Image(
                bitmap = effectiveArt.asImageBitmap(),
                contentDescription = "Icono o Carátula",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderBrush),
                contentAlignment = Alignment.Center
            ) {
                IslandIcon(
                    type = IslandIconType.MUSIC,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

@Composable
fun FireworksBackground(
    palette: AlbumPalette,
    isPlaying: Boolean,
    intensity: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var accumulatedTimeMs by remember { mutableLongStateOf(450L) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(16)
                val now = SystemClock.elapsedRealtime()
                accumulatedTimeMs += (now - lastTime)
                lastTime = now
            }
        }
    }

    val p1 = ((accumulatedTimeMs) % 2400L) / 2400f
    val p2 = ((accumulatedTimeMs + 600L) % 2800L) / 2800f
    val p3 = ((accumulatedTimeMs + 1200L) % 2200L) / 2200f
    val p4 = ((accumulatedTimeMs + 1700L) % 2600L) / 2600f
    val p5 = ((accumulatedTimeMs + 350L) % 3100L) / 3100f
    val p6 = ((accumulatedTimeMs + 950L) % 2700L) / 2700f
    val p7 = ((accumulatedTimeMs + 1500L) % 3300L) / 3300f
    val p8 = ((accumulatedTimeMs + 2100L) % 2900L) / 2900f

    Box(modifier = modifier) {
        // 1. Fondo base negro profundo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PixelTokens.IslandBlack)
        )

        // 2. Degradado de abajo hacia arriba con el color del album
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val gradientTopY = h * 0.32f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.primary.copy(alpha = 0.12f),
                        palette.primary.copy(alpha = 0.30f),
                        palette.primary.copy(alpha = 0.52f)
                    ),
                    startY = gradientTopY,
                    endY = h
                )
            )
        }

        // 3. Explosiones de fuegos artificiales: ejecutan su explosión normal,
        // pero solo aparecen dentro del largo/área del degradado
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val w = size.width
            val h = size.height
            val gradientTopY = h * 0.32f

            fun drawFirework(progress: Float, color: Color, center: Offset, maxRadius: Float) {
                if (progress <= 0f || progress >= 1f) return
                val radius = maxRadius * (0.22f + 0.78f * progress) * intensity.coerceIn(0.4f, 2.0f)
                val baseAlpha = (if (progress < 0.25f) {
                    (progress / 0.25f) * 0.75f
                } else {
                    ((1f - progress) / 0.75f) * 0.75f
                }) * intensity.coerceIn(0.3f, 1.8f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = (baseAlpha * 0.85f).coerceIn(0f, 1f)),
                            color.copy(alpha = (baseAlpha * 0.50f).coerceIn(0f, 1f)),
                            color.copy(alpha = (baseAlpha * 0.15f).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(40f)
                    ),
                    center = center,
                    radius = radius
                )
            }

            // Explosiones normales
            drawFirework(p1, palette.primary, Offset(w * 0.20f, h * 0.30f), w * 0.82f)
            drawFirework(p2, palette.secondary, Offset(w * 0.80f, h * 0.35f), w * 0.82f)
            drawFirework(p3, palette.tertiary, Offset(w * 0.50f, h * 0.65f), w * 0.75f)
            drawFirework(p4, palette.highlight, Offset(w * 0.25f, h * 0.85f), w * 0.70f)
            drawFirework(p5, palette.secondary, Offset(w * 0.75f, h * 0.80f), w * 0.75f)
            drawFirework(p6, palette.primary, Offset(w * 0.38f, h * 0.25f), w * 0.65f)
            drawFirework(p7, palette.highlight, Offset(w * 0.62f, h * 0.45f), w * 0.70f)
            drawFirework(p8, palette.tertiary, Offset(w * 0.85f, h * 0.60f), w * 0.65f)

            // Máscara vertical para limitar la visibilidad de los fuegos artificiales al área del degradado
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        (gradientTopY / h).coerceIn(0f, 1f) to Color.Transparent,
                        1.0f to Color.White
                    ),
                    startY = 0f,
                    endY = h
                ),
                blendMode = BlendMode.DstIn
            )
        }
    }
}

@Composable
private fun PillNotchScaffold(
    notchMarginDp: Float,
    showGuide: Boolean,
    leftAlignment: Alignment = Alignment.CenterStart,
    rightAlignment: Alignment = Alignment.CenterEnd,
    startPadding: Dp = 12.dp,
    endPadding: Dp = 12.dp,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeNotchEnd = (11.dp + notchMarginDp.dp).coerceAtLeast(0.dp)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .padding(start = startPadding, end = safeNotchEnd),
            contentAlignment = leftAlignment
        ) {
            leftContent()
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp),
            contentAlignment = Alignment.Center
        ) {
            if (showGuide) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(1.5.dp, Color.Red, CircleShape)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .padding(start = safeNotchEnd, end = endPadding),
            contentAlignment = rightAlignment
        ) {
            rightContent()
        }
    }
}

@Composable
fun rememberRunningTimer(startTimeMs: Long): String {
    var elapsedSec by remember(startTimeMs) {
        val now = System.currentTimeMillis()
        val diff = if (startTimeMs > 0 && now >= startTimeMs) (now - startTimeMs) / 1000L else 0L
        mutableLongStateOf(diff)
    }

    LaunchedEffect(startTimeMs) {
        while (isActive) {
            val now = System.currentTimeMillis()
            val diff = if (startTimeMs > 0 && now >= startTimeMs) (now - startTimeMs) / 1000L else 0L
            elapsedSec = diff
            delay(500)
        }
    }

    val hours = elapsedSec / 3600
    val minutes = (elapsedSec % 3600) / 60
    val seconds = elapsedSec % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun PulsingRecDot(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFF3B30)
) {
    val transition = rememberInfiniteTransition(label = "recPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )
    Box(
        modifier = modifier
            .size(11.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ExpandedCallCard(
    callState: CallState,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationStr = rememberRunningTimer(callState.startTimeMs)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(PixelTokens.GreenContainer),
                contentAlignment = Alignment.Center
            ) {
                IslandIcon(
                    type = IslandIconType.CALL,
                    contentDescription = "Llamada",
                    tint = PixelTokens.Green,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = callState.contactName.ifBlank { stringResource(R.string.call_ongoing) },
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PixelTokens.Green)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = durationStr,
                        color = PixelTokens.Green,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onEndCall),
            shape = RoundedCornerShape(23.dp),
            color = PixelTokens.Red
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Finalizar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.end_call),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpandedTorchCard(
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                IslandIcon(
                    type = IslandIconType.TORCH,
                    contentDescription = "Linterna",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.torch_on),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.torch_tap_to_turn_off),
                    color = PixelTokens.TextSecondary,
                    fontSize = 13.5.sp
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onToggleTorch),
            shape = RoundedCornerShape(23.dp),
            color = PixelTokens.Yellow
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FlashlightOff,
                    contentDescription = "Apagar",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.torch_off),
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpandedScreenRecordCard(
    screenRecordState: ScreenRecordState,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationStr = rememberRunningTimer(screenRecordState.startTimeMs)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                PulsingRecDot(modifier = Modifier.size(22.dp), color = Color(0xFFFF3B30))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.screen_recording),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.recording_time, durationStr),
                    color = PixelTokens.Red,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clickable(onClick = onStopRecording),
            shape = RoundedCornerShape(23.dp),
            color = PixelTokens.Red
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Detener",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stop_rec),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun formatRelativeTime(timestamp: Long): String {
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = diffMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        minutes < 1 -> stringResource(R.string.time_just_now)
        minutes < 60 -> stringResource(R.string.time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.time_hours_ago, hours.toInt())
        else -> stringResource(R.string.time_yesterday)
    }
}

@Composable
private fun ExpandedDownloadCard(
    downloadState: DownloadState,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (downloadState.isCancelled) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancelado",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else if (downloadState.isFailed) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Falló",
                        tint = Color(0xFFFF3B30),
                        modifier = Modifier.size(30.dp)
                    )
                }
            } else if (downloadState.isCompleted) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PixelTokens.GreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completado",
                        tint = PixelTokens.Green,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else if (downloadState.appIcon != null) {
                Image(
                    bitmap = downloadState.appIcon.asImageBitmap(),
                    contentDescription = "App",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    IslandIcon(
                        type = IslandIconType.DOWNLOAD,
                        contentDescription = "Descarga",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = downloadState.fileName.ifBlank { stringResource(R.string.downloading) },
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (downloadState.isDownloading && downloadState.progress > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${downloadState.progress}%",
                            color = PixelTokens.Blue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                if (downloadState.isCancelled) {
                    Text(
                        text = stringResource(R.string.download_cancelled),
                        color = Color(0xFFFF3B30),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (downloadState.isFailed) {
                    Text(
                        text = stringResource(R.string.download_failed_stopped),
                        color = Color(0xFFFF3B30),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (downloadState.isCompleted) {
                    Text(
                        text = stringResource(R.string.download_complete) + " • 100%",
                        color = PixelTokens.Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val displayInfo = when {
                        downloadState.speedText.isNotBlank() && downloadState.speedText != "0 B" && !downloadState.speedText.startsWith("0 B") -> downloadState.speedText
                        downloadState.totalSizeText.isNotBlank() && downloadState.totalSizeText != "0 B" && !downloadState.totalSizeText.startsWith("0 B") -> downloadState.totalSizeText
                        else -> stringResource(R.string.downloading)
                    }
                    Text(
                        text = displayInfo,
                        color = PixelTokens.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x33FFFFFF))
            )
            val animatedProgress by animateFloatAsState(
                targetValue = if (downloadState.isCompleted) 1f else (downloadState.progress / 100f).coerceIn(0f, 1f),
                animationSpec = tween(300, easing = LinearEasing),
                label = "downloadProgress"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0.001f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            downloadState.isCancelled || downloadState.isFailed -> Brush.horizontalGradient(listOf(Color(0xFFFF3B30), PixelTokens.RedContainer))
                            downloadState.isCompleted -> Brush.horizontalGradient(listOf(PixelTokens.Green, PixelTokens.GreenContainer))
                            else -> Brush.horizontalGradient(listOf(PixelTokens.Blue, PixelTokens.Cyan))
                        }
                    )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onCancelDownload),
            shape = RoundedCornerShape(22.dp),
            color = when {
                downloadState.isCancelled || downloadState.isFailed -> PixelTokens.RedContainer
                downloadState.isCompleted -> PixelTokens.GreenContainer
                else -> Color(0x26FFFFFF)
            }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        downloadState.isCancelled || downloadState.isFailed -> Icons.Default.Close
                        downloadState.isCompleted -> Icons.Default.Check
                        else -> Icons.Default.Close
                    },
                    contentDescription = null,
                    tint = when {
                        downloadState.isCancelled || downloadState.isFailed -> Color(0xFFFF3B30)
                        downloadState.isCompleted -> PixelTokens.Green
                        else -> Color.White
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        downloadState.isCancelled || downloadState.isFailed -> stringResource(R.string.close)
                        downloadState.isCompleted -> stringResource(R.string.done)
                        else -> stringResource(R.string.cancel)
                    },
                    color = when {
                        downloadState.isCancelled || downloadState.isFailed -> Color(0xFFFF3B30)
                        downloadState.isCompleted -> PixelTokens.Green
                        else -> Color.White
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ExpandedHotspotCard(
    hotspotState: HotspotState,
    onToggleHotspot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(PixelTokens.SkyBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                IslandIcon(
                    type = IslandIconType.HOTSPOT,
                    contentDescription = "Punto de acceso",
                    tint = PixelTokens.SkyBlue,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.hotspot),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = PixelTokens.SkyBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    val devText = when (hotspotState.connectedDevices) {
                        0 -> stringResource(R.string.hotspot_devices_none)
                        1 -> stringResource(R.string.hotspot_devices_one)
                        else -> stringResource(R.string.hotspot_devices_many, hotspotState.connectedDevices)
                    }
                    Text(
                        text = devText,
                        color = PixelTokens.SkyBlue,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onToggleHotspot),
            shape = RoundedCornerShape(22.dp),
            color = PixelTokens.SkyBlue
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Desactivar",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.turn_off),
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpandedClipboardCard(
    latestCopiedText: String,
    copiedHistory: List<CopiedItem>,
    onCopyAgain: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var copiedItemText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedItemText) {
        if (copiedItemText != null) {
            delay(1800L)
            copiedItemText = null
        }
    }

    val displayList = remember(copiedHistory, latestCopiedText) {
        if (copiedHistory.isNotEmpty()) {
            copiedHistory
        } else if (latestCopiedText.isNotBlank()) {
            listOf(CopiedItem(latestCopiedText, System.currentTimeMillis()))
        } else {
            emptyList()
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PixelTokens.BlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    IslandIcon(
                        type = IslandIconType.CLIPBOARD,
                        contentDescription = stringResource(R.string.clipboard),
                        tint = PixelTokens.Blue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.clipboard),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (displayList.isNotEmpty()) stringResource(R.string.clipboard_items_count, displayList.size) else stringResource(R.string.clipboard_empty),
                        color = PixelTokens.TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = PixelTokens.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (displayList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.clipboard_empty_desc),
                    color = PixelTokens.TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = displayList,
                    key = { "${it.timestamp}_${it.text.hashCode()}" }
                ) { item ->
                    val isJustCopied = copiedItemText == item.text
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCopyAgain(item.text)
                                copiedItemText = item.text
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isJustCopied) PixelTokens.GreenContainer else Color(0x1FFFFFFF),
                        border = BorderStroke(
                            1.dp,
                            if (isJustCopied) PixelTokens.Green else Color(0x28FFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = item.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatRelativeTime(item.timestamp),
                                    color = PixelTokens.TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isJustCopied) PixelTokens.GreenContainer
                                        else PixelTokens.BlueContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isJustCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = if (isJustCopied) stringResource(R.string.copied) else stringResource(R.string.copy),
                                    tint = if (isJustCopied) PixelTokens.Green else PixelTokens.Blue,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedNavigationCard(
    navigationState: NavigationState
) {
    val maneuverIcon = when (navigationState.maneuver) {
        "TURN_LEFT" -> Icons.Default.TurnLeft
        "STRAIGHT" -> Icons.Default.Navigation
        "UTURN" -> Icons.Default.TurnLeft
        else -> Icons.Default.TurnRight
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(PixelTokens.Blue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = maneuverIcon,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = navigationState.distanceText.ifBlank { stringResource(R.string.next_maneuver) },
                    color = PixelTokens.Blue,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = navigationState.instruction.ifBlank { stringResource(R.string.follow_route) },
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            if (navigationState.etaText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PixelTokens.BlueContainer
                ) {
                    Text(
                        text = navigationState.etaText,
                        color = PixelTokens.Blue,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable {
                        DynamicIslandManager.stopLiveNavigationSimulation()
                    },
                shape = RoundedCornerShape(19.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.stop),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1.3f)
                    .height(38.dp)
                    .clickable {
                        try {
                            navigationState.openIntent?.send()
                        } catch (_: Exception) {}
                    },
                shape = RoundedCornerShape(19.dp),
                color = PixelTokens.Blue
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NearMe,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.open_maps),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedTimerCard(
    timerState: TimerState
) {
    val isTimer = timerState.type == TimerType.TIMER
    val isStopwatch = timerState.type == TimerType.STOPWATCH
    val isAlarm = timerState.type == TimerType.ALARM

    val accentColor = when {
        isAlarm -> PixelTokens.Red
        isStopwatch -> PixelTokens.Green
        else -> PixelTokens.Orange
    }

    val displayTime = when {
        isAlarm -> "07:00 AM"
        isStopwatch -> {
            val totalSec = timerState.elapsedTimeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (timerState.elapsedTimeMs % 1000) / 100
            String.format("%02d:%02d.%d", min, sec, ms)
        }
        else -> {
            val min = timerState.remainingTimeSeconds / 60
            val sec = timerState.remainingTimeSeconds % 60
            String.format("%02d:%02d", min, sec)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IslandIcon(
                    type = IslandIconType.TIMER,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = timerState.label.ifBlank { if (isTimer) stringResource(R.string.timer) else if (isStopwatch) stringResource(R.string.stopwatch) else stringResource(R.string.alarm) },
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = if (isAlarm) stringResource(R.string.ringing) else if (timerState.isPaused) stringResource(R.string.paused) else stringResource(R.string.active),
                    color = accentColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Text(
            text = displayTime,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable {
                        try {
                            if (isAlarm) timerState.alarmDismissIntent?.send()
                            else timerState.cancelIntent?.send()
                        } catch (_: Exception) {}
                        DynamicIslandManager.cancelTimer()
                    },
                shape = RoundedCornerShape(19.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isAlarm) stringResource(R.string.dismiss) else stringResource(R.string.cancel),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clickable {
                        try {
                            timerState.pauseResumeIntent?.send()
                        } catch (_: Exception) {}
                        DynamicIslandManager.toggleTimerPauseResume()
                    },
                shape = RoundedCornerShape(19.dp),
                color = accentColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isAlarm) stringResource(R.string.snooze) else if (timerState.isPaused) stringResource(R.string.resume) else stringResource(R.string.pause),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedCastCard(
    castState: CastState,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IslandIcon(
                    type = IslandIconType.CAST,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.casting_screen),
                    color = PixelTokens.Blue,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = castState.deviceName.ifBlank { stringResource(R.string.wireless_display) },
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable(onClick = onDisconnect),
            shape = RoundedCornerShape(19.dp),
            color = PixelTokens.Red
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.disconnect_display),
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clickable {
                    DynamicIslandManager.openMediaOutput()
                },
            shape = RoundedCornerShape(19.dp),
            color = Color.White.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.island_cast_audio_only),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ExpandedPrivacyCard(
    privacyState: PrivacyState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(
                text = stringResource(R.string.privacy_title),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            val inUseText = if (privacyState.appName.isNotBlank()) {
                privacyState.appName
            } else when {
                privacyState.isCameraInUse -> stringResource(R.string.camera)
                privacyState.isMicInUse -> stringResource(R.string.microphone)
                privacyState.isLocationInUse -> stringResource(R.string.location)
                else -> stringResource(R.string.system_app)
            }
            Text(
                text = stringResource(R.string.in_use_by, inUseText),
                color = PixelTokens.TextSecondary,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (privacyState.isCameraInUse) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PixelTokens.GreenContainer,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = PixelTokens.Green,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = stringResource(R.string.camera),
                            color = PixelTokens.Green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (privacyState.isMicInUse) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PixelTokens.OrangeContainer,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = PixelTokens.Orange,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = stringResource(R.string.microphone),
                            color = PixelTokens.Orange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (privacyState.isLocationInUse) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PixelTokens.CyanContainer,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = PixelTokens.Cyan,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = stringResource(R.string.location),
                            color = PixelTokens.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedChargingCard(batteryState: DynamicIslandBatteryState) {
    val greenColor = PixelTokens.Green
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = greenColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IslandIcon(
                            type = if (batteryState.watts > 30.0) IslandIconType.CHARGING_SUPER else IslandIconType.CHARGING,
                            contentDescription = null,
                            tint = greenColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = if (batteryState.watts >= 65.0) {
                            stringResource(R.string.charging_hypercharge)
                        } else if (batteryState.watts >= 30.0) {
                            stringResource(R.string.charging_turbo)
                        } else if (batteryState.watts >= 15.0) {
                            stringResource(R.string.charging_fast)
                        } else if (batteryState.isCharging) {
                            stringResource(R.string.charging_battery)
                        } else {
                            stringResource(R.string.charging_poco_battery)
                        },
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.device_specs_battery),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "${batteryState.level}%",
                color = greenColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { (batteryState.level / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = greenColor,
            trackColor = Color.White.copy(alpha = 0.15f)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = stringResource(R.string.power_label), color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (batteryState.watts > 1.0) {
                            if (batteryState.watts >= 10.0) "${batteryState.watts.toInt()}W"
                            else String.format(java.util.Locale.US, "%.1fW", batteryState.watts)
                        } else if (batteryState.isCharging) {
                            stringResource(R.string.charging_label)
                        } else {
                            "--"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = stringResource(R.string.est_time_label), color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    val estMin = ((100 - batteryState.level) * 0.4).toInt().coerceAtLeast(1)
                    Text(
                        text = if (batteryState.level >= 100) stringResource(R.string.charging_complete) else "~$estMin min",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = stringResource(R.string.health_label), color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.health_optimal),
                        color = greenColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandedHeadphonesCard(banner: TransientBannerState) {
    val isDisconnected = banner.iconName == "headphones_off" || banner.subtitleResId == R.string.island_disconnected
    val themeColor = if (isDisconnected) PixelTokens.Red else PixelTokens.Cyan
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = themeColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IslandIcon(
                            type = IslandIconType.HEADPHONES,
                            contentDescription = null,
                            tint = themeColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = banner.title.ifBlank { stringResource(if (isDisconnected) R.string.island_headphones_disconnected else R.string.headphones_connected) },
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (banner.subtitleResId != 0) stringResource(banner.subtitleResId) else banner.subtitle.ifBlank { stringResource(if (isDisconnected) R.string.island_disconnected else R.string.headphones_hires) },
                        color = if (isDisconnected) PixelTokens.Red else Color.White.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
            Icon(
                imageVector = if (isDisconnected) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = themeColor.copy(alpha = 0.12f),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.dolby_atmos), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = stringResource(R.string.low_latency), color = Color.White.copy(alpha = 0.85f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ExpandedSoundDndCard(banner: TransientBannerState) {
    val isDnd = banner.type == TransientBannerType.DND
    val accentColor = if (isDnd) PixelTokens.Purple else PixelTokens.Red

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IslandIcon(
                            type = when {
                                isDnd -> IslandIconType.DND
                                banner.iconName == "silent" -> IslandIconType.SILENT
                                banner.iconName == "vibrate" -> IslandIconType.VIBRATE
                                banner.iconName == "sound" -> IslandIconType.SOUND
                                else -> IslandIconType.SOUND
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = when {
                            isDnd -> stringResource(R.string.mode_dnd)
                            banner.iconName == "silent" -> stringResource(R.string.mode_silent)
                            banner.iconName == "vibrate" -> stringResource(R.string.mode_vibrate)
                            else -> stringResource(R.string.mode_sound)
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (isDnd) stringResource(R.string.dnd_description) else stringResource(R.string.sound_description),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (banner.iconName == "silent") PixelTokens.RedContainer else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        DynamicIslandManager.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IslandIcon(
                        type = IslandIconType.SILENT,
                        contentDescription = null,
                        tint = if (banner.iconName == "silent") PixelTokens.Red else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.silent),
                        color = if (banner.iconName == "silent") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (banner.iconName == "vibrate") PixelTokens.OrangeContainer else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        DynamicIslandManager.setRingerMode(android.media.AudioManager.RINGER_MODE_VIBRATE)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IslandIcon(
                        type = IslandIconType.VIBRATE,
                        contentDescription = null,
                        tint = if (banner.iconName == "vibrate") PixelTokens.Orange else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vibrate),
                        color = if (banner.iconName == "vibrate") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (banner.iconName == "sound") PixelTokens.GreenContainer else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        DynamicIslandManager.setRingerMode(android.media.AudioManager.RINGER_MODE_NORMAL)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IslandIcon(
                        type = IslandIconType.SOUND,
                        contentDescription = null,
                        tint = if (banner.iconName == "sound") PixelTokens.Green else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sound),
                        color = if (banner.iconName == "sound") Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDnd) PixelTokens.PurpleContainer else Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        DynamicIslandManager.toggleDndMode()
                    }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IslandIcon(
                        type = IslandIconType.DND,
                        contentDescription = null,
                        tint = if (isDnd) PixelTokens.Purple else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dnd),
                        color = if (isDnd) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandedLowBatteryCard(
    banner: TransientBannerState,
    onEnableBatterySaver: () -> Unit
) {
    val alertColor = Color(0xFFFF3B30)
    val batteryPct = banner.subtitle.replace("%", "").trim().toIntOrNull() ?: 20
    val progress = (batteryPct / 100f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = alertColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_power_low),
                            contentDescription = null,
                            tint = alertColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.island_battery_low),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.island_battery_saver_desc),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "$batteryPct%",
                color = alertColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = alertColor,
            trackColor = Color.White.copy(alpha = 0.15f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = alertColor.copy(alpha = 0.22f),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnableBatterySaver() }
        ) {
            Row(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_power_saver),
                    contentDescription = null,
                    tint = alertColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.island_battery_saver_btn),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

