#!/bin/bash


for i in `seq $1`
do

java -cp ../target/gaulish_olympics_two-1.0-SNAPSHOT-jar-with-dependencies.jar so.modernized.dos.SpawnRandomTablet 127.0.0.1 2552 $2 $3 "Rome|Gaul|Carthage" "Curling|Piathlon|Bayesball" > tablet-$i-of-$1-at-$2-asks-$3-total.log &

done
