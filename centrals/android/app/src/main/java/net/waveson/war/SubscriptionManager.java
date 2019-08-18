/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.nio.charset.StandardCharsets;

import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import android.util.Log;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

class SubscriptionManager extends BluetoothGattCallback {

    private static final String TAG = SubscriptionManager.class.getSimpleName( );

    private static Set<UUID> uuids = new HashSet<>();
    static {
        uuids.add( RemoteControl.NOTIFY_UUID );
    }

    private BlockingDeque<UUID> backlog = new LinkedBlockingDeque<>( );
    private BlockingDeque<UUID> subscribed = new LinkedBlockingDeque<>( );

    private int mtu = 20;
    private ChunkedUtf8StringBuffer deviceName;

    private final Dispatcher dispatcher;
    private long delay;

    interface Callback {
        void onConnectionError(int string, Object... etc);
        void onError(int string, Object... args);
        void onSubscriptionChanged(boolean subscribed);
        void onNotification(int value);
    }
    private Callback callback;

    private enum State { DISCONNECTED, CONNECTED, SUBSCRIBING, SUBSCRIBED, UNSUBSCRIBING, UNSUBSCRIBED }
    private State state = State.DISCONNECTED;

    private void setState(State state) {
        this.state = state;
    }

    private State getState() {
        return this.state;
    }

    SubscriptionManager(Callback callback,
                        String deviceName,
                        Dispatcher dispatcher,
                        long delay) {

        this.delay = delay;
        this.dispatcher = dispatcher;
        setCallback( callback );
        this.deviceName = new ChunkedUtf8StringBuffer( deviceName );
    }

    synchronized void setCallback(Callback callback) {
        this.callback = callback;
    }

    void reset(Callback callback) {
        setCallback( callback );
        setState( State.DISCONNECTED );
    }

