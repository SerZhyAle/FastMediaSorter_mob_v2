# V2 Specification

This file contains detailed specifications for FastMediaSorter v2, corresponding to the plan items in Version2_todo.md.

## Phase 1: Discovery and Specification

### 1.1. Define Core Concept & Goals

#### TERMS:

Read the file V2_TERMS.md

#### The primary goals for v2.:

Read the file V2_p1_1.md

### 1.2. UI/UX Design

#### Welcome Screen
Launches on the first start of the program after installation and via a button on the settings screen.
It appears as several changing pages with instructions and a description of the program. There is a "Skip" button that closes this window, even if the user has not scrolled to the end.

#### Main Screen
The main screen of the program.
The following elements are visible and accessible to the user (depending on the settings):

1.  List of registered "folders" (resources). In this list, each resource should be represented as a line containing:
    *   short name of the resource,
    *   full address of the resource (in very small font),
    *   resource type,
    *   number of media files in the resource (determined when added or last opened in the Browse Screen or last scanned),
    *   a mark if the resource is also in the destinations list
    *   briefly by the first letters about the types of media files that the user has defined for the folder ("I"mage, "V"ideo, "A"udio, "G"if (animations)),
    *   a button to open the "resource profile screen" for editing (as an "edit" icon)
    *   "up" and "down" buttons (as "arrow" icons) to move the resource's position in the list,
    *   a button to delete the folder (resource) from the list (as a "trash can" icon)

    The size of the folder (resource) list should be dynamic. It will be empty at first, but when it no longer fits on the screen with other elements, it should have a scroll bar.

    Only one item can be selected in the folder (resource) list. After opening the Main Screen, the last selected resource should be highlighted. If a resource has been deleted, no resource should be selected. The user needs to select one.

    The button to delete a resource from the list should bring up a dialog "Are you sure you want to delete the resource?" or (if applicable) "Are you sure you want to delete the destination?". If the user answers "YES", the record for this resource is deleted.

    If the user briefly taps on a folder (resource) line, it is simply highlighted and becomes selected. As soon as we have a selected folder-resource, the "Start Player" and "Copy Resource" buttons become active (enabled).

    If the user "long-presses the resource", it calls the Browse Screen for that resource.

    If the user "double-taps the resource", it calls the Browse Screen for that resource.

    If a new resource has just been manually added to the list, it should be selected immediately. If the added resources are the result of a scan, they should not be selected automatically.

    It is important to remember that almost all screens called from here can change the composition and data of this screen when they are closed.

2.  Buttons to manage this list of folders (resources). Located in a single row above the list, which occupies 100% of the width:
    *   "Start Player" button as a double-sized "PLAY" icon.
        Calls the Browse Screen for the selected resource.
        If no resource is selected in the list, the "Start Player" button is inactive (disabled).
    *   "Add Resource" button as a "large Plus" icon.
        Calls the "add and scan resources screen".
    *   "Copy Resource" button as a "copy" icon.
        Calls the "add and scan resources screen" taking into account the selected resource.
        If no resource is selected in the list, the "Copy Resource" button is inactive (disabled).
        Unlike the "create" button, when copying, all values for the new resource are taken from the currently selected resource in the list. The user only needs to specify the changes (differences from the original).
    *   A small space between the buttons.
    *   "filter and sort" button as a "filter" icon.
        Calls the "filter and sort resource list screen".
        When a filter is applied on the main screen, a warning with a description of the applied filter appears at the bottom.
    *   "refresh" button as a "refresh" icon.
        Calls the procedure to update (rescan) existing folders.
    *   A small space between the buttons.
    *   Button to go to the Settings Screen as a "Settings" icon.
        Calls the Settings Screen.
    *   Button to exit the program (close it) as an "Exit Door" icon.

#### Add and Scan Resources Screen
On this screen, the user must enter the details of a new folder-resource record.
When copying from an existing resource, all values for the new resource are taken from the currently selected resource in the list. The user only needs to specify the changes (differences from the original). That is, the resource type does not change when copying.

Elements:
1.  "Back" button, to cancel the addition and return to the "Main Screen".

2.  Select resource type.
    A convenient interface element is needed for this:
    *   "Local folder" option (clickable if the user has granted permission for local folders, otherwise visible but inactive)
    *   "Network folder" option
    *   "Cloud folder" option
    *   "SFTP" option (clickable if the user has granted permission for internet connection)

