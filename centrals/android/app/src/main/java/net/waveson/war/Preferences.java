/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.util.Log;

import android.content.SharedPreferences;

class Preferences implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = Preferences.class.getSimpleName( );

    private boolean bondedOnly = false;
    private int maxVolumeSteps = 16;
    private long gattDelay = 128L;
    private boolean autoRetry = false;
    private long retryInterval = 1024L;

    static final String GATT_DELAY_KEY = "gatt_delay";
    static final String BONDED_ONLY_KEY = "bonded_only";
    static final String VOLUME_STEPS_KEY = "volume_steps";
    static final String AUTO_RETRY_KEY = "auto_retry";
    static final String RETRY_INTERVAL_KEY = "retry_interval";

    private long init(SharedPreferences shared, String key, long fallback) {

        long result = fallback;
        try {
            if (shared.contains( key )){
                result = Long.parseLong(
                    shared.getString( key, Long.toString( result ) )
                );
                Log.d( TAG, "Read " + result + " for '" + key + "'" );
            }else{
                shared.edit( )
                    .putString( key, Long.toString( result ) )
                    .apply( );
            }
        }
        catch (Exception ex) {
            Log.e( TAG,"Exception whilst retrieving/parsing '" + key + "'", ex );
        }
        return result;
    }

    void init(SharedPreferences shared) {

        try {
            if (shared.contains( BONDED_ONLY_KEY )){
                bondedOnly = shared.getBoolean( BONDED_ONLY_KEY, false );
            }else{
                shared.edit( )
                    .putBoolean( BONDED_ONLY_KEY, bondedOnly )
                    .apply( );
            }
        }
        catch (Exception ex) {
            Log.e( TAG,"Exception whilst retrieving/parsing the configured delay", ex );
        }

        gattDelay = init( shared, GATT_DELAY_KEY, gattDelay );

        try {
            if (shared.contains( VOLUME_STEPS_KEY )){
                maxVolumeSteps = Integer.parseInt(
                    shared.getString( VOLUME_STEPS_KEY, Integer.toString( maxVolumeSteps ) )
                );
                Log.d( TAG, "Read " + maxVolumeSteps + " as max number of volume-change steps" );
            }else{
                shared.edit( )
                    .putString( VOLUME_STEPS_KEY, Integer.toString( maxVolumeSteps ) )
                    .apply( );
            }
        }
        catch (Exception ex) {
            Log.e( TAG,"Exception whilst retrieving/parsing the volume steps preference", ex );
        }

        try {
            if (shared.contains( AUTO_RETRY_KEY )){
                autoRetry = shared.getBoolean( AUTO_RETRY_KEY, false );
            }else{
                shared.edit( )
                    .putBoolean( AUTO_RETRY_KEY, autoRetry )
                    .apply( );
            }
        }
        catch (Exception ex) {
            Log.e( TAG,"Exception whilst retrieving/parsing the configured delay", ex );
        }

        retryInterval = init( shared, RETRY_INTERVAL_KEY, retryInterval );
    }

    boolean isBondedOnly() {
        return bondedOnly;
    }

    int getMaxVolumeSteps() {
        return maxVolumeSteps;
    }

    long getGattDelay() {
        return gattDelay;
    }

    boolean isAutoRetry() {
        return autoRetry;
    }

    long getRetryInterval() {
        return retryInterval;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences shared, String key) {

        if (BONDED_ONLY_KEY.equals( key )){
            bondedOnly = shared.getBoolean(
                key,
                bondedOnly
            );
            return;
        }

        if (VOLUME_STEPS_KEY.equals( key )){
            try {
                final String defValue = Integer.toString(
                    maxVolumeSteps
                );
                maxVolumeSteps = Integer.parseInt(
                    shared.getString( key, defValue )
                );
                Log.d( TAG, VOLUME_STEPS_KEY + " changed to " + maxVolumeSteps );
            }
            catch (Exception ex) {
                Log.w( TAG, "Exception whilst rec'ving update to " + VOLUME_STEPS_KEY );
            }
            return;
        }

        if (GATT_DELAY_KEY.equals( key )){
            gattDelay = getChangedPreference( shared, key, gattDelay );
            return;
        }

        if (AUTO_RETRY_KEY.equals( key )){
            autoRetry = shared.getBoolean(
                key,
                autoRetry
            );
            return;
        }

        if (RETRY_INTERVAL_KEY.equals( key )){
            retryInterval = getChangedPreference( shared, key, retryInterval );
            return;
        }
    }

    private long getChangedPreference(SharedPreferences shared, String key, long fallback) {

        long result = fallback;
        try {
            final String defValue = Long.toString(
                fallback
            );
            result = Long.parseLong(
                shared.getString( key, defValue )
            );
            Log.d( TAG, key + " changed to " + result );
        }
        catch (Exception ex) {
            Log.w( TAG, "Exception whilst rec'ving update to " + key );
        }
        return result;
    }
}
