/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.Collections;

import java.lang.ref.WeakReference;

import android.os.Build;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;

import android.app.Service;

import android.util.Log;

import android.view.KeyEvent;

import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;

import android.content.pm.PackageManager;

import android.preference.PreferenceManager;

import android.media.AudioManager;

import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeScanner;

import android.support.annotation.Nullable;

import android.support.v4.content.LocalBroadcastManager;

public class RemoteControlService extends Service implements RemoteControl, SubscriptionManager.Callback {

    private static boolean oneplus = false;
    static {
        String[] strings = new String[]{
            Build.BRAND,
            Build.MANUFACTURER
        };
        for (String string : strings){
            final boolean match = string.toLowerCase( ).contains( "oneplus" );
            if (match){
                oneplus = true;
                break;
            }
        }
    }

    private static final String TAG = RemoteControlService.class.getSimpleName();

    public class LocalBinder extends Binder {
        RemoteControlService getService() {
            return RemoteControlService.this;
        }
    }
    private final IBinder binder = new LocalBinder();

    // Gives the instance id
    private int startId;

    // Indicates whether the service has been started, or not
    // and whether it is stopping, or not
    private boolean started = false, failed = false;
    private int stopping = 0;

    // Gives the number of times we've re-tried subscribing to the same remote control
    private int retries;

    private Preferences preferences;

    private static class RemoteControlHandler extends Handler {

        private final WeakReference<RemoteControlService> serviceWeakReference;