3.  Then, below, depending on the resource type, there will be different elements:

    **3.1 For a local folder:**

    3.1.1 A button with a magnifying glass icon and the label "SCAN" and a description text next to it "Add all local folders with media".
    Calls the procedure for scanning available local folders containing media files. The found resources are added to the "resources to add" list.
    There are predefined Android folders - Download, photos, movies, Camera. It is necessary to determine which ones are on this device. If these predefined folders are not yet in the resource list (for example, when scanning for the first time), they should be added to the "resources to add" list, even if they do not contain media files.
    The rest of the device's folders need to be scanned for media files and, if they exist, added to the "resources to add" list with the folder name (for example, from Telegram or Whatsapp). It is necessary to check that the folders being added to the "resources to add" list are not already registered in the folder (resource) list.

    During the scan, a pop-up message "Scanning local folders..." appears.
    The number of media files in the folder is determined during the scan.
    The right to write to this folder is determined during the scan.

    3.1.2 A button with a "select folder" icon and the label "add manually" and a description text "Specify folder manually".
    When this button is called, an Android dialog for selecting a local folder is needed. If the user has selected a folder in the dialog, the "List of folders (resources) to add" should appear below the button.
    The user can add different folders to this list one by one using the "select folder" button.

    After selecting a folder for the resource, the number of media files in the folder is determined.
    After selecting a folder for the resource, the right to write to this folder is determined.

    3.1.3 After the scan or successful closing of the "select folder" dialog (with a folder selected), the "List of folders (resources) to add" should appear (be populated) below.
    The list consists of resources found during the scan or added manually. It consists of the following fields:
    *   "Add" checkbox. The user can uncheck it if they do not want to add this resource, it is checked by default.
    *   a short name from the folder name, which the user can edit.
    *   the number of media files found inside.
    *   "In destinations" checkbox, but only for folders that are writable. It is unchecked by default. The user can check it manually for each line of the list before the next step.

    After scanning or manually adding folders, if the list is not empty, a "Add to resources" button with an "add to table" icon appears below the list.

    By clicking this button, all resources not unchecked by the user from the "to add" list are added to the program's folder (resource) list with the scan result information and the short name specified here. If a folder is specified for destinations and the destinations list is not full, a destination flag and the next available position in the queue are added to the record of such a resource. If destinations are already full (up to 10), the folder-resource is added to the resources without this flag, and a pop-up message appears for the user that the destinations are full.

    We pass the need to display warnings to the main screen.
    After that, return to the "Main Screen".

    **3.2 For a network folder (in the local network):**

    3.2.1 Field for entering the machine's IP.
    The field should be automatically filled with the IP data of this Android device. For example, if the IP is 192.168.1.100, we immediately fill the field for the user as "192.168.1." with the cursor at the end of the line. The user can only enter numbers and a dot in the field. But if they enter a comma - it is immediately recognized as a dot. If they enter any other characters - they are ignored. If they enter a fourth dot - the input is ignored. If they try to enter a four-digit number - the input is blocked. In general, a convenient IP address input field is needed. A "field help" link should lead to a Google search engine immediately with the string "How to determine the IP of my computer on the internal network?".

    3.2.2 "user" and "password" fields in one line.
    If the fields are empty, the program accepts the user and password entered in the program settings. If there are no saved user and password in the program settings - the fields should be underlined in red, requiring the user to enter them.

    3.2.3 "Test" button to check access to the IP.
    As a result, the user gets a dialog with a detailed description of network access to this IP. This dialog, in addition to the OK button, has a "copy to clipboard" button for the diagnostic text.

    3.2.4 In the same row with the "Test" button, another one - a button with a magnifying glass icon and the label "SCAN" and a description text next to it "Add all open network folders from this computer".

    Calls the procedure for scanning available network folders, regardless of the presence of media files in them. The found resources are added to the "resources to add" list if these folders are not yet in the program's resource list.
    Predefined administrative folders (like $C) are ignored.

    During the scan, a pop-up message "Scanning network folders..." appears.
    The number of media files in the folder is determined during the scan.
    The right to write to this folder is determined during the scan.

    3.2.5 The third button in the same row with a "select folder" icon and the label "add manually" and a description text "Specify folder manually".
    When this button is called, a dialog for entering the text value of the folder address is needed. With "Add" and "Cancel" buttons.
    In the dialog, it should be clear that the IP address no longer needs to be entered, nor any forward or backward slashes. In the dialog, it is advantageous to have a drop-down list with the names of folders found on this computer. But the user will most likely want to enter a subfolder or address manually.
    So, for example, if the user registers a new resource "192.168.1.100\Common\Images", then the text "192.168.1.100\" should be an informational header, the text "Common\" can drop down as one of the folders found on this computer. And the user must enter the text "Images" themselves.

    After the user has specified the folder in the dialog, which they close with the "Add" button, the resource should appear in the "List of folders (resources) to add".
    The user can add different folders to this list one by one using the "select folder" button.

    After selecting a folder for the resource, the number of media files in the folder is determined.
    After selecting a folder for the resource, the right to write to this folder is determined.
    If the network folder is not found, it is still present in the list to be added, but with the text ("not found").
    If the access rights to the folder are not full, it is still present in the list to be added, but with a text about insufficient rights.

    3.2.6 After the scan or successful closing of the "select folder" dialog (with a folder selected), the "List of folders (resources) to add" should appear (be populated) below.
    The list consists of resources found during the scan or added manually. It consists of the following fields:
    *   "Add" checkbox. The user can uncheck it if they do not want to add this resource, it is checked by default.
    *   a short name from the IP\folder name, which the user can edit.
    *   the number of media files found inside.
    *   "In destinations" checkbox, but only for folders that are writable. It is unchecked by default. The user can check it manually for each line of the list before the next step.

    After scanning or manually adding folders, if the list is not empty, a "Add to resources" button with an "add to table" icon appears below the list.

    By clicking this button, all resources not unchecked by the user from the "to add" list are added to the program's folder (resource) list with the scan result information and the short name specified here. If a folder is specified for destinations and the destinations list is not full, a destination flag and the next available position in the queue are added to the record of such a resource. If destinations are already full (up to 10), the folder-resource is added to the resources without this flag, and a pop-up message appears for the user that the destinations are full.

    We pass the need to display warnings to the main screen.
    After that, return to the "Main Screen".

    **3.3 Cloud folder:**

    3.3.1 A set of buttons "Google Drive", "One Drive", "Dropbox". Clicking on such buttons - authorization and selection. Calls an authorization dialog with the selected cloud resource provider and selection of a folder for addition there on the storage.

    3.3.2 Upon successful closing of the "select folder" dialog (with a folder selected), the "List of folders (resources) to add" should appear (be populated) below.
    The list consists of manually added resources. It consists of the following fields:
    *   "Add" checkbox. The user can uncheck it if they do not want to add this resource, it is checked by default.
    *   a short name from the "Cloud provider\folder name", which the user can edit.
    *   the number of media files found inside.
    *   "In destinations" checkbox, but only for folders that are writable. It is unchecked by default. The user can check it manually for each line of the list before the next step.

    After manually adding folders, if the list is not empty, a "Add to resources" button with an "add to table" icon appears below the list.

    By clicking this button, all resources not unchecked by the user from the "to add" list are added to the program's folder (resource) list with the scan result information and the short name specified here. If a folder is specified for destinations and the destinations list is not full, a destination flag and the next available position in the queue are added to the record of such a resource. If destinations are already full (up to 10), the folder-resource is added to the resources without this flag, and a pop-up message appears for the user that the destinations are full.

    We pass the need to display warnings to the main screen.

    After that, return to the "Main Screen".

    **3.4 SFTP:**

    3.4.1 Field for the resource name on the network, field "short resource name".

    3.4.2 "user", "password", "port" fields in one line.
    If the fields are empty, the program accepts the user and password entered in the program settings. If there are no saved user and password in the program settings - they should be underlined in red, requiring the user to enter them. The port is set by default for SMTP connections, but the user can change it.

    3.4.3 "Add to destinations" checkbox. Unchecked by default.

    3.4.4 "Test" button to check access to the SFTP resource.
    As a result, the user gets a dialog with a detailed description of access to this SFTP resource. This dialog, in addition to the OK button, has a "copy to clipboard" button for the diagnostic text.

    3.4.5 "Add to resources" button with an "add to table" icon.
    By clicking this button, this SFTP resource is added to the program's folder (resource) list with the scan result information and the short name specified here. If a folder is specified for destinations and the destinations list is not full, a destination flag and the next available position in the queue are added to the record of such a resource. If destinations are already full (up to 10), the folder-resource is added to the resources without this flag, and a pop-up message appears for the user that the destinations are full.

    We allow the user to add a folder even if it is unavailable, but we pass the need to display warnings to the main screen, such as "resource unavailable" or "insufficient rights".

    After that, return to the "Main Screen".

