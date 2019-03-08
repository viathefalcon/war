/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.os.IBinder;
import android.os.Handler;

import android.app.Service;

import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;

import android.support.annotation.Nullable;

import android.support.v4.content.LocalBroadcastManager;

public class DispatchService extends Service {

    public static final int DISPATCH_STOP = 0x00000001;
    public static final int DISPATCH_RETRY = 0x00000002;

    public static final String DISPATCH_EXTRA = "net.waveson.war.dispatch.extra";

    public static final String STOP_ACTION = "net.waveson.war.stop.action";

    private static final String TAG = DispatchService.class.getSimpleName( );

    private BlockingQueue<Integer> dispatches;

    private Handler handler;

    private ServiceConnection serviceConnection = null;

    private BroadcastReceiver localBroadcastReceiver = null;

    private RemoteControlService remoteControlService = null;

    private void dispatchAll() {

        handler.post( new Runnable( ) {
            @Override
            public void run() {

                // Look for an early out
                if (remoteControlService == null){
                    Log.w( TAG, "remoteControlService is null! (" + dispatches.size( ) + ")" );
                    return;
                }
                while (!dispatches.isEmpty( )){
                    final Integer dispatch = dispatches.poll( );
                    if (dispatch == null){
                        Log.w( TAG, "Invalid null dispatch value" );
                    }
                    switch (dispatch) {
                        case DISPATCH_STOP:
                            remoteControlService.stopIfStarted( );
                            break;

                        case DISPATCH_RETRY:
                            remoteControlService.retry( );
                            break;

                        default:
                            Log.w( TAG, "Unrecognised dispatch value: " + dispatch );
                            break;
                    }
                }
            }
        } );
    }

    private void setRemoteControlService(RemoteControlService service) {
        this.remoteControlService = service;
        dispatchAll( );
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        super.onCreate( );
        dispatches = new LinkedBlockingDeque<>( );
        handler = new Handler( getMainLooper( ) );

        // Start listening for the instruction to stop
        localBroadcastReceiver = new BroadcastReceiver( ) {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (STOP_ACTION.equals( intent.getAction( ) )){
                    DispatchService.this.stopSelf( );
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter( );
        intentFilter.addAction( STOP_ACTION );
        LocalBroadcastManager.getInstance( this )
                .registerReceiver( localBroadcastReceiver, intentFilter );

        // Try and bind to the remote control service
        serviceConnection = new ServiceConnection( ) {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {

                // Capture a reference to the bound service
                RemoteControlService.LocalBinder binder = (RemoteControlService.LocalBinder) service;
                DispatchService.this.setRemoteControlService( binder.getService( ) );
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d( DispatchService.TAG, "onServiceDisconnected( " + name.toString( ) + " )" );
            }
        };
        final boolean boundServiceConnection = bindService(
                new Intent( this, RemoteControlService.class ),
                serviceConnection,
                Context.BIND_AUTO_CREATE
        );
        if (boundServiceConnection){
            return;
        }
        serviceConnection = null;
    }

    @Override
    public void onDestroy() {

        Log.d( TAG, "onDestroy( )" );
        if (serviceConnection != null){
            unbindService( serviceConnection );
        }
        LocalBroadcastManager.getInstance( this )
                .unregisterReceiver( localBroadcastReceiver );
        super.onDestroy( );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {

        final int dispatch = intent.getIntExtra( DISPATCH_EXTRA, 0 );
        dispatches.offer( dispatch );
        dispatchAll( );
        return START_NOT_STICKY;
    }
}
