/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.List;

import android.os.Bundle;
import android.os.Handler;

import android.app.Activity;

import android.util.Log;

import android.view.View;

import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;

import android.content.pm.PackageManager;

import android.bluetooth.BluetoothAdapter;

import android.support.annotation.NonNull;

import android.support.v7.app.ActionBar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.support.v4.content.LocalBroadcastManager;

public class SplashActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String FINISH_ACTION = "net.waveson.war.splash.finish";

    private static final String LOG_TAG = SplashActivity.class.getName( );

    private static final int ENABLE_BT_REQUEST = 4321;

    private boolean dismissed = false;

    private Handler handler;

    private Prerequisites prerequisites;

    private BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver( ) {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (FINISH_ACTION.equals( intent.getAction( ) )){
                handler.postDelayed( new Runnable( ) {
                    @Override
                    public void run() {
                        SplashActivity.this.dismiss( );
                    }
                }, 5000 );
            }
        }
    };

    private ServiceConnection serviceConnection = new StartRemoteControlServiceConnection( );

    void dismiss() {

        if (dismissed){
            return;
        }
        dismissed = true;
        LocalBroadcastManager.getInstance( this )
            .unregisterReceiver( localBroadcastReceiver );
        finish( );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView( R.layout.activity_splash );
        ActionBar actionBar = getSupportActionBar( );
        if (actionBar != null){
            // Show the 'Up' button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled( true );
        }

        // Register to listen to be told to finish up
        handler = new Handler( getMainLooper( ) );
        IntentFilter intentFilter = new IntentFilter( );
        intentFilter.addAction( FINISH_ACTION );
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance( this );
        lbm.registerReceiver( localBroadcastReceiver, intentFilter );
    }

    @Override
    protected void onStart(){

        super.onStart( );
        prerequisites = new Prerequisites( this );
        if (checkBluetoothEnabled( )){
            requestPermissions( );
        }
    }

    @Override
    protected void onStop() {

        unbindService( serviceConnection );
        super.onStop( );
    }

    @Override
    protected void onDestroy() {

        LocalBroadcastManager.getInstance( this )
                .unregisterReceiver( localBroadcastReceiver );
        super.onDestroy( );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        int counter = 0, granted = 0;
        for (int grantResult : grantResults){
            if (grantResult == PackageManager.PERMISSION_GRANTED){
                granted++;
            }else{
                Log.w( LOG_TAG, "Permission not granted for " + permissions[counter]);
            }
            counter++;
        }
        if (granted == counter){
            onPermissionsGranted( );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case ENABLE_BT_REQUEST:
                if (resultCode == Activity.RESULT_OK){
                    requestPermissions( );
                }else{
                    // Just bail
                    finish( );
                }
                break;

            default:
                super.onActivityResult( requestCode, resultCode, data );
                break;
        }
    }

    private boolean checkBluetoothEnabled() {

        switch (prerequisites.getBluetoothStatus( )){
            case BLUETOOTH_ENABLED:
                return true;

            case BLUETOOTH_DISABLED:
            {
                // Ask the user to enable it
                Intent intent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
                startActivityForResult( intent, ENABLE_BT_REQUEST );
            }
                break;

            case BLUETOOTH_UNAVAILABLE:
                // Just bail
                finish( );
        }
        return false;
    }

    private void requestPermissions() {

        List<String> permissionsDenied = prerequisites.getPermissionsDenied( );
        if (permissionsDenied.isEmpty( )){
            onPermissionsGranted( );
        }else {
            String[] requestedPermissions = new String[permissionsDenied.size()];
            for (int i = 0; i < permissionsDenied.size( ); i++) {
                requestedPermissions[i] = permissionsDenied.get( i );
            }
            ActivityCompat.requestPermissions(this, requestedPermissions, 0);
        }
    }

    private void onPermissionsGranted() {

        // Bind to the service
        Intent intent = new Intent( this, RemoteControlService.class );
        bindService( intent, serviceConnection, Context.BIND_AUTO_CREATE );
    }

    public void dismissSplash(View view) {
        dismiss( );
    }
}
