package de.tum.flexsmc.smc.aggregator;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.value.OInt;

public interface AggregatorApplication extends Application {
	OInt[] getResult();
}
