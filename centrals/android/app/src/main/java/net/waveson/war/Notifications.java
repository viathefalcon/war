/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;

import android.os.Build;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.annotation.TargetApi;

import android.support.annotation.NonNull;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import android.support.v4.content.ContextCompat;

class Notifications {

    static final int FLAG_NOISE = 0x00000001;
    static final int FLAG_RETRY = 0x00000002;
    static final int FLAG_IMPORTANT = 0x00000004;

    // Gives the singleton instance
    private static Notifications instance = null;

    // Gives the notification channel identifiers
    private static final String channel1Id = "warc1";
    private static final String channel5Id = "warc5";

    // Gives the notification channels
    private NotificationChannel notificationChannel1 = null;
    private NotificationChannel notificationChannel5 = null;

    // Gives the notification
    private Notification notification = null;

    // Gives the notification identifier
    private int notificationId = -1;

    // Gives the large icon for notifications
    private Bitmap largeIcon = null;

    /**
     * Initialises an instance
     */
    private Notifications() {

        Random random = new Random( );
        this.notificationId = random.nextInt( 100000 );
    }

    /**
     * @return the singleton instance.
     */
    static Notifications getInstance() {

        if (instance == null){
            instance = new Notifications( );
        }
        return instance;
    }

    void startServiceWithNotification(Service service) {

        if (largeIcon == null){
            largeIcon = BitmapFactory.decodeResource(
                service.getResources( ),
                R.drawable.icn_war
            );
        }
        if (isPreOreo( )){
            startServiceWithNotificationPreOreo( service );
        }else{
            startServiceWithNotificationOreo( service );
        }
    }

    void updateNotification(Service service, String content, int flags) {

        if (isPreOreo( )){
            updateNotificationPreOreo( service, content, flags );
        }else{
            updateNotificationOreo( service, content, flags );
        }
    }

    private NotificationCompat.Action getAction(Service service, String action, int title) {

        Intent intent = new Intent( service, BroadcastReceiver.class );
        intent.setAction( action );
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            service,
            (int) System.currentTimeMillis( ),
            intent,
            0
        );
        return new NotificationCompat.Action(
            0,
            service.getResources( ).getString( title ),
            pendingIntent
        );
    }

    private Notification buildNotification(Service service,
                                           String content,
                                           String channelId,
                                           int flags) {

        Intent settingsIntent = new Intent( service, BroadcastReceiver.class );
        settingsIntent.setAction( BroadcastReceiver.SETTINGS_ACTION );
        PendingIntent contentIntent = PendingIntent.getBroadcast(
            service,
            (int) System.currentTimeMillis( ),
            settingsIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        );
        NotificationCompat.Builder notification = new NotificationCompat.Builder( service, channelId )
            .setContentTitle( getNotificationTitle( service ) )
            .setContentText( content )
            .setSmallIcon( R.drawable.ic_status_notification )
            .setColor( ContextCompat.getColor( service, R.color.colorPrimaryDark ) )
            .setLargeIcon( largeIcon )
            .setContentIntent( contentIntent )
            .setOngoing( true )
            .setStyle( new NotificationCompat.BigTextStyle( ).bigText( content ) )
            .setVisibility( Notification.VISIBILITY_PUBLIC );

        // Fun with flags, etc..
        int nFlags = 0;
        if ((flags & FLAG_NOISE) == FLAG_NOISE){
            nFlags |= Notification.DEFAULT_SOUND;
        }
        if (isPreOreo( )){
            if ((flags & FLAG_IMPORTANT) == FLAG_IMPORTANT){
                notification.setPriority( NotificationCompat.PRIORITY_MAX );
                nFlags |= Notification.DEFAULT_VIBRATE;
            }
        }
        notification.setDefaults( nFlags );

        // Finally (!) gin up, add the actions
        if ((flags & FLAG_RETRY) == FLAG_RETRY){
            notification.addAction(
                getAction(
                    service,
                    BroadcastReceiver.RETRY_ACTION,
                    R.string.notification_title_retry
                )
            );
        }
        notification.addAction(
            getAction(
                service,
                BroadcastReceiver.STOP_ACTION,
                R.string.notification_title_stop
            )
        );
        return notification.build( );
    }

    @TargetApi(25)
    private Notification buildNotificationPreOreo(Service service, String content, int flags) {
        return buildNotification( service, content, null, flags );
    }

    @TargetApi(26)
    private NotificationChannel createNotificationChannel(Context context,
                                                          String identifier,
                                                          int name,
                                                          int importance) {

        NotificationChannel notificationChannel = new NotificationChannel(
                identifier,
                context.getString( name ),
                importance
        );
        notificationChannel.enableLights( false );
        notificationChannel.enableVibration( false );
        notificationChannel.setShowBadge( false );
        NotificationManager notificationManager =
            (NotificationManager) context.getSystemService( Context.NOTIFICATION_SERVICE );
        notificationManager.createNotificationChannel( notificationChannel );
        return notificationChannel;
    }

    @TargetApi(26)
    private Notification buildNotificationOreo(Service service, String content, int flags) {

        // Create the notification channels, if we haven't already
        if (notificationChannel1 == null){
            notificationChannel1 = createNotificationChannel(
                service,
                channel1Id,
                R.string.notification_channel_one,
                NotificationManager.IMPORTANCE_HIGH
            );
        }
        if (notificationChannel5 == null){
            notificationChannel5 = createNotificationChannel(
                service,
                channel5Id,
                R.string.notification_channel_five,
                NotificationManager.IMPORTANCE_LOW
            );
        }
        final String channelId = ((flags & FLAG_IMPORTANT) == FLAG_IMPORTANT)
            ? channel1Id
            : channel5Id;
        return buildNotification( service, content, channelId, flags );
    }

    @TargetApi(25)
    private void updateNotificationPreOreo(Service service, String content, int flags) {

        Notification notification = buildNotificationPreOreo( service, content, flags );
        NotificationManagerCompat
                .from( service )
                .notify( notificationId, notification );
    }

    @TargetApi(26)
    private void updateNotificationOreo(Service service, String content, int flags) {

        Notification notification = buildNotificationOreo( service, content, flags );
        NotificationManagerCompat
                .from( service )
                .notify( notificationId, notification );
    }

    @TargetApi(25)
    private void startServiceWithNotificationPreOreo(Service service) {

        Notification notification = buildNotificationPreOreo(
                service,
                getNotificationContent( service ),
                0
        );
        service.startForeground( notificationId, notification );
    }

    @TargetApi(26)
    private void startServiceWithNotificationOreo(Service service) {

        Notification notification = buildNotificationOreo(
                service,
                getNotificationContent( service ),
                0
        );
        service.startForeground( notificationId, notification );
    }

    @NonNull
    private static String getNotificationContent(Context context) {
        return context.getString( R.string.notification_content_running);
    }

    @NonNull
    private static String getNotificationTitle(Context context) {
        return context.getString( R.string.app_name );
    }

    private static boolean isPreOreo() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
    }
}
