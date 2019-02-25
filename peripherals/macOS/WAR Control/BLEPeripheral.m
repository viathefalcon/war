//
//  BLEPeripheral.m
//  WAR Control
//
//  Created by Stephen Higgins on 22/09/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#include <stdatomic.h>
#include <dispatch/dispatch.h>

#import <CoreBluetooth/CoreBluetooth.h>

#import "BLEPeripheral.h"

#define ACTION_DOWN 0x40
#define TWO_STEP 0x80
#define WAR_PING 0x7F
#define WAR_STOP 0xFF

NSString *const SERVICE_UUID = @"ACB76F70-2B52-4234-AFB4-A8E9CEB925A4";
NSString *const NOTIFY_UUID = @"70BC9D28-EBEC-4EC6-9B27-6B79A718D34C";
NSString *const NAME_UUID = @"7F2D6DF8-1610-4729-9038-A49163702EE2";

@interface WARNameBuffer : NSObject
{
	NSMutableData* buffer;
	BOOL complete;
}
@property (nonatomic, readonly) BOOL isComplete;
- (BOOL)append:(NSData*)data;
- (NSString*)stringValue;
@end

@implementation WARNameBuffer
@dynamic isComplete;

- (instancetype)init
{
	self = [super init];
	if (self){
		buffer = [NSMutableData data];
		complete = NO;
	}
	return self;
}

- (BOOL)append:(NSData*)data
{
	// Look for an early out
	if (data.length < 1){
		return NO;
	}

	// Get out and test the prefix
	unsigned char prefix;
	[data getBytes:&prefix length:1];
	const int mask = 0x00000080;
	complete = ((int) prefix) & mask;

	// Accumulate the rest of the data
	[buffer appendData:[data subdataWithRange:NSMakeRange(1, data.length - 1)]];
	return complete;
}

- (NSString*)stringValue
{
	return [[NSString alloc] initWithData:buffer encoding:NSUTF8StringEncoding];
}

- (BOOL)isComplete
{
	return complete;
}
@end

@interface WARCentral
- (BOOL)appendNameData:(NSData*)data;
- (BOOL)didSubscribe;
@end

@interface BLEPeripheral () <CBPeripheralManagerDelegate>
{
	_Atomic(int) subscribedCentrals;
	CBPeripheralManager* peripheralManager;
	CBMutableCharacteristic* notifyCharacteristic;
	CBMutableCharacteristic* nameCharacteristic;
	CBMutableService* service;
	
	NSMutableDictionary* centrals;
	dispatch_queue_t serial_queue;
}

@property (atomic, strong) NSObject<BLEPeripheralDelegate>* delegate;

- (BOOL)notify:(unsigned char)byte;
- (void)advertise:(CBPeripheralManager *)peripheral;
@end

@implementation BLEPeripheral
@synthesize delegate;

- (instancetype)initWithDelegate:(NSObject<BLEPeripheralDelegate>*)delegate
{
	self = [super init];
	if (self){
		centrals = [NSMutableDictionary dictionary];
		serial_queue = dispatch_queue_create( "net.waveson.war.peripheral.queue", DISPATCH_QUEUE_SERIAL );

		self.delegate = delegate;
	}
	return self;
}

- (void)start
{
	// Setup bluetooth
	peripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
	atomic_store( &subscribedCentrals, 0 );
}
	
- (void)touch
{
	if (self.active){
		const int i = WAR_PING;
		[self notify:((unsigned char) i)];
	}else{
		NSLog( @"[BLEPeripheral touch] (inactive)" );
	}
}

- (void)stop
{
	// Signal any subscribers to go away
	if (self.active){
		
		const int stop = WAR_STOP;
		[self notify:((unsigned char) stop)];
	}
	
	if (peripheralManager.isAdvertising){
		[peripheralManager stopAdvertising];
	}
	peripheralManager = nil;
}

- (BOOL)notify:(unsigned char)byte
{
	NSData* data = [NSData dataWithBytes:&byte length:1];
	const BOOL sent = [peripheralManager updateValue:data forCharacteristic:notifyCharacteristic onSubscribedCentrals:nil];
	if (sent){
		const int count = atomic_load( &subscribedCentrals );
		NSLog( @"Sent %02x to %d central(s)", ((int)byte), count );
	}else{
		NSLog( @"Failed to notifiy all subscribed centrals?" );
	}
	return sent;
}
	
- (void)advertise:(CBPeripheralManager *)peripheral
{
	NSDictionary* advertisementData = @{CBAdvertisementDataServiceUUIDsKey:@[service.UUID]};
	[peripheral startAdvertising:advertisementData];
}

