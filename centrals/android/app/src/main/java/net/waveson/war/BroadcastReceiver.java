/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.Map;
import java.util.HashMap;

import android.content.Intent;
import android.content.Context;

public class BroadcastReceiver extends android.content.BroadcastReceiver {

    public static final String STOP_ACTION = "net.waveson.war.stop";
    public static final String RETRY_ACTION = "net.waveson.war.retry";
    public static final String SETTINGS_ACTION = "net.waveson.war.settings";

    private static final Map<String, Integer> dispatches = new HashMap<>( );
    static {
        dispatches.put( STOP_ACTION, DispatchService.DISPATCH_STOP );
        dispatches.put( RETRY_ACTION, DispatchService.DISPATCH_RETRY );
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction( );
        if (dispatches.containsKey( action )){
            Intent dispatch = new Intent( context, DispatchService.class );
            dispatch.putExtra( DispatchService.DISPATCH_EXTRA, dispatches.get( action ) );
            context.startService( dispatch );
        }else if (SETTINGS_ACTION.equals( action )){
            context.sendBroadcast(
                new Intent( Intent.ACTION_CLOSE_SYSTEM_DIALOGS )
            );

            // Kick off the settings activity
            Intent settings = new Intent( context, SettingsActivity.class );
            settings.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            context.startActivity( settings );
        }
    }
}
