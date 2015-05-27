package clive.peer.membership;


import java.util.UUID;

import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;


public class ShuffleRequest extends MSMessage {

	private static final long serialVersionUID = 8493601671018888143L;
	private final UUID requestId;
	private final DescriptorBuffer randomBuffer;
	private int slots;

//-------------------------------------------------------------------
	public ShuffleRequest(MSPeerAddress source, MSPeerAddress destination, UUID requestId, DescriptorBuffer randomBuffer, int slots) {
		super(source, destination);
		this.requestId = requestId;
		this.randomBuffer = randomBuffer;
		this.slots = slots;
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
	public int getSlots() {
		return slots;
	}

//-------------------------------------------------------------------
	public int getSize() {
		return 1;
	}
}