        RemoteControlHandler(RemoteControlService service) {
            this.serviceWeakReference = new WeakReference<>( service );
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case NOTIFICATION_MSG:
                {
                    RemoteControlService service = serviceWeakReference.get( );
                    if (service == null){
                        break;
                    }
                    service.onNotification( msg.arg1 );
                }
                break;

                case SUBSCRIPTION_MSG:
                {
                    RemoteControlService service = serviceWeakReference.get( );
                    if (service == null){
                        break;
                    }
                    service.onSubscriptionChanged( (msg.arg1 == IS_SUBSCRIBED) );
                }
                break;

                default:
                    super.handleMessage( msg );
                    break;
            }
        }
    }
    private RemoteControlHandler handler;
    private RemoteControlDispatcher dispatcher;

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothDevice bluetoothDevice = null;

    private BluetoothLeScanner bluetoothLeScanner = null;
    private PeripheralScanCallback scanCallback = null;

    private BluetoothGatt bluetoothGatt = null;
    private SubscriptionManager subscriptionManager = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind( intent );
    }

    @Override
    public void onCreate() {

        super.onCreate();

        // Hook up the preferences
        preferences = new Preferences( );
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences( this );
        preferences.init( shared );
        shared.registerOnSharedPreferenceChangeListener( preferences );

        // Create the handler and dispatcher
        handler = new RemoteControlHandler( this );
        dispatcher = new RemoteControlDispatcher( this, handler );
    }

    @Override
    public void onDestroy() {

        PreferenceManager.getDefaultSharedPreferences( this )
            .unregisterOnSharedPreferenceChangeListener( preferences );
        Log.d( TAG, "Destroying Service.." );
        super.onDestroy( );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        final String action = intent.getAction( );
        if (RemoteControl.START_ACTION.equals( action )){
            this.startId = startId;
            this.stopping = 0;

            // Throw up the notification
            Notifications notifications = Notifications.getInstance( );
            notifications.startServiceWithNotification( this );

            // Start scanning
            startScan( );
        }
        return START_NOT_STICKY;
    }

    @Override
    public void startIfNotStarted() {

        if (!started) {
            Intent intent = new Intent( this, getClass( ) );
            intent.setAction( RemoteControl.START_ACTION );
            startService( intent );

            // Set the flag to indicate that the service has been started
            started = true;
        }

        // Signal the activity that it can finish
        handler.postDelayed( new Runnable( ) {
            @Override
            public void run() {
                dismissSplash( );
            }
        }, 5000 ); // TODO: externalise this..?
    }

    @Override
    public void stopIfStarted() {

        final int i = stopping++;
        Log.d( TAG, "stopIfStarted( " + stopping + " )" );
        switch (i) {
            case 0:
                stopScan( );
                unsubscribe( );
                break;

            default:
                unhook( );
                onBluetoothUnhooked( );
                break;
        }
    }

    @Override
    public void retry() {

        // Fall-back (older) way..
        if (bluetoothGatt == null || subscriptionManager == null){
            if (subscriptionManager != null){
                subscriptionManager.setCallback( null );
                subscriptionManager.unsubscribeFromNotifications( bluetoothGatt, true );
            }
            unhook( );

            if (bluetoothDevice == null){
                startScan( );
            }else{
                subscribe( );
            }
            return;
        }

        // Update the notification
        retries += 1;
        final String name = (bluetoothDevice == null)
            ? null
            : bluetoothDevice.getName( );
        final String content = (name == null || name.isEmpty( ))
            ? getString( R.string.notification_content_retry_2, retries )
            : getString( R.string.notification_content_retry_1, name, retries );
        Log.d( TAG, content );
        Notifications.getInstance( )
            .updateNotification( this, content, 0 );

        // Newer way: make a best effort to silently clean up and then re-connect
        // after a short delay
        subscriptionManager.setCallback( null );
        subscriptionManager.unsubscribeFromNotifications( bluetoothGatt, true );
        subscriptionManager.reset( this );
        dispatcher.dispatch(
            new Runnable( ) {
                @Override
                public void run() {
                    if (bluetoothGatt.connect( )) {
                        Log.i( TAG, "Reconnected?" );
                    }else{
                        Log.i( TAG, "Re-subscribing.." );
                        subscribe( );
                    }
                }
            },
            preferences.getRetryInterval( )
        );
    }

    private void onBluetoothUnhooked() {

        // Stop ourselves
        Log.d( TAG, "onBluetoothUnhooked( );" );
        stopForeground( true );
        stopSelf( startId );

        // Signal the dispatch service to stop
        LocalBroadcastManager.getInstance( this )
            .sendBroadcast( new Intent( DispatchService.STOP_ACTION ) );
    }

    void startScan() {

        // Look for an early out
        if (getPackageManager( ).hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE )){
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService( Context.BLUETOOTH_SERVICE );
            if (bluetoothManager != null) {
                bluetoothAdapter = bluetoothManager.getAdapter( );
            }
        }else{
            bluetoothAdapter = null;
        }
        if (bluetoothAdapter == null){
            Log.w( TAG, "Can't scan as have no Bluetooth adapter" );
            return;
        }

        // Cancel any active scan
        stopScan( );
        scanCallback = preferences.isBondedOnly( )
            ? new BondedPeripheralScanCallback( this )
            : new PeripheralScanCallback( this );
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner( );

        // Update the notification
        Notifications.getInstance( )
            .updateNotification(
                this,
                getString( R.string.notification_content_scanning ),
                0
            );

        // Go do the scan
        final ScanSettings.Builder scanSettings = new ScanSettings.Builder( );
        scanSettings.setScanMode( ScanSettings.SCAN_MODE_LOW_LATENCY );
