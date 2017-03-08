#!/bin/bash
# Cleanup any running left-over.
ps aux | grep "[j]ava -Djava.util.logging.config.file=logging.properties -cp target/flexsmc-fresco" | awk '{print $2}' | xargs kill
# Start instances in background with logging to /tmp (overwrites previous logs).
fresco_instances=${1:-1}
until [  $fresco_instances -lt 1 ]; do
	echo "Starting instance ${fresco_instances}"
	nohup java -Djava.util.logging.config.file="logging.properties" -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "localhost:1313${fresco_instances}" 2>&1> /tmp/flexsmc-fresco-s${fresco_instances}.log &
	let fresco_instances-=1
done

exit 0