All new folders are saved by default with the "support all media types" flags.
All new folders are saved by default with the default slideshow interval or the one taken from the program settings.

#### Resource Profile Screen
On this screen, the user sees all the data fields inherent to the folder (resource). I have listed them above in the description of the term "folder (resource)".
Here they are presented as a list of fields (for editing or selection) and texts (information).
In this resource "card", the user, of course, cannot change the resource's type, unique identifier, its creation date, the number of media files found. But the rest is available for editing. Including editing the resource address.

In this window, the user can define the slideshow duration for the folder (resource), as well as which types of media files will be considered in this folder.

The screen has "Back", "Test", "Reset", "Save" buttons.
"Back" - the usual cancellation of any changes and return to the previous screen.
"Test" - a dialog for testing the resource depending on its type, as it is performed when called from the "add and scan resources screen".
"Reset" - cancellation of all changes in the text fields that the user has made (reread from the database).
"Save" - save the changed data to the database and close this screen.

#### "Filter and Sort Resource List Screen"
This is a small dialog with "apply" and "cancel" buttons, in which the user can:

*   select a sorting option from a drop-down list (listed above), applicable to the folder (resource) name.
*   filter resource types using a block of checkboxes.
*   filter resources by supported media file types using a block of checkboxes.
*   text field "by part of the name". It is empty, but if the user enters some text here, only resources in which this text (as a substring) appears in either the short name or the full address, case-insensitively, should be selected.

