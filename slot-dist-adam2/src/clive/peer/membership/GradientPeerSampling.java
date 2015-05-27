package clive.peer.membership;

import clive.simulator.core.NeedHelper;
import se.sics.kompics.PortType;

public final class GradientPeerSampling extends PortType {{
	negative(Join.class);
	
	positive(JoinCompleted.class);
	positive(NeedHelper.class);
}}
