#!/bin/bash
java -cp target/*.jar socs.network.Main conf/router1.conf router1.txt &
java -cp target/*.jar socs.network.Main conf/router2.conf router2.txt &
java -cp target/*.jar socs.network.Main conf/router3.conf router3.txt &
java -cp target/*.jar socs.network.Main conf/router4.conf router4.txt &
java -cp target/*.jar socs.network.Main conf/router5.conf router5.txt &
java -cp target/*.jar socs.network.Main conf/router6.conf router6.txt &