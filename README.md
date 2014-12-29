# Backup

## statement

Backup is a utility that helps me copy directories to my backup hard-drives in a systematic manner. Some properties
are currently hard-coded, so without making modifications in the source-code, this is probably not useful for
anyone but myself.

This project is (C)opyright 2014-2015 by Hanns Holger Rutz. All rights reserved. It is released under
the [GNU General Public License](https://raw.github.com/Sciss/Backup/master/LICENSE) v3+ and comes with absolutely
no warranties. To contact the author, send an email to `contact at sciss.de`

## getting started

To run, use `sbt run`. Drag-and-drop a folder onto the field in the window's top, or press the "DVD" button to
make a backup of the inserted DVD (assumes default Linux paths). You will be asked for your password to enable
use of `sudo` for the backup hard-disk which should be write-protected for normal users.