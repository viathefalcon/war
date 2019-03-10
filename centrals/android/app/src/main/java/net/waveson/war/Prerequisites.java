/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
import java.util.List;
import java.util.ArrayList;

import android.Manifest;

import android.content.Context;

import android.content.pm.PackageManager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;

import android.support.v4.content.ContextCompat;

class Prerequisites {

    enum BluetoothStatus {
        BLUETOOTH_UNAVAILABLE,
        BLUETOOTH_DISABLED,
        BLUETOOTH_ENABLED
    };

    private BluetoothStatus bluetoothStatus;

    private List<String> permissionsDenied;

    Prerequisites(Context context) {

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService( Context.BLUETOOTH_SERVICE );
        BluetoothAdapter bluetoothAdapter = (bluetoothManager == null)
            ? null
            : bluetoothManager.getAdapter( );
        if (bluetoothAdapter == null){
            bluetoothStatus = BluetoothStatus.BLUETOOTH_UNAVAILABLE;
        }else {
            bluetoothStatus = bluetoothAdapter.isEnabled()
                ? BluetoothStatus.BLUETOOTH_ENABLED
                : BluetoothStatus.BLUETOOTH_DISABLED;
        }

        final String[] desiredPermissions = new String[] {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        };
        permissionsDenied = new ArrayList<String>( desiredPermissions.length );
        for (String permission : desiredPermissions){
            final int permissionCheck = ContextCompat.checkSelfPermission( context, permission );
            if (permissionCheck != PackageManager.PERMISSION_GRANTED){
                permissionsDenied.add( permission );
            }
        }
    }

    BluetoothStatus getBluetoothStatus() {
        return bluetoothStatus;
    }

    List<String> getPermissionsDenied() {
        return permissionsDenied;
    }
}
