package de.tum.flexsmc.smc.rpc;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.logging.Logger;

import de.tum.flexsmc.smc.engine.BgwEngine;
import de.tum.flexsmc.smc.rpc.CmdResult.Status;
import de.tum.flexsmc.smc.rpc.SMCCmd.PayloadCase;
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
		logger.info("RPC server started, listening on socket " + listenerSocket.toString());
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
		private final CmdResult errorInvalidTransition = CmdResult.newBuilder().setMsg("Invalid state transition")
				.setStatus(CmdResult.Status.DENIED).build();

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
			// Setup Fresco and associate with session
			BgwEngine engine = new BgwEngine();
			sessions.put(sessionID, engine);

			// Reply to callee
			CmdResult reply = CmdResult.newBuilder().setMsg("[" + sessionID + "] init done.")
					.setStatus(CmdResult.Status.SUCCESS).build();
			responseObserver.onNext(reply);
			responseObserver.onCompleted();
		}

		@Override
		public void nextCmd(SMCCmd req, StreamObserver<CmdResult> responseObserver) {
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

			// Prepare reply
			CmdResult.Builder reply = CmdResult.newBuilder().setStatus(Status.SUCCESS);

			PayloadCase phase = req.getPayloadCase();
			switch (phase) {
			case PREPARE: {
				PreparePhase p = req.getPrepare();
				logger.info("Prepare phase:" + p.getParticipantsCount());

				try {
					eng.initializeConfig(req.getSmcPeerID(), p.getParticipantsList());
					eng.prepareSCE();

					reply.setMsg("prep done");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					sendException(responseObserver, e);
					return;
				}
			}
				break;
			case SESSION:
				SessionPhase p = req.getSession();
				logger.info("Session phase");

				try {
					SMCResult res = eng.runPhase();
					reply.setMsg("sess done").setResult(res).setStatus(Status.SUCCESS_DONE);
				} catch (Exception e) {
					e.printStackTrace();
					reply.setMsg("session failed. tear down all connections").setStatus(Status.ABORTED);
				}
				
				break;

			default:
				responseObserver.onNext(errorInvalidTransition);
				responseObserver.onCompleted();
				return;
			}

			responseObserver.onNext(reply.build());
			responseObserver.onCompleted();
		}

		public void tearDown(SessionCtx req, StreamObserver<CmdResult> responseObserver) {
			String sessionID = req.getSessionID();
			if (sessions.containsKey(sessionID)) {
				// Teardown SMC session if still running
//				BgwEngine eng = (BgwEngine) sessions.get(sessionID);
				
				sessions.remove(req.getSessionID());
				logger.info("Removed session " + sessionID);
			} else {
				logger.info("teardown: session not found: " + sessionID);
			}
			CmdResult msg = CmdResult.newBuilder().setStatus(CmdResult.Status.SUCCESS_DONE).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
			return;
		}

		private void sendException(StreamObserver<CmdResult> responseObserver, Exception e) {
			CmdResult msg = CmdResult.newBuilder().setMsg(e.getMessage()).setStatus(CmdResult.Status.DENIED).build();
			responseObserver.onNext(msg);
			responseObserver.onCompleted();
		}
		
//		private void sendFatalException(StreamObserver<CmdResult> responseObserver, Exception e) {
//			CmdResult msg = CmdResult.newBuilder().setMsg(e.getMessage()).setStatus(CmdResult.Status.ABORTED).build();
//			responseObserver.onNext(msg);
//			responseObserver.onCompleted();
//		}
	}

}