    private synchronized void onConnectionError(int string, Object... etc) {

        if (callback == null){
            Log.i( TAG, "onConnectionError( " + string + " ) when callback == null" );
            return;
        }
        callback.onConnectionError( string, etc );
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {

        final State state = getState( );
        Log.d( TAG, "onConnectionStateChange( .., " + status + ", " + newState + " ): " + state );
        switch (state) {
            case DISCONNECTED:
                if (status == BluetoothGatt.GATT_SUCCESS){
                    if (newState == BluetoothProfile.STATE_CONNECTED){
                        dispatcher.dispatch( new Runnable( ) {
                            @Override
                            public void run() {
                                if (gatt.discoverServices( )) {
                                    setState( State.CONNECTED );
                                }else{
                                    // Failed to discover services
                                    onConnectionError( R.string.error_subscription_1);
                                }
                            }
                        }, this.delay );
                    }
                }else{
                    onConnectionError( R.string.error_subscription_2, status );
                }
                break;

            default:
            {
                final boolean ok = (status == BluetoothGatt.GATT_SUCCESS)
                        && (newState == BluetoothProfile.STATE_CONNECTED);
                if (!ok){
                    onConnectionError( R.string.error_unsub, status, newState );
                }
            }
            break;
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {

        Log.d( TAG, "onMtuChanged( .., " + mtu + ", " + status + " );" );
        if (status == BluetoothGatt.GATT_SUCCESS){
            this.mtu = mtu;
        }
        super.onMtuChanged( gatt, mtu, status );
    }

    private synchronized void onError(int string, int status) {
        
        if (callback == null){
            Log.i( TAG, "onError( " + string + ", " + status + " ) when callback == null" );
            return;
        }
        callback.onError( string, status );
    }
    
    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {

        backlog.clear( );
        subscribed.clear( );
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                // Subscribe for notifications from the service's characteristics, one at a time
                backlog.addAll( uuids );
                subscribeForNotifications( gatt );
                break;

            default:
                Log.w( TAG, "Error discovering services: " + status );
                onError(
                    R.string.error_services_discovered,
                    status
                );
                break;
        }
    }

    private synchronized void onError(int string) {
        
        if (callback == null){
            Log.i( TAG, "onError( " + string + " ) when callback == null" );
            return;
        }
        callback.onError( string );
    }
    
    private boolean toggleSubscription(BluetoothGatt gatt, UUID uuid, boolean enable) {

        // Get the service
        BluetoothGattService service = gatt.getService( RemoteControl.SERVICE_UUID );
        if (service == null){
            if (enable){
                onError( R.string.error_service_not_found );
            }
            Log.w( TAG, "W.A.R. service not found?" );
            return false;
        }

        // Get the characteristic
        BluetoothGattCharacteristic characteristic = service.getCharacteristic( uuid );
        if (characteristic == null){
            if (enable){
                onError( R.string.error_characteristic_not_found );
            }
            Log.w( TAG, "Failed to retrieve the push characteristic." );
            return false;
        }

        // For macOS-hosted peripherals? c.f. https://stackoverflow.com/a/41286992
        characteristic.setWriteType( BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT );

        // Enable/disable it
        if (!gatt.setCharacteristicNotification( characteristic, enable )) {
            if (enable){
                onError( R.string.error_characteristic_notification_1 );
            }
            Log.w( TAG, "Failed to (un)subscribe to/from the push characteristic notification (1)." );
            return false;
        }

        final UUID configDescriptor = UUID.fromString( "00002902-0000-1000-8000-00805f9b34fb" );
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor( configDescriptor );
        descriptor.setValue( enable
                ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE );
        if (!gatt.writeDescriptor( descriptor )){
            if (enable){
                onError( R.string.error_characteristic_notification_2 );
            }
            Log.w( TAG, "Failed to (un)subscribe to/from the push characteristic notification (2)." );
            return false;
        }
        return true;
    }

    private void doSendDeviceName(BluetoothGatt gatt) {

        // Look for an early out
        if (!deviceName.hasMore( )){
            return;
        }
        BluetoothGattService service = gatt.getService( RemoteControl.SERVICE_UUID );
        if (service == null){
            Log.w( TAG, "W.A.R. service not found?" );
            onError( R.string.error_send_devicename_failed );
            return;
        }
        for (BluetoothGattCharacteristic c : service.getCharacteristics( )){
            Log.d( TAG, "Found: " + c.getUuid( ) );
        }

        // Get the characteristic
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(
            RemoteControl.NAME_UUID
        );
        if (characteristic == null){
            Log.w( TAG, "Failed to retrieve the name characteristic." );
            onError( R.string.error_send_devicename_failed );
            return;
        }
        if ((characteristic.getProperties( ) & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE){
            Log.i( TAG, "No write response expected?" );
        }
        characteristic.setWriteType( BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT );

        // Get the next chunk and write out
        byte[] chunk = deviceName.getNextChunk( mtu );
        boolean result = characteristic.setValue( chunk );
        if (result){
            result = gatt.writeCharacteristic( characteristic );
            if (!result){
                Log.w( TAG, "Failed to write out device name?" );
            }
        }else{
            Log.w( TAG, "Failed to set value of name characteristic." );
        }
        if (!result){
            deviceName.rewind( chunk.length );
            onError( R.string.error_send_devicename_failed );
        }
    }

    private void sendDeviceName(final BluetoothGatt gatt) {

        dispatcher.dispatch( new Runnable( ) {
            @Override
            public void run() {
                doSendDeviceName( gatt );
            }
        }, this.delay );
    }

    private synchronized void onSubscriptionChanged(boolean subscribed) {

        if (callback == null){
            Log.i( TAG, "onSubscriptionChanged( " + subscribed + " ) when callback == null" );
            return;
        }
        callback.onSubscriptionChanged( subscribed );
    }

    private void subscribeForNotifications(final BluetoothGatt gatt) {

        // Look for an early out
        if (backlog.isEmpty( )){
            setState( State.SUBSCRIBED );

            // Notify the service
            onSubscriptionChanged( true );

            // Next, try and send our friendly name to the other side
            // For display purposes
            deviceName.reset( );
            sendDeviceName( gatt );
            return;
        }
        setState( State.SUBSCRIBING );

        final UUID uuid = backlog.pop( );
        dispatcher.dispatch( new Runnable( ) {
            @Override
            public void run() {
                // Toggle the subscription for the next characteristic 'on'
                toggleSubscription( gatt, uuid, true );
            }
        }, this.delay );
    }

    void unsubscribeFromNotifications(final BluetoothGatt gatt, final boolean force) {

        if (gatt == null){
            Log.w( TAG, "gatt == null" );
            return;
        }
        if (subscribed.isEmpty( )){
            setState( State.UNSUBSCRIBED );

            onSubscriptionChanged( false );
            return;
        }
        setState( State.UNSUBSCRIBING );

        // Toggle the subscription for the next characteristic 'off'
        final UUID uuid = subscribed.pop( );
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d( TAG, "Unsubscribing from " + uuid.toString( ) );
                if (!toggleSubscription( gatt, uuid, false ) || force){
                    unsubscribeFromNotifications( gatt, force );
                }
            }
        };
        if (force){
            runnable.run( );
        }else{
            dispatcher.dispatch( runnable, delay );
        }
    }

    @Override
    public void onCharacteristicWrite(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        final UUID uuid = characteristic.getUuid( );
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                if (RemoteControl.NAME_UUID.equals( uuid )) {
                    sendDeviceName( gatt );
                }
                break;

            default:
                Log.w( TAG, "Failed to write " + uuid );
                break;
        }
    }