#pragma mark Media Key Delegate
- (BOOL)active
{
	return (atomic_load( &subscribedCentrals ) > 0);
}

- (BOOL)keyDown:(MediaKey)key
{
	const unsigned int i = ((int)key | TWO_STEP | ACTION_DOWN);
	return [self notify:((unsigned char)i)];
}

- (BOOL)keyUp:(MediaKey)key
{
	const unsigned int i = ((int)key | TWO_STEP);
	return [self notify:((unsigned char)i)];
}

#pragma mark Bluetooth Peripheral Delegate
- (void)peripheralManagerDidUpdateState:(nonnull CBPeripheralManager *)peripheral
{
	switch (peripheral.state){
		case CBManagerStatePoweredOn:
		{
			notifyCharacteristic = [[CBMutableCharacteristic alloc] initWithType:[CBUUID UUIDWithString:NOTIFY_UUID] properties:(CBCharacteristicPropertyRead|CBCharacteristicPropertyNotify) value:nil permissions:CBAttributePermissionsReadable];
			nameCharacteristic = [[CBMutableCharacteristic alloc] initWithType:[CBUUID UUIDWithString:NAME_UUID] properties:CBCharacteristicPropertyWrite value:nil permissions:CBAttributePermissionsWriteable];
			service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:SERVICE_UUID] primary:YES];
			service.characteristics = @[notifyCharacteristic, nameCharacteristic];
			[peripheralManager addService:service];
		}
			break;
			
		case CBManagerStateUnsupported:
			NSLog( @"CBPeripheralManagerStateUnsupported" );
			break;
			
		case CBManagerStateUnauthorized:
			NSLog( @"CBPeripheralManagerStateUnauthorized" );
			break;
			
		default:
			NSLog( @"peripheralManagerDidUpdateState (other): %ld", (long)peripheral.state );
			break;
	}
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didSubscribeToCharacteristic:(CBCharacteristic *)characteristic
{
	NSLog( @"Central subscribed to characteristic: %@", central );
	atomic_fetch_add_explicit( &subscribedCentrals, 1, memory_order_relaxed );
	if (peripheral.isAdvertising){
		[peripheral stopAdvertising];
	}
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray<CBATTRequest *> *)requests
{
	NSLog( @"didReceiveWriteRequests: %ld", [requests count] );
	CBUUID* nameUUID = nameCharacteristic.UUID;
	[requests enumerateObjectsUsingBlock:^(CBATTRequest * _Nonnull obj, NSUInteger idx, BOOL * _Nonnull stop) {

		CBATTRequest* request = (CBATTRequest*) obj;
		NSLog( @"didReceiveWriteRequest: %@", request.characteristic.UUID );
		if ([request.characteristic.UUID isEqual:nameUUID]){
			dispatch_async( self->serial_queue, ^(void) {

				WARNameBuffer* name = [self->centrals objectForKey:request.central];
				if (name == nil){
					name = [[WARNameBuffer alloc] init];
				}
				const BOOL completed = [name append:request.value];
				[self->centrals setObject:name forKey:request.central];
				if (completed){
					[self->delegate didSubscribe:[name stringValue]];
				}
			} );
		}
		*stop = NO;
	}];
	
	[peripheral respondToRequest:requests.firstObject withResult:CBATTErrorSuccess];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic;
{
	atomic_fetch_sub_explicit( &subscribedCentrals, 1, memory_order_relaxed );
	NSLog( @"Central unsubscribed from characteristic: %@", central );

	dispatch_async( self->serial_queue, ^(void) {
		WARNameBuffer* name = [self->centrals objectForKey:central];
		if (name){
			[self->delegate didUnsubscribe:[name stringValue]];
		}
		[self->centrals removeObjectForKey:central];
	} );

	// If no one is subscribed, start advertising again
	if (atomic_load( &subscribedCentrals ) == 0){
		[self advertise:peripheral];
	}
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error
{
	if (error){
		NSLog( @"Error adding service: %@", error );
	}else{
		// Start advertising the service
		[self advertise:peripheral];
	}
}

- (void)peripheralManagerDidStartAdvertising:(CBPeripheralManager *)peripheral error:(NSError *)error
{
	NSLog( @"Status: %ld", (long)[CBPeripheralManager authorizationStatus] );
	
	if (error){
		NSLog( @"Error advertising: %@", error );
	}else{
		NSLog( @"Did start advertising service." );
	}
}
@end
