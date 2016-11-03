package de.tum.flexsmc.smc.engine;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang.ArrayUtils;

import de.tum.flexsmc.smc.rpc.CmdResult;
import de.tum.flexsmc.smc.rpc.PreparePhase;
import de.tum.flexsmc.smc.rpc.SMCCmd;
import de.tum.flexsmc.smc.rpc.SMCResult;
import de.tum.flexsmc.smc.rpc.SessionPhase;
import de.tum.flexsmc.smc.rpc.CmdResult.Status;
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
	
	protected final CmdResult errorInvalidTransition = CmdResult.newBuilder().setMsg("Invalid state transition")
			.setStatus(CmdResult.Status.DENIED).build();
	
	protected enum JobProgress {
		NOT_INITIALIZED(0),
		PREPARE_START(1),
		PREPARE_FINISH(2),
		SESSION_START(3),
		SESSION_FINSIH(4);
		
		private final int value;

		private JobProgress(int value) {
			this.value = value;
		}
	}
	private final JobProgress[][] allowedTransitions = {
		{},	//< to: NOT_INITIALIZED, only set on start
		{JobProgress.NOT_INITIALIZED, JobProgress.PREPARE_START, JobProgress.PREPARE_FINISH},	//< to: PREPARE_START
		{JobProgress.PREPARE_START},															//< to: PREPARE_FINISH
		{JobProgress.PREPARE_FINISH},															//< to: SESSION_START
		{JobProgress.SESSION_START},															//< to: SESSION_FINSIH
	};
	
	private JobProgress phase = JobProgress.NOT_INITIALIZED;
	
	protected synchronized void setPhase(JobProgress phase) {
		this.phase = phase;
	}
	
	protected synchronized void validateSetPhase(JobProgress newPhase) {
		l.info("Old state->" + this.phase + " new->" + newPhase);
		JobProgress oldPhase = this.phase;
		if (ArrayUtils.contains(allowedTransitions[newPhase.value], oldPhase)) {
			// Valid transition, so apply.
			this.phase = newPhase;
		
		} else {
			throw new IllegalArgumentException("Invalid state transition");
		}
	}
	
	public CmdResult runNextPhase(SMCCmd req) {
		// Prepare reply
		CmdResult.Builder reply = CmdResult.newBuilder().setStatus(Status.SUCCESS);
		l.info("Incoming phase: " + req.getPayloadCase().toString());

		PayloadCase phase = req.getPayloadCase();
		switch (phase) {
		case PREPARE: {
			validateSetPhase(JobProgress.PREPARE_START);
			
			PreparePhase p = req.getPrepare();
			l.info("Prepare phase:" + p.getParticipantsCount());

			try {
				prepare(req.getSmcPeerID(), p.getParticipantsList());
				reply.setMsg("prep done");
				setPhase(JobProgress.PREPARE_FINISH);
				
			} catch (Exception e) {
				// Only send error, but allow to recover.
				reply.setStatus(CmdResult.Status.DENIED).setMsg(e.getMessage());
				e.printStackTrace();
			}
		}
			break;
			
		case SESSION:
			validateSetPhase(JobProgress.SESSION_START);
			
			SessionPhase p = req.getSession();
			l.info("Session phase");

			SMCResult res = runSession();
			// Note: exceptions are thrown in case of irrevesible errors.
			reply.setMsg("sess done").setResult(res).setStatus(Status.SUCCESS_DONE);

			setPhase(JobProgress.SESSION_FINSIH);
			break;

		default:
			return errorInvalidTransition;
		}

		return reply.build();
	}
	
	public abstract void prepare(int myId, List<PreparePhase.Participant> participants) throws RuntimeException, IOException;
	
	public abstract SMCResult runSession();
	
	public abstract void stopAndInvalidate();
}
