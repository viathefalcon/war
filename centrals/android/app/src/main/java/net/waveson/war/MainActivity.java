/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.List;
import java.util.ArrayList;

import android.Manifest;

import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;

import android.content.Context;

import android.util.Log;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.ServiceConnection;
import android.content.ComponentName;

import android.content.pm.PackageManager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;

import android.support.v7.app.ActionBar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String FINISH_ACTION = "net.waveson.war.main.finish";

    private static final String LOG_TAG = MainActivity.class.getName( );

    private static final int ENABLE_BT_REQUEST = 4321;

    private BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver( ) {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d( MainActivity.LOG_TAG, "MainActivity#localBroadcastReceiver(" + intent.getAction( ) + ")" );
            if (FINISH_ACTION.equals( intent.getAction( ) )){
                MainActivity.this.finish( );
            }
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection( ) {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            // Tell the service to start if it hasn't, already
            RemoteControlService.LocalBinder binder = (RemoteControlService.LocalBinder) service;
            binder.getService( ).startIfNotStarted( );
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar( );
        if (actionBar != null){
            // Show the 'Up' button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled( true );
        }
        setContentView( R.layout.activity_main );

        // Register to listen for
        IntentFilter intentFilter = new IntentFilter( );
        intentFilter.addAction( FINISH_ACTION );
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance( this );
        lbm.registerReceiver( localBroadcastReceiver, intentFilter );
    }

    @Override
    protected void onStart(){

        super.onStart( );
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

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

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService( Context.BLUETOOTH_SERVICE );
        BluetoothAdapter bluetoothAdapter = (bluetoothManager == null)
            ? null
            : bluetoothManager.getAdapter( );
        if (bluetoothAdapter == null){
            // Just bail
            finish( );
        }else{
            if (bluetoothAdapter.isEnabled( )){
                return true;
            }

            // Ask the user to enable it
            Intent intent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( intent, ENABLE_BT_REQUEST );
        }
        return false;
    }

    private void requestPermissions() {

        // Obtain the required permissions, if we haven't already
        final String[] desiredPermissions = new String[] {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
        };
        List<String> permissionDenied = new ArrayList<String>( desiredPermissions.length );
        for (String permission : desiredPermissions){
            final int permissionCheck = ContextCompat.checkSelfPermission( this, permission );
            if (permissionCheck != PackageManager.PERMISSION_GRANTED){
                permissionDenied.add( permission );
            }
        }
        if (permissionDenied.isEmpty( )){
            onPermissionsGranted( );
        }else {
            String[] requestedPermissions = new String[permissionDenied.size()];
            for (int i = 0; i < permissionDenied.size( ); i++) {
                requestedPermissions[i] = permissionDenied.get( i );
            }
            ActivityCompat.requestPermissions(this, requestedPermissions, 0);
        }
    }

    private void onPermissionsGranted() {

        // Bind to the service
        Intent intent = new Intent( this, RemoteControlService.class );
        bindService( intent, serviceConnection, Context.BIND_AUTO_CREATE );
    }
}
