package de.tum.flexsmc.smc;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import de.tum.flexsmc.smc.rpc.RPCServer;

public final class CLIMain {
	private static final Logger logger = Logger.getLogger(RPCServer.class.getName());

	private CommandLine cmd;

	public CLIMain() {

	}

	public CommandLine parse(String[] args) {
		try {
			CommandLineParser parser = new DefaultParser();
			Options helpOpt = new Options();
			helpOpt.addOption(Option.builder("h").desc("Display this help message").longOpt("help").required(false)
					.hasArg(false).build());

			cmd = parser.parse(helpOpt, args, true);
			if (cmd.hasOption("h")) {
				System.exit(0);
			}
	
		} catch (ParseException e) {
			logger.severe("Could not parse command: " + e.getMessage());
			System.exit(1);
		}
		return cmd;
	}

	public static void main(String[] args) {
		CLIMain cli = new CLIMain();
		cli.parse(args);
		
		// Start RPC server
		RPCServer rpcServer = new RPCServer();
		try {
			rpcServer.start();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
