/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.os.IBinder;

import android.content.ComponentName;
import android.content.ServiceConnection;

class StartRemoteControlServiceConnection implements ServiceConnection {

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        // Tell the service to start if it hasn't, already
        RemoteControlService.LocalBinder binder = (RemoteControlService.LocalBinder) service;
        binder.getService( ).startIfNotStarted( );
    }

    @Override
    public void onServiceDisconnected(ComponentName name) { }
}
