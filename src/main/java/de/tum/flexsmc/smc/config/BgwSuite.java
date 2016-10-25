package de.tum.flexsmc.smc.config;

import java.math.BigInteger;
import java.util.logging.Logger;

import de.tum.flexsmc.smc.rpc.RPCServer;
import dk.alexandra.fresco.framework.sce.configuration.SCEConfiguration;
import dk.alexandra.fresco.suite.bgw.configuration.BgwConfiguration;

public class BgwSuite implements BgwConfiguration {
	private static final Logger logger = Logger.getLogger(RPCServer.class.getName());
	private static BigInteger DEFAULT_MODULUS = new BigInteger("618970019642690137449562111");

	private int threshold;
	private BigInteger modulus;
	
	public BgwSuite(SCEConfiguration sceConf) {
		modulus = DEFAULT_MODULUS;
		// Allow < n/2 parties to be corrupt
		threshold = ((int) Math.ceil((double) sceConf.getParties().size() / 2.0)) - 1;
		if (threshold < 0) {
			threshold = 0;
			logger.severe("BGW: not enough parties to provide any protection for corruption");
		}
	}

	public BgwSuite(int threshold) {
		this.threshold = threshold;
		modulus = DEFAULT_MODULUS;
	}

	public BgwSuite(int threshold, BigInteger modulus) {
		this.threshold = threshold;
		this.modulus = modulus;
	}

	@Override
	public int getThreshold() {
		// TODO Auto-generated method stub
		return threshold;
	}

	@Override
	public BigInteger getModulus() {
		// TODO Auto-generated method stub
		return modulus;
	}

}
