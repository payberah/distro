package clive.peer.membership;

import java.util.UUID;

import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;

public class ShuffleResponse extends MSMessage {

	private static final long serialVersionUID = -5022051054665787770L;
	private final UUID requestId;
	private final DescriptorBuffer randomBuffer;
	private double[] hits;

//-------------------------------------------------------------------
	public ShuffleResponse(MSPeerAddress source, MSPeerAddress destination, UUID requestId, DescriptorBuffer randomBuffer, double[] hits) {
		super(source, destination);
		this.requestId = requestId;
		this.randomBuffer = randomBuffer;
		this.hits = hits;
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
	public double[] getHits() {
		return hits;
	}

//-------------------------------------------------------------------
	public int getSize() {
		return hits.length;
	}
}