When this dialog is closed with the "Apply" button - the sorting and filters are applied to the resource list on the main screen. When a filter is applied on this screen, a warning with a description of the applied filter appears at the bottom.

#### Browse Screen
This is the working screen of the program. It is quite complex, as its composition will depend on the program settings, the selected resource settings, and the access rights to the resource.
The idea is that the user sees a list or table of media files from one folder (resource). They have the ability to change the sorting, scroll, and swipe. They can select one or more media files for available operations. They can start playing a media file or a slideshow, starting from that media file.

Appearance: at the top is a control panel, a row of buttons as icons:
*   "Back"
*   a small space,
*   "Sort". Calls a dialog for selecting the sorting of media files in the folder with "apply" and "cancel". The sorting mode is initially taken by default from the program settings. And is saved for each resource.
*   "Filter". Calls a dialog for filtering media files in the folder with "apply" and "cancel". In the filter dialog, the user can enter part of the file name (case-insensitive), can enter a condition for the creation date of files >=Date;<=Date, can enter a condition for the size of media files >=Mb; <=Mb. The filtering is not saved for the resource (folder) after the user exits the Browse Screen back.
*   Grid/list view toggle button. The view is saved for the folder (resource).
*   "copy to..." button. Visible if not disabled in the settings and at least one destination is set in the program and this destination is not the current folder (resource). If no file is selected below in the list/grid - disabled. Calls the "copy to..." dialog screen.
*   "move to..." button. Visible if not disabled in the settings and at least one destination is set in the program and this destination is not the current folder (resource). And if we have write permissions in the current folder (resource), to delete the file after moving. If no file is selected below in the list/grid - disabled. Calls the "move to..." dialog screen.
*   "rename to..." button. Visible if not disabled in the settings and we have write permissions in the current folder (resource). If no file is selected below in the list/grid - disabled. Calls the "rename to..." dialog screen.
*   "delete" button. Visible if not disabled in the settings and we have write permissions in the current folder (resource). If no file is selected below in the list/grid - disabled. Calls the "delete" dialog screen.
*   "undo operation" icon button. Visible if enabled in the settings - starts the process of undoing the last operation (copy, move, rename, delete). If there is no last operation - the button is disabled.
*   a small space,
*   Button to start playback in (player screen in slideshow mode) from the currently selected media file (the first one, if none is selected) in the sequence set by sorting.
The height of the buttons is halved if the "Small controls" condition is applied in the settings.

