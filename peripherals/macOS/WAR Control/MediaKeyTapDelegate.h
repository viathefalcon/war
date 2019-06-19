//
//  MediaKeyTapDelegate.h
//  WAR Control
//
//  Created by Stephen Higgins on 22/09/2018.
//  Copyright © 2018-19 Stephen Higgins. All rights reserved.
//

#ifndef MediaKeyTapDelegate_h
#define MediaKeyTapDelegate_h

typedef enum {
	
	MediaKeyInvalid = 0x00,
	MediaKeyPlayPause = 0x01,
	MediaKeyBack = 0x02,
	MediaKeyForward = 0x04,
	MediaKeyVolumeUp = 0x08,
	MediaKeyVolumeDown = 0x10,
	MediaKeyVolumeMute = 0x20,
	MediaKeyToggleVibrate = (MediaKeyVolumeMute|MediaKeyVolumeDown|MediaKeyVolumeUp)

} MediaKey;

@protocol MediaKeyTapDelegate
@property (readonly) BOOL active;

- (BOOL)keyDown:(MediaKey)key;
- (BOOL)keyUp:(MediaKey)key;
- (BOOL)eject;
@end

#endif // MediaKeyTapDelegate_h
