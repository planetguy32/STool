#!/bin/sh

#Note: I manually extracted the SQL jar into build/classes.

./gradlew build
scp build/libs/YLMCJ-1.7.10-0.1.X.jar craftadmin@planetguy.sandcats.io:~/mcservers/SMPVP2/mods
