/*
 * Copyright (c) 2018-19 Stephen Higgins.
 * All rights reserved.
 */
package net.waveson.war;

interface Dispatcher {
    void dispatch(Runnable runnable);
    void dispatch(Runnable runnable, long delay);
}
