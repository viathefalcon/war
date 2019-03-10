/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.os.Bundle;

import android.util.Log;

import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;

import android.support.v7.app.AppCompatActivity;

import android.support.v4.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName( );

    private BroadcastReceiver localBroadcastReceiver;

    private ServiceConnection serviceConnection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Register to listen to be told to finish up
        localBroadcastReceiver = new FinishBroadcastReceiver( this );
        IntentFilter intentFilter = new IntentFilter( );
        intentFilter.addAction( FinishBroadcastReceiver.ACTION );
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance( this );
        lbm.registerReceiver( localBroadcastReceiver, intentFilter );
    }

    @Override
    protected void onStart(){

        super.onStart( );

        // If the prerequisites are met, then just bind directly to the service and start it
        Prerequisites prerequisites = new Prerequisites( this );
        if ((prerequisites.getBluetoothStatus( ) == Prerequisites.BluetoothStatus.BLUETOOTH_ENABLED)
            && (prerequisites.getPermissionsDenied( ).isEmpty( ))){
            Log.d( TAG, "Bluetooth enabled and permissions already granted; starting service.." );
            serviceConnection = new StartRemoteControlServiceConnection( );
            Intent intent = new Intent( this, RemoteControlService.class );
            bindService( intent, serviceConnection, Context.BIND_AUTO_CREATE );
        }else{
            Log.d( TAG, "Starting splash activity.." );

            // Throw up the splash activity, and go away ourselves
            Intent intent = new Intent( this, SplashActivity.class );
            intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK );
            startActivity( intent );
            finish( );
        }
    }

    @Override
    protected void onStop() {

        Log.d( TAG, "MainActivity#onStop()" );
        if (serviceConnection != null){
            unbindService( serviceConnection );
        }
        super.onStop( );
    }

    @Override
    protected void onDestroy() {

        Log.d( TAG, "MainActivity#onDestroy()" );
        LocalBroadcastManager.getInstance( this )
                .unregisterReceiver( localBroadcastReceiver );
        super.onDestroy( );
    }
}
