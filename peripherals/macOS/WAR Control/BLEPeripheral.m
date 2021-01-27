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
    NSMutableData* unsent;
}

@property (atomic, strong) NSObject<BLEPeripheralDelegate>* delegate;

- (BOOL)send:(unsigned char)byte;
- (void)advertise:(CBPeripheralManager *)peripheral;
@end

@implementation BLEPeripheral
@synthesize delegate;
@dynamic subscribers;

- (instancetype)initWithDelegate:(NSObject<BLEPeripheralDelegate>*)delegate
{
	self = [super init];
	if (self){
		centrals = [NSMutableDictionary dictionary];
        serial_queue = dispatch_queue_create( "net.waveson.war.peripheral.queue", DISPATCH_QUEUE_SERIAL );
        unsent = [NSMutableData dataWithCapacity:16U];

		self.delegate = delegate;
	}
	return self;
}

- (void)start
{
	// Setup bluetooth
    atomic_store( &subscribedCentrals, 0 );
	peripheralManager = [[CBPeripheralManager alloc] initWithDelegate:self queue:nil];
}

- (void)stop
{
	// Signal any subscribers to go away
	if (atomic_load( &subscribedCentrals ) > 0){
		const int stop = WAR_STOP;
		[self send:((unsigned char) stop)];
	}
	
    dispatch_sync( serial_queue, ^{
        if (peripheralManager.isAdvertising){
            [peripheralManager stopAdvertising];
        }
    } );
	peripheralManager = nil;
}

- (BOOL)notify:(MediaKey)key isDown:(BOOL)down
{
	unsigned int u = ((int)key | TWO_STEP);
	if (down){
		u |= ACTION_DOWN;
	}
	return [self send:((unsigned char)u)];
}

- (NSUInteger)subscribers
{
	return (NSUInteger) atomic_load( &subscribedCentrals );
}

- (BOOL)send:(unsigned char)byte
{
    const int count = atomic_load( &(subscribedCentrals) );
    if (count){
        // Append the byte to the "queue" and then attempt to drain the queue
        NSData* data = [NSData dataWithBytes:&byte length:1];
        dispatch_async( serial_queue, ^{
            [self->unsent appendData:data];
        } );
        [self peripheralManagerIsReadyToUpdateSubscribers:peripheralManager];
        return YES;
    }
    return NO;
}

- (void)advertise:(CBPeripheralManager *)peripheral
{
	NSDictionary* advertisementData = @{CBAdvertisementDataServiceUUIDsKey:@[service.UUID]};
	[peripheral startAdvertising:advertisementData];
}

#pragma mark Bluetooth Peripheral Delegate
- (void)peripheralManagerIsReadyToUpdateSubscribers:(CBPeripheralManager *)peripheral
{
    // Drain the "queue" of unsent notifications, if any
    dispatch_async( serial_queue, ^{
        const unsigned char* ptr = [self->unsent bytes];

        NSUInteger u = 0U;
        for (const NSUInteger bound = [self->unsent length]; u < bound; ){
            // Check if there's anyone to send the unsent notification to
            const int count = atomic_load( &(self->subscribedCentrals) );
            if (count < 1){
                // Yup, bail
                u = bound;
                continue;
            }

            // Try and send the next byte
            const BOOL sent = [peripheral updateValue:[NSData dataWithBytes:ptr length:1U]
                                    forCharacteristic:self->notifyCharacteristic
                                 onSubscribedCentrals:nil];
            if (sent){
                NSLog( @"Sent %02x to %d central(s)", ((int) (*ptr)), count );

                // Advance!
                ++u;
                ++ptr;
                continue;
            }
            break;
        }

        // Remove from the queue the bytes that we know (think) we sent
        // or couldn't be sent because there's no one left to send them to
        if (u > 0U){
            [self->unsent replaceBytesInRange:NSMakeRange(0, u) withBytes:NULL length:0];
        }
    } );
}

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
    if (@available( macOS 10.15, * )){
        NSLog( @"authorization: %ld", (long)[CBPeripheralManager authorization] );
    }else{
        NSLog( @"authorizationStatus: %ld", (long)[CBPeripheralManager authorizationStatus] );
    }

    if (error){
		NSLog( @"Error advertising: %@", error );
	}else{
		NSLog( @"Did start advertising service." );
	}
}
@end
