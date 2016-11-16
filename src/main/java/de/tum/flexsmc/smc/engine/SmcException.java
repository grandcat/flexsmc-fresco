package de.tum.flexsmc.smc.engine;

import de.tum.flexsmc.smc.rpc.CmdResult;

public class SmcException extends RuntimeException {
	private static final long serialVersionUID = 9091710698565635450L;
	
	private CmdResult.Status cmdStatus;
	
	protected SmcException(String message, CmdResult.Status status) {
		super(message);
		this.cmdStatus = status;
	}
	
	public CmdResult.Status getStatus() {
		return cmdStatus;
	}
	
	public CmdResult.Builder generateErrorMessage() {
		return CmdResult.newBuilder().setMsg(getMessage()).setStatus(cmdStatus);
	}
	
	@Override
	public String toString() {
        String s = getClass().getName() + ": ";
        String message = getLocalizedMessage();
        s = (message != null) ? (s + message) : s;
        return s + " [Status:" + cmdStatus.toString() + "]";
    }

}
