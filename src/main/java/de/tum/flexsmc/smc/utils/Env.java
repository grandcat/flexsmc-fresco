package de.tum.flexsmc.smc.utils;

public class Env {

	/**
	 * Code borrowed from FRESCO CmdLineUtil (private func).
	 * 
	 * @return Number of threads that can be used for SMC computation
	 */
	public static int getDefaultNoOfThreads() {
		int n = Runtime.getRuntime().availableProcessors();
		if (n == 1) {
			return 1;
		}
		return n - 1;
	}

}