*   a small text header with the folder (resource) name and its full address, a counter of selected media files.

Below is a list or table (grid) of media files.
For a list, each media file is represented as
*   a line with a file icon (size from settings) or, if the "Small controls" condition is applied in the settings, the user sees the media file extension icon.
*   Next in the line is the file name,
*   file size in relative units (gigabyte, megabyte, kilobyte) with 1 decimal place
*   file creation date
*   Copy icon button. Visible if not disabled in the settings and at least one destination is set in the program and this destination is not the current folder (resource).
*   "move to..." icon button. Visible if not disabled in the settings and at least one destination is set in the program and this destination is not the current folder (resource). And if we have write permissions in the current folder (resource), to delete the file after moving.
*   "rename to..." icon button. Visible if not disabled in the settings and we have write permissions in the current folder (resource).
*   "delete" icon button. Visible if not disabled in the settings and we have write permissions in the current folder (resource).
*   a small space,
*   Icon button to start playback of the currently selected media file (player screen) in the sequence set by sorting.

If the user single-taps a media file in the list on the icon/image, name, date, size, or the start playback button - this file is selected and the player screen is launched for it.

If the user long-presses a media file (but not on the buttons), then
1.  if no file was selected before, this file becomes selected, but the "player screen" is not launched for it.
2.  All files between the current and the previously selected file are added to the selection. This way, the user can scroll through the list or grid and mark all the necessary media files for an operation as selected.

Selected files are highlighted in the list. Their number is written in the text header.
If one or more files are selected, the user can press the "copy", "move", "rename", or "delete" command buttons for the selected files (depending on their availability).

#### "copy to..." dialog screen
On this screen, the user sees
1.  Header "copying N files from [source folder name]"
2.  A series of buttons from 1 to 10 from destinations depending on the number of available destinations for copying. That is, all destinations, except the current folder (resource).
3.  Cancel button.
Destination buttons in the order and with the color as specified in the recipients list.
Destination buttons are dynamically sized and occupy the available space.
When this button is pressed, the selected media file or list of media files will be copied to the recipient folder. If the process takes more than two seconds, a progress bar is needed. After copying, a pop-up message "N files copied" is needed.
Consider the copy settings to overwrite or skip files if they exist.
After copying, if several files were selected, the last one remains selected or, if the program setting "go to the next file after copying" is set, - the next one.
The form background is dark green for the dark color scheme and light green for the light one.
In case of copy errors, pop-up messages about this are needed even in the release version of the program.
The result of the copy is stored in memory until the next file operation. It is necessary to perform an "undo operation" by a special command. Any next operation is moving, renaming, deleting, exiting the list, viewing (playing) another file.

#### "move to..." dialog screen
On this screen, the user sees
1.  Header "moving N files from [source folder name]"
2.  A series of buttons from 1 to 10 from destinations depending on the number of available destinations for copying. That is, all destinations, except the current folder (resource).
3.  Cancel button.
Destination buttons in the order and with the color as specified in the recipients list.
Destination buttons are dynamically sized and occupy the available space.
When this button is pressed, the selected media file or list of media files will be moved to the recipient folder. If the process takes more than two seconds, a progress bar is needed. After copying, a pop-up message "N files copied" is needed.
Consider the move settings to overwrite or skip files if they exist.
After moving, the next file after the last selected one remains selected.
The form background is dark blue for the dark color scheme and light blue for the light one.
In case of move errors, pop-up messages about this are needed even in the release version of the program.
The result of the move is stored in memory until the next file operation. It is necessary to perform an "undo operation" by a special command. Any next operation is copying, renaming, deleting, exiting the list, viewing (playing) another file.

#### "rename to..." dialog screen
On this screen, the user sees
1.  Header "renaming N files from [source folder name]"
2.1 For a single selected file - "Name" field with the value of the current name with extension. Editable.
2.2 For multiple selected files - a list of media file names (with extensions), editable by the user.
3.  "apply" and "cancel" buttons.
When "apply" is pressed, the file or list of files is renamed (if the name was changed). If a file with that name already exists, the renaming should be skipped and a pop-up message "file [name] with that name already exists - skipped" is needed.
The form background is dark yellow for the dark color scheme and light yellow for the light one.
In case of rename errors, pop-up messages about this are needed even in the release version of the program.
The result of the renames is stored in memory until the next file operation. It is necessary to perform an "undo operation" by a special command. Any next operation is copying, moving, deleting, exiting the list, viewing (playing) another file.