    @Override
    public void onDescriptorWrite(final BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        Log.d( TAG, "onDescriptorWrite( " + descriptor.getCharacteristic( ).getUuid( ) + " )" );
        if (status == BluetoothGatt.GATT_SUCCESS){
            UUID uuid = descriptor.getCharacteristic( ).getUuid( );
            if (uuids.contains( uuid )){
                switch (getState( )){
                    case SUBSCRIBING:
                        Log.d( TAG, "Subscribed to: " + uuid.toString( ) );
                        subscribed.push( uuid );
                        break;

                    case UNSUBSCRIBING:
                        Log.d( TAG, "Unsubscribed from: " + uuid.toString( ) );
                        break;

                    default:
                        // Do nothing
                        break;
                }
            }
        }
        switch (getState( )){
            case SUBSCRIBING:
                subscribeForNotifications( gatt );
                break;

            case UNSUBSCRIBING:
                unsubscribeFromNotifications( gatt, false );
                break;

            default:
                // Do nothing
                break;
        }
    }

    private synchronized void onNotification(final int value) {

        if (callback == null){
            return;
        }
        callback.onNotification( value );
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        final UUID uuid = characteristic.getUuid( );
        if (uuid.equals( RemoteControl.NOTIFY_UUID )){
            // Get the value
            final Integer value = characteristic.getIntValue( BluetoothGattCharacteristic.FORMAT_UINT8, 0 );
            //Log.d( TAG, "onCharacteristicChanged( " + uuid + ") with value: " + value );
            onNotification( value.intValue( ) );
        }else{
            Log.w( TAG, "Received notification for unknown characteristic: " + uuid );
        }
    }
}

class ChunkedUtf8StringBuffer {

    private int offset = 0;

    private final byte[] bytes;

    ChunkedUtf8StringBuffer(String arg) {
        bytes = arg.getBytes( StandardCharsets.UTF_8 );
    }

    void reset() {
        this.offset = 0;
    }

    byte[] getNextChunk(int mtu) {

        final int delta = bytes.length - offset;
        final int prefix = (mtu > delta) ? 0x00000080 : 0;
        final int size = Math.min( delta+1, mtu );
        final int length = size-1;
        byte[] chunk = new byte[size];
        chunk[0] = (byte) prefix;
        System.arraycopy( bytes, offset, chunk, 1, length );

        // Update the state
        offset += length;
        return chunk;
    }

    boolean hasMore() {
        return (offset < bytes.length);
    }

    void rewind(int size) {
        offset -= (size - 1);
    }
}
