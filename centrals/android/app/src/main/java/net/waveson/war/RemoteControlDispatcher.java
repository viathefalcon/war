/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import android.os.Handler;

class RemoteControlDispatcher implements Dispatcher, SubscriptionManager.Callback {

    private final SubscriptionManager.Callback delegate;

    private final Handler handler;

    RemoteControlDispatcher(SubscriptionManager.Callback delegate, Handler handler) {
        this.delegate = delegate;
        this.handler = handler;
    }

    @Override
    public void dispatch(Runnable runnable) {
        handler.post( runnable );
    }

    @Override
    public void dispatch(Runnable runnable, long delay) {
        handler.postDelayed( runnable, delay );
    }

    @Override
    public void onConnectionError(final int string, final Object... etc) {
        dispatch( new Runnable( ) {
            @Override
            public void run() {
                delegate.onConnectionError( string, etc );
            }
        } );
    }

    @Override
    public void onError(final int string, final Object... args) {
        dispatch( new Runnable( ) {
            @Override
            public void run() {
                delegate.onError( string, args );
            }
        } );
    }

    @Override
    public void onSubscriptionChanged(final boolean subscribed) {

        handler.obtainMessage(
            RemoteControl.SUBSCRIPTION_MSG,
            (subscribed ? RemoteControl.IS_SUBSCRIBED : 0), 0
        ).sendToTarget( );
    }

    @Override
    public void onNotification(final int value) {

        // Send a message because instantiating a new object (a Runnable) to convey 1 byte seems..
        // wasteful? Also, we can expect this to be called a lot during the lifetime of the app,
        // whereas we hope the error handlers will never/rarely be touched
        handler.obtainMessage(
            RemoteControl.NOTIFICATION_MSG,
            value, 0
        ).sendToTarget( );
    }
}