#### "delete" dialog screen
The dialog is shown only if the "Confirm deletion" setting is enabled.
In the dialog for a single selected file, the request text is "Are you sure you want to delete the file [name] from [source folder name]?"
In the dialog for multiple selected files, the request text is "Are you sure you want to delete [N] files from [source folder name]?"
The form background is dark red for the dark color scheme and light red for the light one.
In case of deletion errors, pop-up messages about this are needed even in the release version of the program.
If undo operations are enabled in the settings, the file(s) are not physically deleted immediately, but only disappear from the list (grid table). But they are physically deleted on any subsequent operation, except for the undo itself. Any next operation is copying, moving, renaming, exiting the list, viewing (playing) another file.

#### Player Screen
On this screen, the selected media file is displayed (played).
When loading a file for more than one second, a loading progress bar should be shown.
After the file is loaded, as soon as the media file is played (displayed) to the user and regardless of whether the slideshow mode is on, you need to start loading the next file in the list in the background, if the next file in the list is a static or animated image. If the user goes to the next file, the already loaded file will be shown to them.

The composition of the screen elements depends on the type of file being played.
1.  For a static image:
1.1 For full-screen mode (in the program settings):
The file is displayed in full screen while maintaining the original aspect ratio of the image. When the device is tilted, the picture should also "rotate" following the device, stretched to full screen while maintaining the original aspect ratio of the image. The screen above the picture is divided into 9 touch zones. Three rows of three touch zones. The touch zones are invisible when the image is displayed, but they are shown as a map in the documentation.
1.1.1 Top left corner 30% of the screen width, 30% of the screen height - "Back" command area (white zone).
1.1.2 Top of the screen - 40% of the width after the "Back" area, 30% of the screen height - "copy" command area (green zone).
1.1.3 Top right corner of the screen - 30% of the width after the "copy" area, 30% of the screen height - "rename" command area (yellow zone).
1.1.4 Left side of the screen 30% of the screen width, 40% of the screen height after the "Back" area - "previous" command area (orange zone).
1.1.5 Center of the screen. 40% of the screen width after the "previous" zone, 40% of the height after the "copy" area - "move" command area (blue zone).
1.1.6 Right side of the screen 30% of the screen width after the "move" zone, 40% of the height after the "rename" area - "next" command area (purple zone).
1.1.7 Bottom left of the screen - 30% of the width, 30% of the screen height after the "previous" zone - "show command panel" command area (gray zone).
1.1.8 Bottom of the screen - 40% of the width after the "show command panel" zone, 30% of the screen height after the "rename" area - "delete" command area (red zone).
1.1.9 Bottom right of the screen - 30% of the width after the "delete" zone, 30% of the screen height after the "next" zone - "slideshow" command area (pink zone).

Description of the program's reaction when tapping on the areas:
"Back" - back to the Browse Screen.
"Copy" - call the "copy to..." dialog screen for the currently displayed file
"Previous" - transition (display, playback of the previous media file) in the resource's file list according to its accepted sorting.
"Move" - call the "move to..." dialog screen for the currently displayed file
"Next" - transition (display, playback of the next media file) in the resource's file list according to its accepted sorting.
"Show command panel". The image display switches to the "with command panel" view.
"Delete". Call the "delete" dialog screen for the currently displayed file.
"Slideshow" - a pop-up window with a message that slideshow mode is enabled with the specified interval. Start changing media files in slideshow mode. Stop slideshow mode - by tapping any area of the screen except Next. In slideshow mode, the "next" button displays the next file, resets the interval countdown, but does not stop the slideshow.

1.2 For "with command panel" mode
In this mode, the image does not occupy the entire screen. A command panel appears above the image: "Back", "previous", "next", a small indent, "rename", "delete", "undo", a small indent, "slideshow".

