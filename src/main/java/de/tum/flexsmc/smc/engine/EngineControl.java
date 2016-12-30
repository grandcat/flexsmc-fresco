package de.tum.flexsmc.smc.engine;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.ArrayUtils;

import de.tum.flexsmc.smc.rpc.CmdResult;
import de.tum.flexsmc.smc.rpc.PreparePhase;
import de.tum.flexsmc.smc.rpc.SMCCmd;
import de.tum.flexsmc.smc.rpc.SMCResult;
import de.tum.flexsmc.smc.rpc.SMCTask;
import de.tum.flexsmc.smc.rpc.SessionPhase;
import de.tum.flexsmc.smc.rpc.CmdResult.Status;
import de.tum.flexsmc.smc.rpc.DebugPhase;
import de.tum.flexsmc.smc.rpc.SMCCmd.PayloadCase;

/**
 * Base engine controls the main flow based on incoming commands from the RPC
 * server. It takes care of valid transitions and handles error conditions.
 * 
 * @author stefan
 *
 */
public abstract class EngineControl {
	private static final Logger l = Logger.getLogger(EngineControl.class.getName());
	
	protected static final CmdResult errorInvalidTransition = CmdResult.newBuilder().setMsg("Invalid state transition")
			.setStatus(CmdResult.Status.DENIED).build();
	protected static final CmdResult errorInvalidTask = CmdResult.newBuilder().setMsg("Invalid task")
			.setStatus(CmdResult.Status.DENIED).build();
	
	protected SMCTask task;
	
	protected enum JobPhase {
		NOT_INITIALIZED(0),
		PREPARE_START(1),
		PREPARE_FINISH(2),
		SESSION_START(3),
		SESSION_FINSIH(4);
		
		private final int value;

		private JobPhase(int value) {
			this.value = value;
		}
	}
	
	/**
	 * Given the new phase is the first index of this table, the inner array
	 * contains the possible original phases it could have transitioned from.
	 * Otherwise, the transition is illegal.
	 */
	private final JobPhase[][] allowedTransitions = {
		{},	//< to: NOT_INITIALIZED, only set on start
		{JobPhase.NOT_INITIALIZED, JobPhase.PREPARE_START, JobPhase.PREPARE_FINISH},	//< to: PREPARE_START
		{JobPhase.PREPARE_START},															//< to: PREPARE_FINISH
		{JobPhase.PREPARE_FINISH},															//< to: SESSION_START
		{JobPhase.SESSION_START},															//< to: SESSION_FINSIH
	};
	
	private JobPhase phase = JobPhase.NOT_INITIALIZED;
	
	protected synchronized void setPhase(JobPhase phase) {
		this.phase = phase;
	}
	
	protected synchronized void validateSetPhase(JobPhase newPhase) {
		l.finer("Old state->" + this.phase + " new->" + newPhase);
		JobPhase oldPhase = this.phase;
		if (ArrayUtils.contains(allowedTransitions[newPhase.value], oldPhase)) {
			// Valid transition, so apply.
			this.phase = newPhase;
		
		} else {
			throw new IllegalArgumentException("Invalid state transition");
		}
	}
	
	protected synchronized SMCTask getTask() {
		return this.task;
	}
	
	public CmdResult runNextPhase(SMCCmd req) throws Exception {
		// Prepare reply
		CmdResult.Builder reply = CmdResult.newBuilder().setStatus(Status.SUCCESS);
		l.finer("Incoming phase: " + req.getPayloadCase().toString());

		PayloadCase phase = req.getPayloadCase();
		switch (phase) {
		case PREPARE: {
			validateSetPhase(JobPhase.PREPARE_START);
			
			PreparePhase p = req.getPrepare();
			l.fine("Prepare phase:" + p.getParticipantsCount());	

			try {
				// SMCTask must be set
				SMCTask task = p.getSmcTask();
				if (task == null) {
					return errorInvalidTask;
				}
				this.task = task;
				
				// Start SMC preparation
				prepare(req.getSmcPeerID(), p.getParticipantsList());
				reply.setMsg("prep done");
				setPhase(JobPhase.PREPARE_FINISH);
				
			} catch (SmcException e) {
				// Only send error, but allow to recover. GW should reinit this phase.
				reply = e.generateErrorMessage();
				e.printStackTrace();
				
			} catch (Exception e) {
				// Only send error, but allow to recover. GW should reinit this phase.
				reply.setStatus(CmdResult.Status.DENIED).setMsg(e.getMessage());
				e.printStackTrace();
			}
		}
			break;
			
		case SESSION:
			validateSetPhase(JobPhase.SESSION_START);
			
			SessionPhase p = req.getSession();
			l.fine("Session phase");

			SMCResult res = runSession();
			// Note: exceptions are thrown in case of irrevesible errors. If not handled here,
			// RPCServer generates and send a error message.
			reply.setMsg("sess done").setResult(res).setStatus(Status.SUCCESS_DONE);
			// Note: do not expect any further communication for current job session.

			setPhase(JobPhase.SESSION_FINSIH);
			break;
			
		case DEBUG: {
			DebugPhase dp = req.getDebug();

			SMCResult pong = SMCResult.newBuilder().setRes((float) (dp.getPing() + 1)).build();
			// No validation. Debug phase is always valid.
			reply.setMsg("pong: debug received").setResult(pong).setStatus(Status.SUCCESS);
			break;
		}

		default:
			return errorInvalidTransition;
		}

		return reply.build();
	}
	
	public abstract void prepare(int myId, List<PreparePhase.Participant> participants) throws RuntimeException, IOException;
	
	public abstract SMCResult runSession();
	
	public abstract void stopAndInvalidate();
}
