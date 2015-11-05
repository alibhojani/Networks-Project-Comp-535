#!/bin/bash
killall java
java -cp target/*.jar socs.network.Main conf/router1.conf tests/ring/router1.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router2.conf tests/ring/router2.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router3.conf tests/ring/router3.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router4.conf tests/ring/router4.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router5.conf tests/ring/router5.txt &
sleep 2
java -cp target/*.jar socs.network.Main conf/router6.conf tests/ring/router6.txt &