And under the image, two more command panels appear "Copy to..." and "Move to...".
Such panels are from 1 to 10 buttons depending on the number of available recipients for copying. No more than 5 buttons in the screen width
If there is one button - for the full width of the screen.
If there are two buttons - for 1/2 of the screen width each.
If there are three buttons - for 1/3 of the screen width each.
If there are four buttons - for 1/4 of the screen width each.
If there are five buttons - for 1/5 of the screen width each.
If there are six buttons - two rows of three buttons at 1/3 of the screen width each.
If there are seven buttons - two rows (four and three buttons) at 1/4 and 1/3 of the screen width respectively.
If there are eight buttons - two rows of four buttons at 1/4 of the screen width each.
If there are nine buttons - two rows (five and four buttons) at 1/5 and 1/4 of the screen width respectively.
If there are ten buttons - two rows of five buttons at 1/5 of the screen width each.
Color and order from the recipients list.
One panel with the title "copy to...".
The second panel with the title "rename to...".
The titles and text of the buttons should be small and save more space for displaying the media file.

In this mode, the image itself is covered by only two touch zones. The left half is the "previous" zone. The right half is the "next" zone.

If the "small interface elements" setting is enabled - all buttons are half the height, giving more space to the image.

2.  For an animated image:
Everything is the same as for a static image, but the animation plays without stopping.

3.  For video:
3.1 Full-screen playback (the "show command panel" setting is disabled)
The video is played by the player, the video player control commands appear when you tap on the video file, but in the lower area.
Controls: Standard set (Play/Pause, scroll bar)
Consequently, the touch zones (described for images) do not cover the entire screen area, but are proportionally reduced to only 75% of the height.

3.2 For "with command panel" mode
In this mode, the video playback area does not occupy the entire screen. A command panel appears above it (as described for a static image) and below it, groups of commands "copy to" and "move to" are formed (as described for a static image). The video player area is covered by two touch zones "previous" and "next", but only the top 50% of the video player area, so that in the lower half the user has the opportunity to control the internal controls of the video player.

4.  For audio:
I will describe my wishes for the audio player - it is desirable that the interface be close to the video player. At the bottom there was a control of internal commands, and above it - a list of various characteristics of the media file and audio stream.
Controls: Standard set (Play/Pause, scroll bar). Accordingly, the command areas and touch zones will be the same as for the video player.

In slideshow mode, media files are displayed one after another at a given interval. Three seconds before switching to the next file, the labels "3..", "2..", "1.." appear in the upper right corner before the file changes.
Video and audio files, according to the set setting, must play to the end before switching to the next file, the playback of which starts automatically.

Of course, all operation commands (copy, move, rename, delete, undo) should be visible and available only if they are enabled in the settings and can be applied to the current file (if there are recipients and the necessary access rights).


#### Settings Screen

Program settings screen.
The transition between settings tabs is instantaneous, without scrolling.
The settings are divided into tabs "General", "Media Files", "Playback and Sorting", "Destinations". When the settings are first launched, the user starts with the "General" tab. Then the program stores which tab the user left this screen from, and opens it on launch.

**1. General**

1.1 Program language. Choice between English, Russian, Ukrainian. If the user has changed the language - they should be asked to "restart" the application.

1.2 "Not allow device to sleep" checkbox. Enabled by default. With this setting, the program does not allow the device to go into lock (sleep) mode while it is running.

1.3 "Show small controls" checkbox. Disabled by default.

1.4 "Default User" and "Default Password" fields. If they were not set (empty for a network folder (resource) or SFTP), the program applies them from the settings.

1.5 Button to call the User Guide, which will open the Welcome Screen.

1.6 Button to call the dialog for obtaining rights "to work with local Android files".
*   After pressing and the button working, the user is asked for permission to restart the program.
*   If the right is already there, the button is inactive (Disabled).

1.7 Button to call the dialog for obtaining rights "to work with the external SFTP network".
*   After pressing and the button working, the user is asked for permission to restart the program.
*   If the right is already there, the button is inactive (Disabled).

1.8 "Show log" button. Calls a dialog with a text field, which gets the full log, limited to either the last three sessions or 512 lines. With "Copy to clipboard" and "Close" buttons.

1.9 "Show current session log" button with "Copy to clipboard" and "Close" buttons.

1.10 Text description of the current version of the program, build version, interactive e-mail of the developer - sza@ukr.net

**2. Media Files**

