//
//  MediaKeyTapPeripheral.m
//  WAR Control
//
//  Created by Stephen Higgins on 02/09/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#import <IOKit/hidsystem/ev_keymap.h>

#import "MediaKeyTap.h"

#define NX_KEYSTATE_UP      0x0A
#define NX_KEYSTATE_DOWN    0x0B

CGEventRef tapEventCallback(CGEventTapProxy, CGEventType, CGEventRef, void *);

@interface MediaKeyTap ()
{
	CFMachPortRef port;
	CFRunLoopSourceRef runLoopSource;
}

@property (nonatomic, strong) NSObject<MediaKeyTapDelegate>* delegate;

- (BOOL)keyEvent:(NSEvent*)event;
- (BOOL)disabledByTimeout;
@end

@implementation MediaKeyTap
@synthesize installed, delegate;

- (instancetype)initWithDelegate:(NSObject<MediaKeyTapDelegate>*)delegate;
{
	self = [super init];
	if (self){
		port = NULL;
		runLoopSource = NULL;

		self.installed = NO;
		self.delegate = delegate;
	}
	return self;
}

- (BOOL) install
{
	if (!self.installed){
		// Create the tap
		port = CGEventTapCreate(
			kCGSessionEventTap,
			kCGHeadInsertEventTap,
			kCGEventTapOptionDefault,
			CGEventMaskBit(NX_SYSDEFINED),
			tapEventCallback,
			(__bridge void * _Nullable)(self)
		);
		if (port == NULL){
			NSLog( @"Failed to install event tap." );
			return NO;
		}
		
		runLoopSource = CFMachPortCreateRunLoopSource(
			kCFAllocatorSystemDefault,
			port,
			0
		);
		if (runLoopSource == NULL){
			NSLog( @"Failed to create run loop source with event tap." );
			return NO;
		}
		
		CFRunLoopRef runLoop = CFRunLoopGetCurrent( );
		CFRunLoopAddSource( runLoop, runLoopSource, kCFRunLoopCommonModes );
		self.installed = YES;
	}
	return self.installed;
}

- (BOOL) remove
{
	if (runLoopSource){
		CFRunLoopRef runLoop = CFRunLoopGetCurrent( );
		if (CFRunLoopContainsSource( runLoop, runLoopSource, kCFRunLoopCommonModes )){
			CFRunLoopRemoveSource( runLoop, runLoopSource, kCFRunLoopCommonModes );
		}
		CFRelease( runLoopSource );
		runLoopSource = NULL;
	}
	if (port){
		CFRelease( port );
		port = NULL;
	}
	return (self.installed = NO);
}

- (BOOL)keyEvent:(NSEvent*)event
{
	// Look for an early out
	if (!delegate.active){
		return YES;
	}

	// Get out the data
	const int data = (int)[event data1];
	const int keyFlags = (data & 0xFFFF);

	// Compute the value to send
	MediaKey mediaKey = MediaKeyInvalid;
	const int keyCode = (data & 0xFFFF0000) >> 16;
	const int keyState = (keyFlags & 0xFF00) >> 8;
	switch (keyCode){
		case NX_KEYTYPE_PLAY:
			NSLog( @"Play/pause (%02x)", keyState );
			mediaKey = MediaKeyPlayPause;
			break;
			
		case NX_KEYTYPE_NEXT:
		case NX_KEYTYPE_FAST:
			NSLog( @"Next (%02x)", keyState );
			mediaKey = MediaKeyForward;
			break;
			
		case NX_KEYTYPE_PREVIOUS:
		case NX_KEYTYPE_REWIND:
			NSLog( @"Previous (%02x)", keyState );
			mediaKey = MediaKeyBack;
			break;
			
		case NX_KEYTYPE_SOUND_UP:
			NSLog( @"Volume up (%02x)", keyState );
			mediaKey = MediaKeyVolumeUp;
			break;
			
		case NX_KEYTYPE_SOUND_DOWN:
			NSLog( @"Volume down (%02x)", keyState );
			mediaKey = MediaKeyVolumeDown;
			break;

		case NX_KEYTYPE_MUTE:
			NSLog( @"Mute (%02x)", keyState );
			mediaKey = (event.modifierFlags & NSEventModifierFlagShift)
				? MedigaKeyToggleVibrate
				: MediaKeyVolumeMute;
			break;

		default:
			break;
	}
	if (mediaKey != MediaKeyInvalid){
		switch (keyState){
			case NX_KEYSTATE_DOWN:
				return ![delegate keyDown:mediaKey];

			case NX_KEYSTATE_UP:
				return ![delegate keyUp:mediaKey];
				
			default:
				NSLog( @"Unrecognised key state: %d", keyState );
				break;
		}
	}
	return YES;
}

- (BOOL)disabledByTimeout
{
	CGEventTapEnable( port, TRUE );
	return YES;
}
@end

CGEventRef tapEventCallback(CGEventTapProxy proxy, CGEventType type, CGEventRef cgEvent, void *refcon) {
	
	BOOL result = YES;
	MediaKeyTap* self = (__bridge MediaKeyTap*) refcon;
	switch (type) {
		case kCGEventTapDisabledByTimeout:
			result = [self disabledByTimeout];
			break;

		case NX_SYSDEFINED:
		{
			NSEvent* event = [NSEvent eventWithCGEvent:cgEvent];
			if ([event subtype] == 8){
				result = [self keyEvent:event];
			}
		}
			break;
			
		default:
			// Do nothing
			break;
	}
	return (result ? cgEvent : NULL);
}
