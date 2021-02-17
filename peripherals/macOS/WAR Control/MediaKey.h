//
//  MediaKey.h
//  WAR Control
//
//  Created by Stephen Higgins on 09/08/2019.
//  Copyright © 2019 Stephen Higgins. All rights reserved.
//

#ifndef MediaKey_h
#define MediaKey_h

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

#endif /* MediaKey_h */
