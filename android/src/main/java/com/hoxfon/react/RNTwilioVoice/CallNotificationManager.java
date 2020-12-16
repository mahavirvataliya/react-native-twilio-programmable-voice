package com.hoxfon.react.RNTwilioVoice;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;

import java.util.List;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import static android.content.Context.ACTIVITY_SERVICE;

import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.TAG;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_ANSWER_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_REJECT_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_HANGUP_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_MISSED_CALL;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.INCOMING_CALL_INVITE;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.INCOMING_CALL_NOTIFICATION_ID;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.NOTIFICATION_TYPE;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.CALL_SID_KEY;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.INCOMING_NOTIFICATION_PREFIX;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.MISSED_CALLS_GROUP;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.MISSED_CALLS_NOTIFICATION_ID;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.HANGUP_NOTIFICATION_ID;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.PREFERENCE_KEY;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.ACTION_CLEAR_MISSED_CALLS_COUNT;
import static com.hoxfon.react.RNTwilioVoice.TwilioVoiceModule.CLEAR_MISSED_CALLS_NOTIFICATION_ID;



public class CallNotificationManager {

    private static final String VOICE_CHANNEL = "default";

    private NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

    public CallNotificationManager() {}

    public int getApplicationImportance(ReactApplicationContext context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        if (activityManager == null) {
            return 0;
        }
        List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return 0;
        }

        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(context.getApplicationInfo().packageName)) {
                return processInfo.importance;
            }
        }
        return 0;
    }

    public Class getMainActivityClass(ReactApplicationContext context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Intent getLaunchIntent(ReactApplicationContext context,
                                  int notificationId,
                                  CallInvite callInvite,
                                  Boolean shouldStartNewTask,
                                  int appImportance
    ) {
        Intent launchIntent = new Intent(context, getMainActivityClass(context));

        int launchFlag = Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;
        if (shouldStartNewTask || appImportance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            launchFlag = Intent.FLAG_ACTIVITY_NEW_TASK;
        }

        launchIntent.setAction(ACTION_INCOMING_CALL)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .addFlags(
                        launchFlag +
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD +
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON +
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );

        if (callInvite != null) {
            launchIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
        }
        return launchIntent;
    }

    private Spannable getActionText(String stringRes, int colorRes) {
        Spannable spannable = new SpannableString(stringRes);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        // This will only work for cases where the Notification.Builder has a fullscreen intent set
        // Notification.Builder that does not have a full screen intent will take the color of the
        // app and the following leads to a no-op.
        spannable.setSpan(
            new ForegroundColorSpan(colorRes), 0, spannable.length(), 0);
        }
        return spannable;
    }

    private String sanatizeCallerName(String from) {
        String from_name = from.replace("client:", "");
        String removedClient = from.replace("client:", "");
        String removedUserId = removedClient.replaceAll("\\d","");
        String replaceUnderScore = removedUserId.replaceAll("_", " ");
        return replaceUnderScore.toUpperCase().trim();
    }

    public void createIncomingCallNotification(ReactApplicationContext context,
                                               CallInvite callInvite,
                                               int notificationId,
                                               Intent launchIntent)
    {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "createIncomingCallNotification intent "+launchIntent.getFlags());
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(INCOMING_CALL_NOTIFICATION_ID, notificationId);
        extras.putString(CALL_SID_KEY, callInvite.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_INCOMING_CALL);
        /*
         * Create the notification shown in the notification drawer
         */
        initCallNotificationsChannel(notificationManager, true);

        String from_name = sanatizeCallerName(callInvite.getFrom());

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, VOICE_CHANNEL)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setOnlyAlertOnce(true)
                        .setVibrate(new long[]{0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000})
                        .setContentTitle("Incoming call")
                        .setContentText( from_name + " is calling")
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setSmallIcon(R.drawable.ic_call_white_24dp)
                        .setExtras(extras)
                        .setFullScreenIntent(pendingIntent, true);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (largeIconResId != 0) {
                notificationBuilder.setLargeIcon(largeIconBitmap);
            }
        }

        // Reject action
        Intent rejectIntent = new Intent(ACTION_REJECT_CALL)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingRejectIntent = PendingIntent.getBroadcast(context, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.addAction(0, getActionText( "DISMISS", Color.parseColor("#800000")), pendingRejectIntent);

        // Answer action
        Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
        answerIntent
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingAnswerIntent = PendingIntent.getBroadcast(context, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.addAction(R.drawable.ic_call_white_24dp, getActionText("ANSWER", Color.parseColor("#008000")), pendingAnswerIntent);

        notificationManager.notify(notificationId, notificationBuilder.build());
        TwilioVoiceModule.callNotificationMap.put(INCOMING_NOTIFICATION_PREFIX+callInvite.getCallSid(), notificationId);
    }

    public void initCallNotificationsChannel(NotificationManager notificationManager, boolean isIncoming) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(VOICE_CHANNEL, "Voice Call", NotificationManager.IMPORTANCE_HIGH);

        if(!isIncoming) {
            channel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);
        }
        channel.setLightColor(Color.GREEN);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        if(isIncoming) {
            channel.setVibrationPattern(new long[]{0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000, 0, 1000, 500, 1000});
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setShowBadge(true);
            channel.setDescription("Voice call notifications");
        }

        notificationManager.createNotificationChannel(channel);
    }

    public void createMissedCallNotification(ReactApplicationContext context, CallInvite callInvite) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();

        /*
         * Create a PendingIntent to specify the action when the notification is
         * selected in the notification drawer
         */
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(ACTION_MISSED_CALL)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent clearMissedCallsCountIntent = new Intent(ACTION_CLEAR_MISSED_CALLS_COUNT)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, CLEAR_MISSED_CALLS_NOTIFICATION_ID);
        PendingIntent clearMissedCallsCountPendingIntent = PendingIntent.getBroadcast(context, 0, clearMissedCallsCountIntent, 0);
        /*
         * Pass the notification id and call sid to use as an identifier to open the notification
         */
        Bundle extras = new Bundle();
        extras.putInt(INCOMING_CALL_NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID);
        extras.putString(CALL_SID_KEY, callInvite.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_MISSED_CALL);

        /*
         * Create the notification shown in the notification drawer
         */
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context, VOICE_CHANNEL)
                        .setGroup(MISSED_CALLS_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setSmallIcon(R.drawable.ic_call_missed_white_24dp)
                        .setContentTitle("Missed call")
                        .setContentText(callInvite.getFrom() + " called")
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setExtras(extras)
                        .setDeleteIntent(clearMissedCallsCountPendingIntent)
                        .setContentIntent(pendingIntent);

        int missedCalls = sharedPref.getInt(MISSED_CALLS_GROUP, 0);
        missedCalls++;
        if (missedCalls == 1) {
            inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle("Missed call");
        } else {
            inboxStyle.setBigContentTitle(String.valueOf(missedCalls) + " missed calls");
        }
        inboxStyle.addLine("from: " +callInvite.getFrom());
        sharedPrefEditor.putInt(MISSED_CALLS_GROUP, missedCalls);
        sharedPrefEditor.commit();

        notification.setStyle(inboxStyle);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && largeIconResId != 0) {
            notification.setLargeIcon(largeIconBitmap);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(MISSED_CALLS_NOTIFICATION_ID, notification.build());
    }

    public void createHangupLocalNotification(ReactApplicationContext context, String callSid, String caller) {
        PendingIntent pendingHangupIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_HANGUP_CALL).putExtra(INCOMING_CALL_NOTIFICATION_ID, HANGUP_NOTIFICATION_ID),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        Intent launchIntent = new Intent(context, getMainActivityClass(context));
        launchIntent.setAction(ACTION_INCOMING_CALL)
                .putExtra(INCOMING_CALL_NOTIFICATION_ID, HANGUP_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(INCOMING_CALL_NOTIFICATION_ID, HANGUP_NOTIFICATION_ID);
        extras.putString(CALL_SID_KEY, callSid);
        extras.putString(NOTIFICATION_TYPE, ACTION_HANGUP_CALL);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, VOICE_CHANNEL)
                .setContentTitle("Call in progress")
                .setContentText(sanatizeCallerName(caller))
                .setSmallIcon(R.drawable.ic_call_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setExtras(extras)
                .setContentIntent(activityPendingIntent);

        notification.addAction(0, "HANG UP", pendingHangupIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // Create notifications channel (required for API > 25)
        initCallNotificationsChannel(notificationManager, false);
        notificationManager.notify(HANGUP_NOTIFICATION_ID, notification.build());
    }

    public void removeIncomingCallNotification(ReactApplicationContext context,
                                               CancelledCallInvite callInvite,
                                               int notificationId) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "removeIncomingCallNotification");
        }
        if (context == null) {
            Log.e(TAG, "Context is null");
            return;
        }
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (callInvite != null) {
                /*
                 * If the incoming call message was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    String notificationType = notification.extras.getString(NOTIFICATION_TYPE);
                    if (callInvite.getCallSid().equals(notification.extras.getString(CALL_SID_KEY)) &&
                            notificationType != null && notificationType.equals(ACTION_INCOMING_CALL)) {
                        notificationManager.cancel(notification.extras.getInt(INCOMING_CALL_NOTIFICATION_ID));
                    }
                }
            } else if (notificationId != 0) {
                notificationManager.cancel(notificationId);
            }
        } else {
            if (notificationId != 0) {
                notificationManager.cancel(notificationId);
            } else if (callInvite != null) {
                String notificationKey = INCOMING_NOTIFICATION_PREFIX+callInvite.getCallSid();
                if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                    notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
                    notificationManager.cancel(notificationId);
                    TwilioVoiceModule.callNotificationMap.remove(notificationKey);
                }
            }
        }
    }

    public void removeHangupNotification(ReactApplicationContext context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(HANGUP_NOTIFICATION_ID);
    }
}
