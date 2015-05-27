package clive.peer.membership;


import java.util.UUID;

import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;


public class ShuffleRequest extends MSMessage {

	private static final long serialVersionUID = 8493601671018888143L;
	private final UUID requestId;
	private final DescriptorBuffer randomBuffer;
	private double[] hitRatio;
	private double v;

//-------------------------------------------------------------------
	public ShuffleRequest(MSPeerAddress source, MSPeerAddress destination, UUID requestId, DescriptorBuffer randomBuffer, double[] hitRatio, double v) {
		super(source, destination);
		this.requestId = requestId;
		this.randomBuffer = randomBuffer;
		this.hitRatio = hitRatio;
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
	public int getSize() {
		return 0;
//		return hitRatio.length;
	}
}
