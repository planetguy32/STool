#!/bin/sh

#Note: I manually extracted the SQL jar into build/classes.

./gradlew build
scp build/libs/STool-1.7.10-$1.X.jar craftadmin@planetguy.sandcats.io:~/mcservers/
