# PhotoFrameSlideshow
A Google-Free slideshow app intended for digital photo frames running on Android.

Intentionally decoupled from file transfer/syncing.  For my own personal use, I use SyncThing to share the Pictures directory from the photo frame.

The app will pull images from the /Pictures/ folder on internal storage and display them.  The app will generate a config.json file in the same folder which can be used to adjust the behaviour of the frame.  The config file will be reloaded each time the image changes, and the app will pull in any new images once all of the currently chosen images have been displayed.

---

## In-App Controls

### Swipe Left and Right
- Skip to next or previous image.  You can skip forward as far as you like, but can only skip back to the start of the current image list.

### Single Tap
- Pauses the slideshow on the currently displayed image

### Double Tap
- Brings up the navigation bar if it's hidden, or hides it if it is currently visible.  This lets you get out of the app if you need to manage the device.

### Swipe Up and Down
- Modifies the rotation data in the image and redisplays it.  Swiping down does a clockwise rotation and swiping up does counter-clockwise.

### Physically rotating the device
- Will maintain the current image list and re-display the current image in the new orientation

---

## Config.json Options

### imageTime
- The amount of time each image is displayed for
- In seconds

### shuffle
- Should the images be displayed shuffled or alphabetically 
- true or false

### showFileNames
- If enabled, a pop-up will show the file name briefly when the image changes 
- true or false

### activeHourStart
- The hour of the day when the frame should wake up and start showing images.  No support for minutes at the moment, as that level of granularity seems unnecessary.
- Integer, 0 to 23

### activeHourEnd
- The hour of the day when the frame should go to sleep and stop showing images.  No support for minutes at the moment, as that level of granularity seems unnecessary.
- Integer, 0 to 23

### ntpServer
- The NTP server that the frame will use to sync the time (if forceClockSync is enabled)
- String (an NTP server URL)

### forceClockSync
- Sync the system time with the specified NTP server.  Requires root.
- true or false

### ntpSyncInterval
- How frequently the clock should be synced with NTP
- In seconds

### fileTypes
- A list of file types that the slideshow will recognize as images and try to display.  Probably not necessary to adjust?
- List of strings (Filetypes, including the leading .)

### subdirectories
- A list of subdirectoris within the /Pictures/ folder that can be enabled or disabled via config (Perhaps for seasonal images, etc)
- Each subdirectory entry includes two fields
  - **folder**
    - The folder name (without a leading or trailing slash)
    - String
  - **enabled**
    - Whether or not the folder should be included when loading new images
    - true or false
