//
//  BLEPeripheral.h
//  WAR Control
//
//  Created by Stephen Higgins on 22/09/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "MediaKeyTapDelegate.h"

NS_ASSUME_NONNULL_BEGIN

@protocol BLEPeripheralDelegate <NSObject>
- (void)didSubscribe:(NSString*)central;
- (void)didUnsubscribe:(NSString*)central;
@end

@interface BLEPeripheral : NSObject <MediaKeyTapDelegate>
- (instancetype)initWithDelegate:(NSObject<BLEPeripheralDelegate>*)aDelegate;
- (void)start;
- (void)touch;
- (void)stop;
@end

NS_ASSUME_NONNULL_END
