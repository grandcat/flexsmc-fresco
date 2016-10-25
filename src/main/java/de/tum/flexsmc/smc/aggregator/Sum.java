package de.tum.flexsmc.smc.aggregator;

import java.math.BigInteger;
import java.util.logging.Level;

import dk.alexandra.fresco.framework.ProtocolFactory;
import dk.alexandra.fresco.framework.ProtocolProducer;
import dk.alexandra.fresco.framework.Reporter;
import dk.alexandra.fresco.framework.sce.configuration.SCEConfiguration;
import dk.alexandra.fresco.framework.value.OInt;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.field.integer.BasicNumericFactory;
import dk.alexandra.fresco.lib.helper.builder.NumericIOBuilder;
import dk.alexandra.fresco.lib.helper.builder.NumericProtocolBuilder;
import dk.alexandra.fresco.lib.helper.sequential.SequentialProtocolProducer;

/**
 * Sum is a FRESCO application calculating the sum of all secret values among
 * all parties. By default, the summed shares are distributed to all peers so
 * each of them is able to calculate the outcome of the sum computation.
 * 
 * @author stefan
 *
 */
public class Sum implements AggregatorApplication {
	private static final long serialVersionUID = -24333164590218997L;

	private SCEConfiguration sceConf;

	private BigInteger myInput;
	private OInt[] result;

	public Sum(SCEConfiguration sceConf) {
		this.sceConf = sceConf;
		// XXX: testing
		this.myInput = BigInteger.valueOf(sceConf.getMyId() * 2);
	}

	@Override
	public ProtocolProducer prepareApplication(ProtocolFactory factory) {
		Reporter.init(Level.INFO);
		Reporter.info(">>>>> I am player " + sceConf.getMyId());

		BasicNumericFactory fac = (BasicNumericFactory) factory;
		NumericIOBuilder ioBuilder = new NumericIOBuilder(fac);
		NumericProtocolBuilder npb = new NumericProtocolBuilder(fac);

		final int numPeers = sceConf.getParties().size();

		// Create wires for retrieving the others' shared secret part and ours
		// one
		SInt[] inputSharings = new SInt[numPeers];

		// Closing protocol: what happens behind the scene
		// for (int i = 0; i < inputSharings.length; i++) {
		// inputSharings[i] = fac.getSInt();
		// }

		// Each participant provides a secret value which is shared secretly
		// among the other parties
		// ParallelProtocolProducer shareInputPar = new
		// ParallelProtocolProducer();
		// OInt oi = fac.getOInt();
		// oi.setValue(myValue);
		// for (int p = 1; p <= numPeers; p++) {
		// shareInputPar.append(fac.getCloseProtocol(p, oi, inputSharings[p -
		// 1]));
		// }

		ioBuilder.beginParScope();
		for (int p = 1; p <= numPeers; p++) {
			inputSharings[p - 1] = ioBuilder.input(myInput, p);
		}
		ioBuilder.endCurScope();
		ProtocolProducer closeInputProtocol = (SequentialProtocolProducer) ioBuilder.getProtocol();
		ioBuilder.reset();

		// 2. Protocol: summing up all received shared secrets and one part of
		// our own one
		// This works locally due to the linear properties of the shared secrets

		// Behind the scene: create sequence of protocols which will compute the
		// sum
		// SInt ssum = fac.getSInt();
		// SequentialProtocolProducer sumProtocol = new
		// SequentialProtocolProducer();
		// sumProtocol.append(fac.getAddProtocol(inputSharings[0],
		// inputSharings[1], sum1));
		//
		// if (inputSharings.length > 2) {
		// for (int i = 2; i < inputSharings.length; i++) {
		// // Add sum and next secret shared input and
		// // store in sum.
		// sumProtocol.append(fac.getAddProtocol(sum1,
		// inputSharings[i], sum1));
		// }
		// }

		SInt ssum = npb.sum(inputSharings);
		ProtocolProducer sumProtocol = npb.getProtocol();

		this.result = new OInt[] { ioBuilder.output(ssum) };
		ProtocolProducer openProtocol = ioBuilder.getProtocol();

		ProtocolProducer gp = new SequentialProtocolProducer(closeInputProtocol, sumProtocol, openProtocol);
		return gp;
	}

	@Override
	public OInt[] getResult() {
		return result;
	}

}
