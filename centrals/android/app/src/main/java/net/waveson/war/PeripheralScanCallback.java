/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.List;

import android.util.Log;

import android.bluetooth.BluetoothDevice;

import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanCallback;

class PeripheralScanCallback extends ScanCallback {

    private RemoteControlService service;

    protected void onPeripheralScanned(ScanResult scanResult) {

        if (service == null){
            return;
        }
        service.onScanSucceeded( scanResult.getDevice( ) );
    }

    public PeripheralScanCallback(RemoteControlService service) {
        setService( service );
    }

    public void setService(RemoteControlService service) {
        this.service = service;
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        onPeripheralScanned( result );
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {

        for (ScanResult result : results){
            onPeripheralScanned( result );
        }
    }

    @Override
    public void onScanFailed(int errorCode) {
        service.onScanFailed( errorCode );
    }
}

class BondedPeripheralScanCallback extends PeripheralScanCallback {

    private static final String TAG = BondedPeripheralScanCallback.class.getSimpleName( );

    @Override
    protected void onPeripheralScanned(ScanResult scanResult) {

        final BluetoothDevice device = scanResult.getDevice( );
        if (device.getBondState() == BluetoothDevice.BOND_NONE){
            final String name = device.getName( );
            final String message = (name == null || name.isEmpty( ))
                ? "Unknown device is not bonded"
                : name + " is not bonded";
            Log.i( TAG, message );
            return;
        }
        super.onPeripheralScanned( scanResult );
    }

    public BondedPeripheralScanCallback(RemoteControlService service) {
        super( service );
    }
}
