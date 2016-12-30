package de.tum.flexsmc.smc;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import de.tum.flexsmc.smc.rpc.RPCServer;

public final class CLIMain {
	// Control logging behavior of io.netty that is part of gRPC.
	static {
		System.setProperty("logback.configurationFile", "logback.xml");
	}

	private static final Logger logger = Logger.getLogger(CLIMain.class.getName());

	private Options opt;
	private CommandLine cmd;

	public CLIMain() {
	}

	private static Options buildOptions() {
		Options options = new Options();

		options.addOption(Option.builder("i").desc("The id of this player. Must be a unique positive integer.")
				.longOpt("id").required(false).hasArg().build());

		options.addOption(Option.builder("c")
				.desc("Custom socket address to listen for local RPC connections. E.g. \"unix:///tmp/grpc.sock\"")
				.longOpt("suite").required(false).hasArg().build());
		options.addOption(Option.builder("h").desc("Display this help message").longOpt("help").required(false)
				.hasArg(false).build());

		return options;
	}

	public CommandLine parse(String[] args) {
		try {
			CommandLineParser parser = new DefaultParser();

			opt = buildOptions();
			cmd = parser.parse(opt, args, true);
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
		RPCServer rpcServer = new RPCServer();

		CommandLine cmd = cli.parse(args);
		if (cmd.hasOption('c')) {
			// Custom socket
			logger.info("Custom socket: " + cmd.getOptionValue('c'));
			rpcServer.setCustomSocket(cmd.getOptionValue('c'));
		}
		
		// Start RPC server
		try {
			rpcServer.start();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
