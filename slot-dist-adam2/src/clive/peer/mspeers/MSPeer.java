package clive.peer.mspeers;

import java.util.LinkedList;
import java.util.Set;

import clive.main.Configuration;
import clive.peer.common.Buffer;
import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;
import clive.peer.common.RandomView;
import clive.peer.membership.Gradient;
import clive.peer.membership.GradientConfiguration;
import clive.peer.membership.GradientInit;
import clive.peer.membership.GradientPeerSampling;
import clive.peer.membership.Join;
import clive.peer.membership.JoinCompleted;
import clive.simulator.snapshot.Snapshot;

import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.p2p.bootstrap.BootstrapCompleted;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;
import se.sics.kompics.p2p.bootstrap.BootstrapRequest;
import se.sics.kompics.p2p.bootstrap.BootstrapResponse;
import se.sics.kompics.p2p.bootstrap.P2pBootstrap;
import se.sics.kompics.p2p.bootstrap.PeerEntry;
import se.sics.kompics.p2p.bootstrap.client.BootstrapClient;
import se.sics.kompics.p2p.bootstrap.client.BootstrapClientInit;
import se.sics.kompics.p2p.fd.ping.PingFailureDetector;
import se.sics.kompics.p2p.fd.ping.PingFailureDetectorInit;
import se.sics.kompics.timer.Timer;

public final class MSPeer extends ComponentDefinition {
	
	Negative<MSPeerPort> msPeerPort = negative(MSPeerPort.class);

	Positive<MessagePort> messagePort = positive(MessagePort.class);
	Positive<Network> network = positive(Network.class);
	Positive<Timer> timer = positive(Timer.class);

	private Component membership;
	private Component fd, bootstrap;
	
	private Address self;
	private MSPeerAddress msPeerSelf;

	private int uploadSlots;
	private Buffer buffer;

	private RandomView randomView;
	
	private int bw;
	
	private int bootstrapRequestPeerCount;
	private boolean bootstrapped;

	private BootstrapConfiguration bootstrapConfiguration;
	
//-------------------------------------------------------------------	
	public MSPeer() {
		membership = create(Gradient.class);
		
		fd = create(PingFailureDetector.class);
		bootstrap = create(BootstrapClient.class);
	
		connect(network, fd.getNegative(Network.class));
		connect(network, bootstrap.getNegative(Network.class));
		connect(timer, membership.getNegative(Timer.class));
		connect(timer, fd.getNegative(Timer.class));
		connect(timer, bootstrap.getNegative(Timer.class));
		
		subscribe(handleInit, control);
		subscribe(handleJoin, msPeerPort);
		subscribe(handleJoinCompleted, membership.getPositive(GradientPeerSampling.class));
		subscribe(handleBootstrapResponse, bootstrap.getPositive(P2pBootstrap.class));
		subscribe(handleRecvMSMessage, messagePort);
		subscribe(handleSendMSMessage, membership.getNegative(MessagePort.class));		
	}

//-------------------------------------------------------------------	
	Handler<MSPeerInit> handleInit = new Handler<MSPeerInit>() {
		public void handle(MSPeerInit init) {
			msPeerSelf = init.getMSPeerSelf();
			self = msPeerSelf.getPeerAddress();
			uploadSlots = init.getUploadSlots();
			bw = init.getBW();
			
			MSConfiguration msConfiguration = init.getMSConfiguration();
			GradientConfiguration gradientConfiguration = init.getGradientConfiguration();
			bootstrapConfiguration = init.getBootstrapConfiguration();

			buffer = new Buffer(msConfiguration.getBufferSize(), Configuration.WINDOW_SIZE);

			bootstrapRequestPeerCount = gradientConfiguration.getBootstrapRequestPeerCount();

			randomView = new RandomView(gradientConfiguration.getRandomViewSize(), msPeerSelf);
			
			trigger(new GradientInit(gradientConfiguration, randomView, uploadSlots), membership.getControl());
			trigger(new BootstrapClientInit(self, bootstrapConfiguration), bootstrap.getControl());
			trigger(new PingFailureDetectorInit(self, init.getFdConfiguration()), fd.getControl());
		}
	};

//-------------------------------------------------------------------	
	Handler<JoinMSPeer> handleJoin = new Handler<JoinMSPeer>() {
		public void handle(JoinMSPeer event) {
			BootstrapRequest request = new BootstrapRequest("GradienTV", bootstrapRequestPeerCount);
			trigger(request, bootstrap.getPositive(P2pBootstrap.class));
			
			Snapshot.addPeer(msPeerSelf, bw, buffer, System.currentTimeMillis());
		}
	};

//-------------------------------------------------------------------	
	Handler<BootstrapResponse> handleBootstrapResponse = new Handler<BootstrapResponse>() {
		public void handle(BootstrapResponse event) {
			if (!bootstrapped) {
				Set<PeerEntry> somePeers = event.getPeers();
				LinkedList<MSPeerAddress> cyclonInsiders = new LinkedList<MSPeerAddress>();
				
				for (PeerEntry peerEntry : somePeers)
					cyclonInsiders.add((MSPeerAddress) peerEntry.getOverlayAddress());
				
				trigger(new Join(msPeerSelf, cyclonInsiders), membership.getPositive(GradientPeerSampling.class));
				bootstrapped = true;
			}
		}
	};

//-------------------------------------------------------------------	
	Handler<JoinCompleted> handleJoinCompleted = new Handler<JoinCompleted>() {
		public void handle(JoinCompleted event) {

			// bootstrap completed
			trigger(new BootstrapCompleted("GradienTV", msPeerSelf), bootstrap.getPositive(P2pBootstrap.class));
		}
	};

//-------------------------------------------------------------------	
	Handler<MSMessage> handleSendMSMessage = new Handler<MSMessage>() {
		public void handle(MSMessage event) {
			trigger(event, messagePort);
		}
	};

//-------------------------------------------------------------------	
	Handler<MSMessage> handleRecvMSMessage = new Handler<MSMessage>() {
		public void handle(MSMessage event) {
			trigger(event, membership.getNegative(MessagePort.class));
			Snapshot.updateLoad(event.getSize());
		}
	};
}
