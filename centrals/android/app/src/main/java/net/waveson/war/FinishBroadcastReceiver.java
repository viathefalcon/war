/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.app.Activity;

import android.util.Log;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;

class FinishBroadcastReceiver extends BroadcastReceiver {

    static final String ACTION = "net.waveson.war.finish";

    private static final String TAG = FinishBroadcastReceiver.class.getSimpleName( );

    private final Activity activity;

    FinishBroadcastReceiver(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d( TAG, "FinishBroadcastReceiver#onReceive(" + intent.getAction( ) + ")" );
        if (ACTION.equals( intent.getAction( ) )){
            activity.finish( );
        }
    }
}
