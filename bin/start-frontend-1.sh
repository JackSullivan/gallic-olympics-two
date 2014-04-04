#!/bin/bash

java -classpath ../target/gaulish_olympics_two-1.0-SNAPSHOT-jar-with-dependencies.jar so.modernized.dos.FrontendProcess 'akka.tcp://db@127.0.0.1:1337' 1
