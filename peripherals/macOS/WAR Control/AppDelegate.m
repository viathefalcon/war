//
//  AppDelegate.m
//  WAR Control
//
//  Created by Stephen Higgins on 30/07/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#import "AppDelegate.h"
#import "MediaKeyTap.h"
#import "BLEPeripheral.h"

@interface AppDelegate () <BLEPeripheralDelegate, NSUserNotificationCenterDelegate>
{
	NSStatusItem* statusItem;
	MediaKeyTap* tap;
	BLEPeripheral* peripheral;
	NSUserNotificationCenter* defaultUserNotificationCenter;
}
@property (weak) IBOutlet NSMenu *menu;

-(IBAction)about:(id)sender;
@end

@implementation AppDelegate
- (void)applicationDidFinishLaunching:(NSNotification *)aNotification
{
	// Add the status item
	NSStatusBar* statusBar = [NSStatusBar systemStatusBar];
	statusItem = [statusBar statusItemWithLength:NSSquareStatusItemLength];
	NSImage* image = [NSImage imageNamed:@"Status"];
	statusItem.button.image = image;
	statusItem.menu = self.menu;
	
	// Wire ourselves up to the notification center
	defaultUserNotificationCenter = NSUserNotificationCenter.defaultUserNotificationCenter;
	[defaultUserNotificationCenter setDelegate:self];
	
	// Instantiate the BLE peripheral, and media key hook
	peripheral = [[BLEPeripheral alloc] initWithDelegate:self];
	tap = [[MediaKeyTap alloc] initWithDelegate:peripheral];

	// Install the hook/handler
	[peripheral start];
	[tap install];
}

- (void)applicationWillTerminate:(NSNotification *)aNotification
{
	[peripheral stop];
	[tap remove];
}

-(IBAction)about:(id)sender
{
	// c.f. https://stackoverflow.com/a/20487957
	[NSApp activateIgnoringOtherApps:YES];
	[NSApp orderFrontStandardAboutPanel:self];
}

- (void)didSubscribe:(nonnull NSString *)central
{
	NSLog( @"didSubscribe: %@", central );

	NSUserNotification* notification = [[NSUserNotification alloc] init];
	notification.title = @"Subscribed";
	notification.informativeText = central;
	[defaultUserNotificationCenter deliverNotification:notification];
}

- (void)didUnsubscribe:(nonnull NSString *)central
{
	NSLog( @"didUnsubscribe: %@", central );

	NSUserNotification* notification = [[NSUserNotification alloc] init];
	notification.title = @"Unsubscribed";
	notification.informativeText = central;
	[defaultUserNotificationCenter deliverNotification:notification];
}

- (void)userNotificationCenter:(NSUserNotificationCenter *)center didDeliverNotification:(NSUserNotification *)notification
{
	NSLog( @"Delivered notification: %@ %@", notification.title, notification.informativeText );
}

- (BOOL)userNotificationCenter:(NSUserNotificationCenter *)center shouldPresentNotification:(NSUserNotification *)notification
{
	NSLog( @"shouldPresentNotification: %@ %@", notification.title, notification.informativeText );
	return YES;
}
@end
