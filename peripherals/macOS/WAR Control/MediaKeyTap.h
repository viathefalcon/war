//
//  MediaKeyTapPeripheral.h
//  WAR Control
//
//  Created by Stephen Higgins on 02/09/2018.
//  Copyright © 2018-21 Stephen Higgins. All rights reserved.
//

#import <Cocoa/Cocoa.h>

#import "MediaKeyTapDelegate.h"

@interface MediaKeyTap : NSObject
@property (assign, atomic) BOOL installed;
- (instancetype)initWithDelegate:(NSObject<MediaKeyTapDelegate>*)delegate;
- (BOOL) install;
- (BOOL) remove;
@end
