package clive.peer.membership;

import java.util.UUID;

import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;

public class ShuffleResponse extends MSMessage {

	private static final long serialVersionUID = -5022051054665787770L;
	private final UUID requestId;
	private final DescriptorBuffer randomBuffer;
	private double[] hitRatio;
	private LockState lockstate;
	private double v;

//-------------------------------------------------------------------
	public ShuffleResponse(MSPeerAddress source, MSPeerAddress destination, UUID requestId, DescriptorBuffer randomBuffer, double[] hitRatio, LockState lockstate, double v) {
		super(source, destination);
		this.requestId = requestId;
		this.randomBuffer = randomBuffer;
		this.hitRatio = hitRatio;
		this.lockstate = lockstate;
		this.v = v;		
	}

//-------------------------------------------------------------------
	public UUID getRequestId() {
		return requestId;
	}

//-------------------------------------------------------------------
	public DescriptorBuffer getRandomBuffer() {
		return randomBuffer;
	}

//-------------------------------------------------------------------
	public double[] getHitRatio() {
		return hitRatio;
	}

//-------------------------------------------------------------------
	public double getV() {
		return v;
	}

//-------------------------------------------------------------------
	public LockState getLockState() {
		return lockstate;
	}

//-------------------------------------------------------------------
	public int getSize() {
		return 0;
		//return hitRatio.length;
	}
}
