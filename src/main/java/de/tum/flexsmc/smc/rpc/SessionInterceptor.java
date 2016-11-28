package de.tum.flexsmc.smc.rpc;

import java.util.HashSet;
import java.util.logging.Logger;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class SessionInterceptor implements ServerInterceptor {
	private static final Logger logger = Logger.getLogger(SessionInterceptor.class.getName());

	private static final Metadata.Key<String> SESSION_KEY = Metadata.Key.of("session-id",
			Metadata.ASCII_STRING_MARSHALLER);
	public static final Context.Key<String> SESSION_ID = Context.key("session-id");

	public HashSet<String> ignoreMethods;

	public SessionInterceptor() {
		// Ignore methods which do not require a valid session ID yet.
		ignoreMethods = new HashSet<String>(1);
		ignoreMethods.add("smc.SMC/ResetAll");
		ignoreMethods.add("smc.SMC/Init");
		ignoreMethods.add("smc.SMC/TearDown");
	}

	@Override
	public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
			ServerCallHandler<ReqT, RespT> next) {
		String methodName = call.getMethodDescriptor().getFullMethodName();
		System.out.println(">>>>>>>>>>>>> "+ methodName);
		if (ignoreMethods.contains(methodName)) {
			return next.startCall(call, headers);
		}
		// Require a valid session ID from now on
		logger.fine("Session: " + headers.get(SESSION_KEY));

		String sessID = headers.get(SESSION_KEY);
		if (sessID == null) {
			call.close(Status.UNAUTHENTICATED.withDescription("no session-id"), new Metadata());
			return new Listener<ReqT>() {
			};
		}
		Context context = Context.current().withValue(SESSION_ID, sessID);
		return Contexts.interceptCall(context, call, headers, next);
	}

}
