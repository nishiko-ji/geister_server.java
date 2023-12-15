#!/bin/sh

ip=$1
port=$2
times=$3
i=0
ai="java -cp build/libs/geister.jar net.wasamon.geister.player.RandomPlayer"
while [ $i -lt $times ]
do
    $ai $ip $port
    i=`expr $i + 1`
done

i=0
while [ $i -lt $times ]
do
  if [ $port = "10000" ]; then
    $ai $ip 10001
  fi
  if [ $port = "10001" ]; then
    $ai $ip 10000
  fi
    i=`expr $i + 1`
done
