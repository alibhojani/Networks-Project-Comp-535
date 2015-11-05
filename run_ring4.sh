#!/bin/bash
killall java
java -cp target/*.jar socs.network.Main conf/router1.conf tests/ring4/router1.txt &
sleep 1
java -cp target/*.jar socs.network.Main conf/router2.conf tests/ring4/router2.txt &
sleep 1
java -cp target/*.jar socs.network.Main conf/router3.conf tests/ring4/router3.txt &
sleep 1
java -cp target/*.jar socs.network.Main conf/router3.conf tests/ring4/router4.txt &
