package de.tum.flexsmc.smc.engine;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.ArrayUtils;

import de.tum.flexsmc.smc.aggregator.AggregatorApplication;
import de.tum.flexsmc.smc.aggregator.Sum;
import de.tum.flexsmc.smc.config.BgwSuite;
import de.tum.flexsmc.smc.rpc.Aggregator;
import de.tum.flexsmc.smc.rpc.CmdResult;
import de.tum.flexsmc.smc.rpc.PreparePhase;
import de.tum.flexsmc.smc.rpc.SMCResult;
import de.tum.flexsmc.smc.utils.Env;
import dk.alexandra.fresco.framework.MPCException;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.sce.SCE;
import dk.alexandra.fresco.framework.sce.SCEFactory;
import dk.alexandra.fresco.framework.sce.configuration.ProtocolSuiteConfiguration;
import dk.alexandra.fresco.framework.sce.configuration.SCEConfiguration;
import dk.alexandra.fresco.framework.sce.evaluator.SequentialEvaluator;
import dk.alexandra.fresco.framework.sce.resources.storage.InMemoryStorage;
import dk.alexandra.fresco.framework.sce.resources.storage.Storage;
import dk.alexandra.fresco.framework.sce.resources.storage.StreamedStorage;
import dk.alexandra.fresco.framework.value.OInt;

/**
 * BgwEngine controls all settings to employ a SMC round based on the BGW
 * protocol suite. Basically, it is the place where all parts find together. The
 * RPC input specifies the parameters and controls the SMC phases.
 * 
 * @author stefan
 *
 */
public class BgwEngine extends EngineControl {
	private static final Logger l = Logger.getLogger(BgwEngine.class.getName());
	
	public static final Aggregator[] supportedAggregators = {Aggregator.SUM};

	private SCEConfiguration sceConf;
	private ProtocolSuiteConfiguration suiteConf;
	private SCE smcEngine;

	private AggregatorApplication frescoApp;

	public BgwEngine() {
	}
	
	private void verifyTaskRequirements() throws RuntimeException {
		// Task object must already be set by EngineControl.
		
		// Aggregator support
		if (!ArrayUtils.contains(supportedAggregators, task.getAggregator())) {
			throw new SmcException("aggregator not supported", CmdResult.Status.UNKNOWN_CMD);
		}
	}

	private void initializeConfig(int myId, List<PreparePhase.Participant> participants) throws RuntimeException {
		l.fine("Initialize config: I am ID " + myId + " among other " + participants.size());
		if (myId < 0) {
			throw new IllegalArgumentException("Invalid participants or IDs");

		} else if (participants.size() < 2) {
			throw new IllegalArgumentException("Not enough participants");
		}

		HashMap<Integer, Party> parties = new HashMap<>(participants.size());
		for (PreparePhase.Participant p : participants) {
			// Extract address and port from Endpoint (addr:port)
			String ep = p.getEndpoint();
			int sep = ep.lastIndexOf(':');
			if (sep < 0) {
				throw new IllegalArgumentException("Invalid endpoint address");
			}
			String addr = ep.substring(0, sep);
			// TODO verify if myId matches given address (possibly spoofing?)
			// TODO verify availability of chosen port
			int port = Integer.parseUnsignedInt(ep.substring(sep + 1));
			// Store party
			l.fine("BgwEngine: party " + new Party(p.getSmcPeerID(), addr, port).toString());
			parties.put(p.getSmcPeerID(), new Party(p.getSmcPeerID(), addr, port));
		}

		final Storage storage = new InMemoryStorage();

		this.sceConf = new SCEConfiguration() {
			@Override
			public int getMyId() {
				return myId;
			}

			@Override
			public String getProtocolSuiteName() {
				return "bgw";
			}

			@Override
			public Map<Integer, Party> getParties() {
				return parties;
			}

			@Override
			public Level getLogLevel() {
				return Level.SEVERE;
			}

			@Override
			public int getNoOfThreads() {
				return Env.getDefaultNoOfThreads();
			}

			@Override
			public int getNoOfVMThreads() {
				return Env.getDefaultNoOfThreads();
			}

			@Override
			public ProtocolEvaluator getEvaluator() {
				return new SequentialEvaluator();
			}

			@Override
			public Storage getStorage() {
				return storage;
			}

			@Override
			public int getMaxBatchSize() {
				return 4096;
			}

			@Override
			public StreamedStorage getStreamedStorage() {
				if (storage instanceof StreamedStorage) {
					return (StreamedStorage) storage;
				} else {
					return null;
				}
			}
		};

		// Initialize BGW suite configuration
		this.suiteConf = new BgwSuite(sceConf);
	}

	/**
	 * Creates a FRESCO SCE engine and does all preliminary steps for setup
	 * (e.g. generate randoms, init memory pool, ...)
	 * 
	 * @throws IOException
	 */
	@Override
	public void prepare(int myId, List<PreparePhase.Participant> participants) throws RuntimeException, IOException {
		verifyTaskRequirements();
		l.finer("Task verification done");
		
		initializeConfig(myId, participants);
		this.smcEngine = SCEFactory.getSCEFromConfiguration(sceConf, suiteConf);
		// XXX: allows to kill some nodes in a critical phase while debugging
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		l.fine("Initialize SCE prepare done");
		
		// Initialize all resources and network channels
		// smcEngine.setup();
	}
	
	@Override
	public void linkPeers() {
		// Do last preparation for Fresco application.
		// Normally, loading the application is part of the Session phase.
		// Putting it here renders the Session phase minimal with respect to
		// overhead. This allows more precise measurements.
		switch (this.task.getAggregator()) {
		case SUM:
			frescoApp = new Sum(sceConf);
			break;

		default:
			// Should not reach this code. Normally checked in Prepare phase.
			throw new SmcException("aggregator not supported", CmdResult.Status.ABORTED);
		}
		
		// Connect all Fresco peers with each other.
		try {
			smcEngine.setup();
		} catch (IOException e) {
			throw new MPCException("Could not setup SMC peers: " + e.getMessage());
		}
	}

	@Override
	public SMCResult runSession() {
		// Interconnect peers here if LinkingPhase was not executed before.
		if (frescoApp == null) {
			linkPeers();
		}
		// Run loaded application.
		l.finer("Start: smcEngine.runApplication");
		smcEngine.runApplication(frescoApp);
		l.finer("Done: smcEngine.runApplication");
		// SMC is done here, so fetch the result
		OInt[] res = frescoApp.getResult();
		l.info("Session done with result: " + res[0].getValue().toString());

		SMCResult msg = SMCResult.newBuilder().setRes(res[0].getValue().doubleValue()).build();
		return msg;
	}

	public void stopAndInvalidate() {
		l.fine("Engine shutdown invoked...");
		if (this.smcEngine != null) {
			this.smcEngine.shutdownSCE();
		}
		this.suiteConf = null;
		this.sceConf = null;
	}

}
