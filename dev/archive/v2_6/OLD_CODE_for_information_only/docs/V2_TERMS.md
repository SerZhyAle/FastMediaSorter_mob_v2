# Glossary of Terms

This document contains definitions of key terms used in the FastMediaSorter v2 specification.

## 1. Media File Types and Representations

**File Icon** - a brief representation in the form of an image. Small in the list or large in the table when displaying a number of files next to each other.

**Media File Types** - image, audio, video, animation.

**Image (Picture)** - a static image from a pool of common graphic file formats (JPEG, PNG). Brief representation (file icon) - a reduced version with preserved proportions. File information: location, name, extension, size, creation date, dimensions.

**Animation** - a GIF file. Brief representation (file icon) - a reduced first frame. File information: location, name, size, creation date, number of frames, dimensions.

**Audio File** - audio from a pool of common formats (MP3, WAV). Brief representation (file icon) - an image displaying the file extension. File information: location, name, extension, size, creation date, duration, bitrate.

**Video File** - video from a pool of common formats (MP4, WEBM, MOV, MKV). May or may not have an audio track. Brief representation (file icon) - a reduced first frame with preserved proportions. File information: location, name, extension, size, creation date, duration, video codec, resolution.

**Media Files (Media)** - all files from the above groups that the program supports.

## 2. Resources and Storage

**Folder (Resource)** - an existing [local Android folder] OR [network folder in the device's internal local network] OR [user's virtual disk folder in the cloud (Google Drive, OneDrive, etc.)] OR [SFTP Folder], which potentially may contain media files. This folder (resource) was registered by the user in the program manually or the program found/added it during scanning. For each folder (resource) registered in the program, we must store:

1.  Type (local, network, cloud, other)
2.  Address and access conditions to the resource (differently for file types)
3.  Authorization information (user, password)
4.  Short name (resource name) (for display in lists and sorting buttons)
5.  Types of media files that the program will work with inside (several boolean values)
6.  Name of the last selected or played media file (to resume viewing from the same place on the next opening)
7.  Number of media files inside (at the last opening or scanning)
8.  Set interval for slideshow of folder (resource) media files
9.  Flag that the resource is also a destination
10. Order in the destinations list, if the resource is also a destination
11. Resource creation date
12. Date of last scanning of the resource or work with it
13. Sorting mode for this resource (by default taken from settings, but during playback of resource media files or when editing the resource profile, the user can specify a different value for this folder)
14. Resource display mode: list or grid (taken by default from settings, but the user can choose when editing the resource or during resource viewing)
15. Grid cell size set for this resource (can be set when editing the resource; if not - the default value from settings is taken)

**Destinations (Recipients)** - a list of folders (resources) up to 10 folders, selected from those registered in the program, which are available to the user and the program for writing. The user selected them from the registered folders (resources) or added to the recipients list immediately when registering a new folder (resource). Used as recipients for copy and move operations of media files.

## 3. Operations and Features

**File Selection** - in the program, selection should be implemented through:

1.  File extension (belongs to media type). Video, audio, and animation can be disabled in the program. In this case, any mention of them is also removed from the interface until the user enables them.
2.  Size (separately set limits for video files, audio files, animation, images).
3.  Creation date.

**Media File Sorting** - order of file presentation in the list or order during slideshow:

1.  By name (alphabetically)
2.  Reverse by name (reverse alphabet)
3.  By logical naming: this is when the order is Image1, Image2, Image3, .. Image10 (in the case of normal alphabetical, Image10 would be right after Image1)
4.  By size
5.  Reverse by size
6.  By creation date
7.  Reverse by creation date
8.  By duration (for video and audio)
9.  Reverse by duration (for video and audio)
10. Random (shuffled order)

**Player (Playback)** - demonstration of media files in the currently selected folder (resource) in their natural form (viewing, listening).

**Slideshow** - playback of media files with transition to the next file on schedule. For example, every 10 seconds. Optionally, the schedule condition can be supplemented with duration for video and audio media files. That is, there will be such an option in which the file is played until the end of its duration before transitioning to the next file.

**Sorting** - a list of operations that the user can apply to one file (copy, move, delete, rename) or to selected files from the list (copy, move, delete). For copy and move, all permissible Destinations must be offered.

**Undo** - a complex undo operation of the last operation. The presence of this command in the interface is an option in settings. Depends on the previous operation. Transition to the next file, folder change, or exit from Activity are also operations.

1.  If the last operation was copying, this means trying to delete the copy on the recipient.
2.  If the last operation was moving, try to move the file back.
3.  For deletion operation, do not perform physical file deletion immediately. Wait for the next command. If the next command is not "Undo", then delete the file. If the next command is "Undo", then "forget" about the "Delete" command, which was apparently erroneous.
4.  For renaming - try to return the original name. Also, if the next command after renaming was not "Undo", then you can forget the original name of the last renamed file.

## 4. UI and Interface Elements

**Media File Display Area** - the screen area where the current media file is played. This can be full screen or part of the screen surrounded by control elements (buttons).

**Rotation** - static images during display can be rotated 90 degrees horizontally. All media files, including rotated images, should be stretched as much as possible in the display area. But it is important to always preserve the original file proportions.

**"Back"** - typical Android button to go back to the previous Activity.

**Popup Window** - an instant message element that is displayed for several seconds (depending on the text length).

**Dialog** - a window that appears with information and buttons, waiting for the user's decision. This can be a question with Yes/No/Cancel options. The information can be short or long (available for copying). Custom buttons can be implemented (for example, "copy dialog text to clipboard").
