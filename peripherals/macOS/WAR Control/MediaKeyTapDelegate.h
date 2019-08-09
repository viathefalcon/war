//
//  MediaKeyTapDelegate.h
//  WAR Control
//
//  Created by Stephen Higgins on 22/09/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#include "MediaKey.h"

#ifndef MediaKeyTapDelegate_h
#define MediaKeyTapDelegate_h

@protocol MediaKeyTapDelegate
@property (readonly) BOOL active;

- (BOOL)keyDown:(MediaKey)key;
- (BOOL)keyUp:(MediaKey)key;
- (BOOL)eject;
@end

#endif // MediaKeyTapDelegate_h
