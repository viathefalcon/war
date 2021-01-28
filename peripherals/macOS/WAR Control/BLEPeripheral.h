//
//  BLEPeripheral.h
//  WAR Control
//
//  Created by Stephen Higgins on 22/09/2018.
//  Copyright © 2018-21 Stephen Higgins. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "MediaKey.h"

NS_ASSUME_NONNULL_BEGIN

@protocol BLEPeripheralDelegate <NSObject>
- (void)didSubscribe:(NSString*)central;
- (void)didUnsubscribe:(NSString*)central;
@end

@interface BLEPeripheral : NSObject
@property (readonly) NSUInteger subscribers;

- (instancetype)initWithDelegate:(NSObject<BLEPeripheralDelegate>*)aDelegate;
- (void)start;
- (void)stop;
- (BOOL)notify:(MediaKey)key isDown:(BOOL)down;
@end

NS_ASSUME_NONNULL_END
