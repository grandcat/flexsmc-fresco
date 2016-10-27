#!/bin/bash
COUNTER=3
until [  $COUNTER -lt 1 ]; do
	echo COUNTER $COUNTER
	java -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "unix:///tmp/grpc${COUNTER}.sock" &
	let COUNTER-=1
done
#java -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "unix:///tmp/grpc1.sock" &
#java -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "unix:///tmp/grpc2.sock" &
#java -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "unix:///tmp/grpc3.sock" &
