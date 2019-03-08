/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

import java.util.UUID;

interface RemoteControl {

    String START_ACTION = "net.waveson.war.action.start";

    UUID SERVICE_UUID = UUID.fromString( "ACB76F70-2B52-4234-AFB4-A8E9CEB925A4" );
    UUID NOTIFY_UUID = UUID.fromString( "70BC9D28-EBEC-4EC6-9B27-6B79A718D34C" );
    UUID NAME_UUID = UUID.fromString( "7F2D6DF8-1610-4729-9038-A49163702EE2" );

    int TWO_STEP = 0x00000080;
    int ACTION_DOWN = 0x00000040;
    int MUTE = 0x00000020;
    int VOLUME_DOWN = 0x00000010;
    int VOLUME_UP = 0x00000008;
    int FORWARD = 0x00000004;
    int BACK = 0x00000002;
    int PLAY_PAUSE = 0x00000001;
    int STOP = 0x000000FF;

    int NOTIFICATION_MSG = 0x00000080;
    int SUBSCRIPTION_MSG = 0x00000081;

    int IS_SUBSCRIBED = 1;

    void startIfNotStarted();
    void stopIfStarted();
    void retry();
}
