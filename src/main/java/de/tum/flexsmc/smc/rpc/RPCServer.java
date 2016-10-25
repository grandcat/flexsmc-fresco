package de.tum.flexsmc.smc.rpc;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.logging.Logger;

import de.tum.flexsmc.smc.engine.BgwEngine;
import de.tum.flexsmc.smc.rpc.SMCGrpc.SMCImplBase;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.benchmarks.Utils;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;

public class RPCServer {
	private static final Logger logger = Logger.getLogger(RPCServer.class.getName());

	private final SocketAddress LISTENER_SOCKET = Utils.parseSocketAddress("unix:///tmp/grpc.sock");
	private int port = 50052;
	private Server server;

	public RPCServer() {
		// TODO Auto-generated constructor stub
	}

	public void start() throws IOException {
		// this.server = ServerBuilder.forPort(port).addService(new
		// SMCImpl()).build().start();
		
		// Unix socket properties (Linux only)
		final EventLoopGroup boss;
		final EventLoopGroup worker;
		final Class<? extends ServerChannel> channelType;
		try {
	        Class<?> groupClass = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
	        @SuppressWarnings("unchecked")
	        Class<? extends ServerChannel> channelClass = (Class<? extends ServerChannel>)
	            Class.forName("io.netty.channel.epoll.EpollServerDomainSocketChannel");
			boss = (EventLoopGroup) groupClass.getConstructor().newInstance();
			worker = (EventLoopGroup) groupClass.getConstructor().newInstance();
			channelType = channelClass;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		this.server = NettyServerBuilder.forAddress(LISTENER_SOCKET).bossEventLoopGroup(boss)
.workerEventLoopGroup(worker)
				.channelType(channelType)
				.addService(ServerInterceptors.intercept(new SMCImpl(), new SessionInterceptor())).build().start();
		logger.info("RPC server started, listening on socket " + LISTENER_SOCKET.toString());
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				// Use stderr here since the logger may have been reset by its
				// JVM shutdown hook.
				System.err.println("*** shutting down gRPC server since JVM is shutting down");
				RPCServer.this.stop();
				System.err.println("*** server shut down");
			}
		});
	}

	/**
	 * Await termination on the main thread since the grpc library uses daemon
	 * threads.
	 */
	public void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	private void stop() {
		if (server != null) {
			server.shutdown();
		}
	}

	// For testing the cross language support
	public static void main(String[] args) throws IOException, InterruptedException {
		final RPCServer server = new RPCServer();
		server.start();
		server.blockUntilShutdown();
	}

	private class SMCImpl extends SMCImplBase {
		private final CmdResult errorInvalidSession = CmdResult.newBuilder().setMsg("Session ID not allowed")
				.setStatus(CmdResult.Status.DENIED)
				.build();

		private HashMap<String, Object> sessions;

		public SMCImpl() {
			// Initialize
			sessions = new HashMap<>();
		}

		@Override
		public void init(SessionCtx req, StreamObserver<CmdResult> responseObserver) {
			// Initiate new session if session ID is not already in use.
			if (sessions.containsKey(req.getSessionID())) {
				responseObserver.onNext(errorInvalidSession);
				responseObserver.onCompleted();
				return;
			}
			String sessionID = req.getSessionID();
			sessions.put(sessionID, new BgwEngine());
			// Prepare Fresco
			// ...

			// Reply to callee
			CmdResult reply = CmdResult.newBuilder().setMsg("[" + sessionID + "] init done.")
					.setStatus(CmdResult.Status.SUCCESS).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void doPrepare(PreparePhase req, StreamObserver<CmdResult> responseObserver) {
			// Extract current session from ID
			String sessionID = SessionInterceptor.SESSION_ID.get();
			logger.info("Current session: " + sessionID);
			// Fetch associated engine
			BgwEngine eng = (BgwEngine) sessions.get(sessionID);
			if (eng == null) {
				responseObserver.onNext(errorInvalidSession);
				responseObserver.onCompleted();
				return;
			}

			try {
				eng.initializeConfig(req.getParticipantsList());
				eng.prepareSCE();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Reply
			CmdResult reply = CmdResult.newBuilder().setMsg("[" + sessionID + "] doPrepare done.")
					.setStatus(CmdResult.Status.SUCCESS).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}
	}

}