when we open player (for any file) we have in left upper corner the name of the file and counter (1/6) its very useful. but the problem is next - for many files (like documents) itmay close some information (for examle the first line of the text)
from other hand - I dont want to shift player content down and save full line just for this infomation. Lets auto-hide this visual information after show

- for TXT after 5 seconds
- FOR EPUB and PDF - after 10 seconds
- FOR VIDEO  - after 15 seconds
- FOR IMAGES (GIF) - after 15 seconds
- FOR AUDIO - after 15 seconds

maybe later ill readjust this values

the idea is next - after we show the file - we show its name and start counting. After timeout - hide it. Once person switch to next file - we show it again for another file and timeout depend on type of file
special cases:
for video and audio (not fullscreen). If user pause video and filename with counter already disapper- we show it for timeout. if not disappear - we add time into going timeout

for video and audio (not fullscreen). If user pause video and filename with counter already disappears- we show it for timeout. if not disappear - we add time into going timeout

for images (not fullscreen). If user zoom/unzoom image and filename with counter already disappears- we show it for timeout. if not disappear - we add time into going timeout

why - people need to use sorting commands - they must know the filename they have to operate with
