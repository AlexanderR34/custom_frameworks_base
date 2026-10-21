/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.screenshot

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Environment
import android.os.UserHandle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Display
import com.android.internal.R
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.statusbar.notification.NotificationUtils
import com.android.systemui.util.NotificationChannels
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.Date

/** Convenience class to handle showing and hiding notifications while taking a screenshot. */
class ScreenshotNotificationsController
@AssistedInject
internal constructor(
    @Assisted private val displayId: Int,
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val devicePolicyManager: DevicePolicyManager,
    private val actionIntentCreator: ActionIntentCreator?,
) {
    constructor(
        displayId: Int,
        context: Context,
        notificationManager: NotificationManager,
        devicePolicyManager: DevicePolicyManager,
    ) : this(displayId, context, notificationManager, devicePolicyManager, null)

    private val res = context.resources

    /**
     * Sends a notification that the screenshot capture has failed.
     *
     * @param msgResId The resource ID of the error message string to display.
     */
    fun notifyScreenshotError(msgResId: Int) {
        val errorMsg = res.getString(msgResId)
        showErrorNotification(
            errorMsg,
            res.getString(com.android.systemui.res.R.string.screenshot_failed_title),
        )
    }

    /**
     * Sends a notification that the screenshot capture has failed for custom save location.
     *
     * @param message The exact error message string to display.
     */
    fun notifyCustomUriSaveError(message: String) {
        val titleMessage =
            context.getString(
                com.android.systemui.res.R.string.screenshot_custom_uri_save_fail_title,
                Environment.DIRECTORY_SCREENSHOTS,
            )
        showErrorNotification(message, titleMessage)
    }

    /**
     * Sends a standard notification with preview, timestamp, and actions (Share, Edit, Delete)
     * when a screenshot has been successfully captured and saved, if enabled in Settings.
     */
    fun notifyScreenshotSaved(uri: Uri, bitmap: Bitmap?, timestamp: Long) {
        val isEnabled = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREENSHOT_NOTIFICATION_ENABLED,
            0
        ) == 1
        if (!isEnabled) {
            return
        }

        val title = res.getString(com.android.systemui.res.R.string.screenshot_saved_title)
        val timeFormatted = DateFormat.getTimeFormat(context).format(Date(timestamp))

        val id = if (displayId == Display.DEFAULT_DISPLAY) {
            SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT
        } else {
            SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT_EXTERNAL_DISPLAY
        }
        val tag = "screenshot_saved_${uri.hashCode()}"

        val builder = Notification.Builder(context, NotificationChannels.SCREENSHOTS_HEADSUP)
            .setContentTitle(title)
            .setContentText(timeFormatted)
            .setSmallIcon(com.android.systemui.res.R.drawable.ic_camera)
            .setWhen(timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setColor(context.getColor(R.color.system_notification_accent_color))

        // Open/View intent on click
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            uri.hashCode(),
            viewIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.setContentIntent(viewPendingIntent)

        // Downscale bitmap for notification preview to avoid Binder buffer limits
        val previewBitmap = if (bitmap != null) {
            val maxDimension = 1080
            if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            } else {
                bitmap
            }
        } else {
            null
        }

        if (previewBitmap != null) {
            val style = Notification.BigPictureStyle()
                .bigPicture(previewBitmap)
                .setBigContentTitle(title)
                .setSummaryText(timeFormatted)
            builder.setStyle(style)
        }

        // Action: Share
        val shareIntent = actionIntentCreator?.createShare(uri) ?: Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context,
            uri.hashCode() + 1,
            shareIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shareAction = Notification.Action.Builder(
            Icon.createWithResource(context, com.android.systemui.res.R.drawable.ic_screenshot_share),
            context.getString(com.android.systemui.res.R.string.screenshot_share_label),
            sharePendingIntent,
        ).build()
        builder.addAction(shareAction)

        // Action: Edit
        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, "image/png")
            putExtra("edit_source", "screenshot")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            uri.hashCode() + 2,
            editIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val editAction = Notification.Action.Builder(
            Icon.createWithResource(context, com.android.systemui.res.R.drawable.ic_screenshot_edit),
            context.getString(com.android.systemui.res.R.string.screenshot_edit_label),
            editPendingIntent,
        ).build()
        builder.addAction(editAction)

        // Action: Delete
        val deleteIntent = Intent(context, DeleteScreenshotReceiver::class.java).apply {
            data = uri
            putExtra(DeleteScreenshotReceiver.EXTRA_NOTIFICATION_ID, id)
            putExtra(DeleteScreenshotReceiver.EXTRA_NOTIFICATION_TAG, tag)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            uri.hashCode() + 3,
            deleteIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
        val deleteAction = Notification.Action.Builder(
            Icon.createWithResource(context, com.android.systemui.res.R.drawable.ic_screenshot_delete),
            context.getString(com.android.systemui.res.R.string.screen_record_delete),
            deletePendingIntent,
        ).build()
        builder.addAction(deleteAction)

        NotificationUtils.overrideNotificationAppName(context, builder, true)
        notificationManager.notify(tag, id, builder.build())
    }

    /**
     * Builds and displays the actual error notification. Errors for the non-default display are
     * shown in a unique separate notification.
     *
     * @param baseMessage The main text of the error to show.
     */
    private fun showErrorNotification(baseMessage: String, titleMessage: String) {
        val displayErrorString =
            if (displayId != Display.DEFAULT_DISPLAY) {
                " ($externalDisplayString)"
            } else {
                ""
            }
        val errorMsg = baseMessage + displayErrorString

        // Repurpose the existing notification or create a new one
        val builder =
            Notification.Builder(context, NotificationChannels.ALERTS)
                .setTicker(titleMessage)
                .setContentTitle(titleMessage)
                .setContentText(errorMsg)
                .setSmallIcon(com.android.systemui.res.R.drawable.stat_notify_image_error)
                .setWhen(System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PUBLIC) // ok to show outside lockscreen
                .setCategory(Notification.CATEGORY_ERROR)
                .setAutoCancel(true)
                .setColor(context.getColor(R.color.system_notification_accent_color))
        val intent =
            devicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE
            )
        if (intent != null) {
            val pendingIntent =
                PendingIntent.getActivityAsUser(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE,
                    null,
                    UserHandle.CURRENT,
                )
            builder.setContentIntent(pendingIntent)
        }
        NotificationUtils.overrideNotificationAppName(context, builder, true)
        val notification = Notification.BigTextStyle(builder).bigText(errorMsg).build()
        // A different id for external displays to keep the 2 error notifications separated.
        val id =
            if (displayId == Display.DEFAULT_DISPLAY) {
                SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT
            } else {
                SystemMessageProto.SystemMessage.NOTE_GLOBAL_SCREENSHOT_EXTERNAL_DISPLAY
            }
        notificationManager.notify(id, notification)
    }

    private val externalDisplayString: String
        get() =
            res.getString(
                com.android.systemui.res.R.string.screenshot_failed_external_display_indication
            )

    /** Factory for [ScreenshotNotificationsController]. */
    @AssistedFactory
    fun interface Factory {
        fun create(displayId: Int): ScreenshotNotificationsController
    }
}
