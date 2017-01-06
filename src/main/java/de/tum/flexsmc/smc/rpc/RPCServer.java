package de.tum.flexsmc.smc.rpc;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
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
	private static final Logger l = Logger.getLogger(RPCServer.class.getName());

	private SocketAddress listenerSocket = Utils.parseSocketAddress("unix:///tmp/grpc.sock");
	private Server server;

	public RPCServer() {
		// TODO Auto-generated constructor stub
	}

	public void setCustomSocket(String socket) {
		this.listenerSocket = Utils.parseSocketAddress(socket);
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
		
		this.server = NettyServerBuilder.forAddress(listenerSocket).bossEventLoopGroup(boss)
.workerEventLoopGroup(worker)
				.channelType(channelType)
				.addService(ServerInterceptors.intercept(new SMCImpl(), new SessionInterceptor())).build().start();
		l.info("RPC server started, listening on socket " + listenerSocket.toString());
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
		private ConcurrentHashMap<String, BgwEngine> sessions;

		public SMCImpl() {
			// Initialize
			sessions = new ConcurrentHashMap<>();
		}
		
		@Override
		public void resetAll(FilterArgs req, StreamObserver<CmdResult> responseObserver) {
			for (String sessionID : sessions.keySet()) {
				l.finer("Start shutting down session: " + sessionID);
				gracefulTearDown(sessionID);
			}
			// Reset should always work. If sessions are still running, the
			// corresponding
			// functions should throw an error to their caller instead.
			l.finer("DONE shutting down resetAll");
			CmdResult reply = CmdResult.newBuilder().setMsg("reset done.").setStatus(CmdResult.Status.SUCCESS).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void init(SessionCtx req, StreamObserver<CmdResult> responseObserver) {
			// Initiate new session if session ID is not already in use.
			String sessionID = req.getSessionID();
			if (sessions.containsKey(sessionID)) {
				l.warning("[" + sessionID + "] already exists!");
				responseObserver.onNext(errorInvalidSession);
				responseObserver.onCompleted();
				return;
			}
			// Setup Fresco and associate with session
			BgwEngine engine = new BgwEngine();
			sessions.put(sessionID, engine);
			
			l.info("[" + sessionID + "] new session started");

			// Reply to caller
			CmdResult reply = CmdResult.newBuilder().setMsg("[" + sessionID + "] init done.")
					.setStatus(CmdResult.Status.SUCCESS).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void nextCmd(SMCCmd req, StreamObserver<CmdResult> responseObserver) {
			// Extract current session from ID
			String sessionID = SessionInterceptor.SESSION_ID.get();
			l.finer("Current session: " + sessionID);
			// Fetch associated engine
			BgwEngine eng = (BgwEngine) sessions.get(sessionID);
			if (eng == null) {
				responseObserver.onNext(errorInvalidSession);
				responseObserver.onCompleted();
				l.warning("[" + sessionID + "] nextCmd: no session found!");
				return;
			}
			
			CmdResult resp;
			try {
				resp = eng.runNextPhase(req);
				
			} catch (Exception e) {
				// Exception means that we reached a non-fixable error condition.
				gracefulTearDown(sessionID);
				resp = CmdResult.newBuilder().setMsg(e.getMessage()).setStatus(CmdResult.Status.ABORTED).build();
			}

			responseObserver.onNext(resp);
			responseObserver.onCompleted();
		}

		public void tearDown(SessionCtx req, StreamObserver<CmdResult> responseObserver) {
			gracefulTearDown(req.getSessionID());
			CmdResult msg = CmdResult.newBuilder().setStatus(CmdResult.Status.SUCCESS_DONE).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
			return;
		}
		
		// Helpers
		
		// Tries shutting down any active SMC session and cleans up used resources.
		private void gracefulTearDown(String sessionID) {
			BgwEngine oldEngine = sessions.remove(sessionID);
			if (oldEngine != null) {
				oldEngine.stopAndInvalidate();
				l.fine("gracefulTearDown: successful");
			
			} else {
				l.fine("gracefulTearDown: session not associated with active engine: " + sessionID);
			}
		}
		
	}

}
