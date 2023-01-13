// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution.impl;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.auto.value.AutoValue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseApp.BackgroundStateChangeListener;
import com.google.firebase.appdistribution.InterruptionLevel;
import javax.inject.Inject;

class FirebaseAppDistributionNotificationsManager implements BackgroundStateChangeListener {
  private static final String TAG = "NotificationsManager";

  private static final String PACKAGE_PREFIX = "com.google.firebase.appdistribution";

  @VisibleForTesting
  static final String CHANNEL_GROUP_ID = prependPackage("notification_channel_group_id");

  @VisibleForTesting
  enum Notification {
    APP_UPDATE("notification_channel_id", "app_update_notification_tag"),
    FEEDBACK("feedback_notification_channel_id", "feedback_notification_tag");

    final String channelId;
    final String tag;
    final int id;

    Notification(String channelId, String tag) {
      this.channelId = prependPackage(channelId);
      this.tag = prependPackage(tag);
      this.id = ordinal();
    }
  }

  @AutoValue
  abstract static class FeedbackNotificationData {
    abstract CharSequence infoText();
    abstract InterruptionLevel interruptionLevel();

    static FeedbackNotificationData create(CharSequence infoText, InterruptionLevel interruptionLevel) {
      return new AutoValue_FirebaseAppDistributionNotificationsManager_FeedbackNotificationData(infoText, interruptionLevel);
    }
  }

  @Nullable private FeedbackNotificationData currentNotificationData;
  private boolean hiddenWhileInBackground = false;

  private final Context context;
  private final FirebaseApp firebaseApp;
  private final AppIconSource appIconSource;
  private final NotificationManagerCompat notificationManager;

  @Inject
  FirebaseAppDistributionNotificationsManager(Context context, FirebaseApp firebaseApp, AppIconSource appIconSource) {
    this.context = context;
    this.firebaseApp = firebaseApp;
    this.appIconSource = appIconSource;
    this.notificationManager = NotificationManagerCompat.from(context);
  }

  void showAppUpdateNotification(long totalBytes, long downloadedBytes, int stringResourceId) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.i(TAG, "Creating app update notification channel group");
      createChannel(
          Notification.APP_UPDATE,
          R.string.app_update_notification_channel_name,
          R.string.app_update_notification_channel_description,
          InterruptionLevel.DEFAULT);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.w(
          TAG, "Not showing app update notifications because app notifications are disabled");
      return;
    }

    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(context, Notification.APP_UPDATE.channelId)
            .setOnlyAlertOnce(true)
            .setSmallIcon(appIconSource.getNonAdaptiveIconOrDefault(context))
            .setContentTitle(context.getString(stringResourceId))
            .setProgress(
                /* max= */ 100,
                /* progress= */ (int) (((float) downloadedBytes / (float) totalBytes) * 100),
                /* indeterminate= */ false);
    PendingIntent appLaunchIntent = createAppLaunchIntent();
    if (appLaunchIntent != null) {
      notificationBuilder.setContentIntent(appLaunchIntent);
    }
    notificationManager.notify(
        Notification.APP_UPDATE.tag, Notification.APP_UPDATE.id, notificationBuilder.build());
  }

  @Nullable
  private PendingIntent createAppLaunchIntent() {
    // Query the package manager for the best launch intent for the app
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
    if (intent == null) {
      LogWrapper.w(TAG, "No activity found to launch app");
      return null;
    }
    return getPendingIntent(intent, PendingIntent.FLAG_ONE_SHOT);
  }

  private PendingIntent getPendingIntent(Intent intent, int extraFlags) {
    // Specify mutability because it is required starting at SDK level 31, but FLAG_IMMUTABLE is
    // only supported starting at SDK level 23
    int commonFlags =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
    return PendingIntent.getActivity(
        context, /* requestCode = */ 0, intent, extraFlags | commonFlags);
  }

  void showFeedbackNotification(
      @NonNull CharSequence infoText, @NonNull InterruptionLevel interruptionLevel) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      LogWrapper.i(TAG, "Creating feedback notification channel group");
      createChannel(
          Notification.FEEDBACK,
          R.string.feedback_notification_channel_name,
          R.string.feedback_notification_channel_description,
          interruptionLevel);
    }

    if (!notificationManager.areNotificationsEnabled()) {
      LogWrapper.w(TAG, "Not showing notification because app notifications are disabled");
      return;
    }

    doShowFeedbackNotification(infoText, interruptionLevel);
    currentNotificationData = FeedbackNotificationData.create(infoText, interruptionLevel);
    firebaseApp.addBackgroundStateChangeListener(this);
  }

  private void doShowFeedbackNotification(
      @NonNull CharSequence infoText, @NonNull InterruptionLevel interruptionLevel) {
    Intent intent = new Intent(context, TakeScreenshotAndStartFeedbackActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
    intent.putExtra(TakeScreenshotAndStartFeedbackActivity.INFO_TEXT_EXTRA_KEY, infoText);
    ApplicationInfo applicationInfo = context.getApplicationInfo();
    PackageManager packageManager = context.getPackageManager();
    CharSequence appLabel = packageManager.getApplicationLabel(applicationInfo);
    NotificationCompat.Builder builder =
        new NotificationCompat.Builder(context, Notification.FEEDBACK.channelId)
            .setSmallIcon(R.drawable.ic_baseline_rate_review_24)
            .setContentTitle(context.getString(R.string.feedback_notification_title))
            .setContentText(context.getString(R.string.feedback_notification_text, appLabel))
            .setPriority(interruptionLevel.notificationPriority)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setContentIntent(getPendingIntent(intent, /* extraFlags= */ 0));
    LogWrapper.i(TAG, "Showing feedback notification");
    notificationManager.notify(
        Notification.FEEDBACK.tag, Notification.FEEDBACK.id, builder.build());
  }

  public void cancelFeedbackNotification() {
    doCancelFeedbackNotification();
    currentNotificationData = null;
    hiddenWhileInBackground = false;
    firebaseApp.removeBackgroundStateChangeListener(this);
  }

  private void doCancelFeedbackNotification() {
    LogWrapper.i(TAG, "Cancelling feedback notification");
    NotificationManagerCompat.from(context)
        .cancel(Notification.FEEDBACK.tag, Notification.FEEDBACK.id);
  }

  @Override
  public void onBackgroundStateChanged(boolean background) {
    LogWrapper.d(TAG, "App background state changed to: " + background);
    if (background && currentNotificationData != null) {
      LogWrapper.i(TAG, "Hiding feedback notification because app went to background");
      doCancelFeedbackNotification();
      hiddenWhileInBackground = true;
    } else if (hiddenWhileInBackground && currentNotificationData != null) {
      LogWrapper.i(TAG, "Showing feedback notification because app returned from background");
      doShowFeedbackNotification(currentNotificationData.infoText(), currentNotificationData.interruptionLevel());
      hiddenWhileInBackground = false;
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private void createChannel(
      Notification notification, int name, int description, InterruptionLevel interruptionLevel) {
    notificationManager.createNotificationChannelGroup(
        new NotificationChannelGroup(
            CHANNEL_GROUP_ID, context.getString(R.string.notifications_group_name)));
    NotificationChannel channel =
        new NotificationChannel(
            notification.channelId, context.getString(name), interruptionLevel.channelImportance);
    channel.setDescription(context.getString(description));
    channel.setGroup(CHANNEL_GROUP_ID);
    // Register the channel with the system; you can't change the importance
    // or other notification behaviors after this
    notificationManager.createNotificationChannel(channel);
  }

  private static String prependPackage(String id) {
    return String.format("%s.%s", PACKAGE_PREFIX, id);
  }
}
