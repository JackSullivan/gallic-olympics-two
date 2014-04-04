#!/bin/bash

java -classpath ../target/gaulish_olympics_two-1.0-SNAPSHOT-jar-with-dependencies.jar so.modernized.dos.RequestRouterProcess 'akka.tcp://frontend-1@127.0.0.1:2557|akka.tcp://frontend-2@127.0.0.1:2558' 