2.1 "Support static images" checkbox with a list of all supported types.
Enabled by default.
Under it, a subordinate field "Image size limit". A double slider with minimum and maximum values is needed here. The slider is from 0 to 1Gb. By default, it is selected from 1Kb to 10Mb.
The set limit is applied when scanning the number of media files and when playing them.

2.2 "Support GIF animation" checkbox.
Disabled by default.

2.3 "Support video" checkbox with a list of all supported formats.
Enabled by default.
Under it, a subordinate field "Video file size limit". A double slider with minimum and maximum values is needed here. The slider is from 0 to 1Tb. By default, it is selected from 100Kb to 1Gb.
The set limit is applied when scanning the number of media files and when playing them.

2.4 "Support audio" checkbox with a list of all supported formats.
Enabled by default.
Under it, a subordinate field "Audio file size limit". A double slider with minimum and maximum values is needed here. The slider is from 0 to 10Gb. By default, it is selected from 10Kb to 100Mb.
The set limit is applied when scanning the number of media files and when playing them.

**3. Playback and Sorting**
3.1 Default media file sorting mode during playback. Default: by name.

3.2 Default slideshow interval in seconds. From 1 to 3600. When set, it recalculates into minutes and displays it as text next to it.

3.3 "Play video and audio to the end before showing the next file in slideshow mode" checkbox.

3.4 "Allow renaming" checkbox. Enabled by default.

3.5 "Allow deletion" checkbox. Enabled by default.
Plus a subordinate checkbox "Ask for user confirmation before deleting". Enabled by default.

3.6 "Media files as a grid by default" checkbox. Disabled by default.

3.7 Default file icon size (and also grid cell). Initial value - 100 by 100.

3.8 "Display media files in full-screen mode (otherwise - with a command panel)" checkbox. Enabled by default.

3.9 "Show detailed error messages for operations and media file playback" checkbox. Disabled by default.
If this checkbox is enabled, then instead of a pop-up error message, the user will be shown a dialog window with a detailed text of the current state and an error message with "copy to clipboard" and "close" buttons.

**4. Destinations.**

4.1 "Enable copying" checkbox. Enabled by default.
Subordinate checkboxes:
 "Go to the next file after copying". Enabled by default.
 "Overwrite existing file when copying". Disabled by default.

4.2 "Enable moving" checkbox. Enabled by default.
Subordinate checkbox: "Overwrite existing file when moving". Disabled by default.

4.3 A numbered list of up to 10 items. Numbered from 0 to 9. These are recipient resources. Here they can be moved up and down in the list and deleted. Also, with the "Add recipient" button, you can select it from the number of registered resources (if this resource is not yet selected as a recipient). This button is available only if there are less than 10 recipient resources. If the list of recipients does not fit on the screen, a scroll bar appears.
Each recipient has its own color, according to its number. This color will highlight the buttons in the media file playback mode. Here we see the short name of the resource, which will be written on the buttons, and the full address of the resource.

---

### 1.3. Functional Specification

See file V2_p1_3.md

### 1.4. Non-Functional Specification

See file V2_p1_4.md

### 1.5. Technology Stack Selection

See file V2_p1_5.md

### 1.6. Risk Assessment

technical — compatibility with Android 9+, network failures
non-legal — data processing in the cloud
time-related — ExoPlayer integration delays

## Phase 2: Environment and Repository Setup

### 2.1. Repository Setup

Described in file V2_p2_1.md

### 2.2. Development Environment Setup

Described in file V2_p2_2.md

### 2.3. Project Structure Initialization

Described in file V2_p2_3.md

### 2.4. CI/CD Pipeline Setup

Described in file V2_p2_4.md

### 2.5. Development Tools Configuration

Described in file V2_p2_5.md

## Phase 3: Detailed Development Planning

### 3.1. Architecture Design

Described in file V2_p3_1.md

### 3.2. Milestone Planning

Described in file V2_p3_2.md

### 3.3. Data Model Design

Described in file V2_p3_3.md

### 3.4. Documentation Planning

Described in file V2_p3_4.md

## Phase 4: Implementation and Iteration

### 4.1. Core Implementation

TODO:

### 4.2. Reference v1 Code

Use v1 as a reference.

## Phase 5: Testing, Release, and Maintenance

### 5.1. Quality Assurance

TODO:

### 5.2. Release Preparation

TODO:

### 5.3. Launch and Post-Launch

TODO:
