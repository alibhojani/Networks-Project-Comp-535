#!/bin/bash
killall java
java -cp target/*.jar socs.network.Main conf/router1.conf tests/ring3/router1.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router2.conf tests/ring3/router2.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router3.conf tests/ring3/router3.txt &