//		scanSettings.setCallbackType( ScanSettings.CALLBACK_TYPE_FIRST_MATCH );
        final ScanFilter.Builder scanFilter = new ScanFilter.Builder( );
        scanFilter.setServiceUuid( new ParcelUuid( SERVICE_UUID ) );
        bluetoothLeScanner.startScan(
            Collections.singletonList( scanFilter.build( ) ),
            scanSettings.build( ),
            scanCallback
        );
    }

    void stopScan() {

        if (scanCallback != null){
            scanCallback.setService( null );
        }
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan( scanCallback );
        }
        bluetoothLeScanner = null;
    }

    void onScanSucceeded(BluetoothDevice bluetoothDevice) {

        stopScan( );
        this.bluetoothDevice = bluetoothDevice;
        subscribe( );
    }

    void onScanFailed(int errorCode) {

        Log.w(TAG, "Bluetooth scan failed with code: " + errorCode);

        // Update the notification
        final String message = getString(
            R.string.error_scan_failed,
            errorCode
        );
        Notifications.getInstance( )
            .updateNotification(
                this,
                message,
                (Notifications.FLAG_IMPORTANT | Notifications.FLAG_NOISE)
            );
    }

    void subscribe() {

        if (bluetoothDevice == null){
            final String message = getString(
                R.string.no_device_scanned
            );
            Notifications.getInstance( )
                .updateNotification(
                    this,
                    message,
                    (Notifications.FLAG_IMPORTANT | Notifications.FLAG_NOISE)
                );
            return;
        }

        final String name = bluetoothDevice.getName( );
        final String content = (name == null || name.isEmpty( ))
            ? getString( R.string.notification_content_subscribing_2 )
            : getString( R.string.notification_content_subscribing_1, name );
        Notifications.getInstance( )
            .updateNotification( this, content, 0 );

        retries = 0;
        subscriptionManager = new SubscriptionManager(
            dispatcher,
            bluetoothAdapter.getName( ),
            dispatcher,
            preferences.getGattDelay( )
        );
        dispatcher.dispatch( new Runnable( ) { // c.f. https://stackoverflow.com/a/23478737
            @Override
            public void run() {
                bluetoothGatt = bluetoothDevice.connectGatt(
                    RemoteControlService.this,
                    false,
                    subscriptionManager,
                    BluetoothDevice.TRANSPORT_LE
                );
            }
        } );
    }

    void unsubscribe() {

        if (subscriptionManager == null){
            onSubscriptionChanged( false );
            return;
        }

        // If the connection failed, we want to make a best-effort to unsubscribe cleanly
        // but neither rely on, nor trip over, callbacks which may or may not come
        // So, we unset ourselves as a callback site, forcibly unsubscribe and then
        // call ourselves back as if normal
        if (failed){
            subscriptionManager.setCallback( null );
        }
        subscriptionManager.unsubscribeFromNotifications( bluetoothGatt, failed );
        if (failed){
            dispatcher.onSubscriptionChanged( false );
        }
    }

    @Override
    public void onConnectionError(int string, Object... etc) {

        // Retry?
        if (preferences.isAutoRetry( )){
            retry( );
            return;
        }

        // Raise the flag
        failed = true;

        // Format the message content for the notification
        String name = (bluetoothDevice == null)
            ? null
            : bluetoothDevice.getName( );
        if (name == null || name.isEmpty( )){
            name = getString( R.string.unknown_device );
        }
        Object[] args = new Object[etc.length+1];
        args[0] = name;
        if (etc.length > 0){
            System.arraycopy( etc, 0, args, 1, etc.length );
        }
        final String message = getString( string, args );
        Log.w( TAG, "onConnectionError(): " + message );

        // Update the notification
        final int flags =
            Notifications.FLAG_IMPORTANT | Notifications.FLAG_NOISE | Notifications.FLAG_RETRY;
        Notifications.getInstance( )
            .updateNotification(this, message, flags );
    }

    @Override
    public void onError(int string, Object... args) {

        // Raise the flag
        failed = true;

        // Get the string
        final String message = getString(
            string,
            args
        );

        // Update the notification
        Notifications.getInstance( )
            .updateNotification(
                this,
                message,
                (Notifications.FLAG_IMPORTANT | Notifications.FLAG_NOISE)
            );
    }

    private boolean isStopping() {
        return (stopping > 0);
    }

    private void unhook() {

        if (subscriptionManager != null){
            subscriptionManager.setCallback( null );
        }
        if (bluetoothGatt != null){
            // BluetoothGatt#disconnect() is redundant, or worse?
            // c.f. https://android.jlelse.eu/lessons-for-first-time-android-bluetooth-le-developers-i-learned-the-hard-way-fee07646624
            // bluetoothGatt.disconnect( );
            bluetoothGatt.close( );
        }
        subscriptionManager = null;
        bluetoothGatt = null;
    }

    private void dismissSplash() {
        LocalBroadcastManager.getInstance( this )
            .sendBroadcast( new Intent( SplashActivity.FINISH_ACTION ) );
    }

    @Override
    public void onSubscriptionChanged(boolean subscribed) {

        final String name = (bluetoothDevice == null)
                ? null
                : bluetoothDevice.getName( );
        if (!subscribed){
            unhook( );
        }

        String content = null;
        if (subscribed){
            dismissSplash( );

            if (name == null || name.isEmpty( )){
                content = (retries == 0)
                    ? getString( R.string.notification_content_subscribed_unpaired )
                    : getString(
                        R.string.notification_content_subscribed_unpaired_retried,
                        retries
                    );
            }else{
                content = (retries == 0)
                    ? getString( R.string.notification_content_subscribed, name )
                    : getString(
                        R.string.notification_content_subscribed_retried,
                        name,
                        retries
                    );
            }
        }else{
            if (isStopping( )){
                onBluetoothUnhooked( );
            }else{
                content = getString( R.string.notification_content_running);
            }
        }
        if (content == null){
            return;
        }
        final int flags = (subscribed ? Notifications.FLAG_IMPORTANT : 0);
        Notifications.getInstance( )
            .updateNotification( this, content, flags );
    }

    private void toggleRingerMode(AudioManager audioManager) {

        // If we think we're on a OnePlus device, then do nothing
        // because it seems to interfere w/the in-built hardware alert slider?
        // (It confuses me, regardless..)
        if (oneplus){
            Log.d( TAG, "On a OnePlus device.." );
            return;
        }

        // If the ringer mode is vibrate, make it normal;
        // if it is normal, switch to vibrate
        //
        // If the ringer mode is anything else (i.e. silent),
        // then we ignore the command because I'm only interested
        // in being able to flip between normal and vibrate and
        // interacting with 'silent' involves obtaining additional
        // permissions (ACCESS_NOTIFICATION_POLICY)
        switch (audioManager.getRingerMode( )){
            case AudioManager.RINGER_MODE_NORMAL:
                audioManager.setRingerMode(
                    AudioManager.RINGER_MODE_VIBRATE
                );
                break;

            case AudioManager.RINGER_MODE_VIBRATE:
                audioManager.setRingerMode(
                    AudioManager.RINGER_MODE_NORMAL
                );
                break;
        }
    }

    @Override
    public void onNotification(int value) {

        // Look for an early out
        if ((value & STOP) == STOP){
            Log.d( TAG, "'STOP' rec'v'd" );
            stopIfStarted( );
            return;
        }

        try {
            AudioManager audioManager = this.getSystemService( AudioManager.class );
            final boolean apply = ((value & TWO_STEP) == TWO_STEP)
                ? ((value & ACTION_DOWN) == 0)
                : true;
            if (apply){
                final int stream = AudioManager.STREAM_MUSIC;
                final boolean muted = audioManager.isStreamMute( stream );
                if ((value & MUTE) == MUTE){
                    if ((value & TOGGLE_RINGER_MODE) == TOGGLE_RINGER_MODE){
                        toggleRingerMode( audioManager );
                    }else{
                        audioManager.adjustStreamVolume(
                            stream,
                            (muted ? AudioManager.ADJUST_UNMUTE : AudioManager.ADJUST_MUTE),
                            AudioManager.FLAG_SHOW_UI
                        );
                    }
                }else if (!muted){
                    final int volume_mask = VOLUME_UP | VOLUME_DOWN;
                    if ((value & volume_mask) != 0){
                        // If it's not up, it's down..
                        final int direction = ((value & VOLUME_UP) == VOLUME_UP)
                            ? AudioManager.ADJUST_RAISE
                            : AudioManager.ADJUST_LOWER;
                        audioManager.adjustStreamVolume(
                            stream,
                            direction,
                            AudioManager.FLAG_SHOW_UI
                        );
                    }
                }
            }

            int event = KeyEvent.KEYCODE_UNKNOWN;
            if ((value & PLAY_PAUSE) == PLAY_PAUSE){
                event = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            }else if ((value & BACK) == BACK){
                event = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            }else if ((value & FORWARD) == FORWARD){
                event = KeyEvent.KEYCODE_MEDIA_NEXT;
            }
            if (event == KeyEvent.KEYCODE_UNKNOWN){
                return;
            }
            Log.d( TAG, "KeyEvent code: " + event );

            if ((value & TWO_STEP) == TWO_STEP){
                final int action = ((value & ACTION_DOWN) == ACTION_DOWN)
                    ? KeyEvent.ACTION_DOWN
                    : KeyEvent.ACTION_UP;
                audioManager.dispatchMediaKeyEvent( new KeyEvent( action, event ) );
            }else{
                audioManager.dispatchMediaKeyEvent( new KeyEvent( KeyEvent.ACTION_DOWN, event ) );
                audioManager.dispatchMediaKeyEvent( new KeyEvent( KeyEvent.ACTION_UP, event ) );
            }
        }
        catch (Exception ex) {
            Log.w( TAG, "Exception whilst handling notification", ex );
        }
    }
}
