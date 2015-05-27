package clive.simulator.core;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Vector;

import clive.main.Configuration;
import clive.peer.common.MSMessage;
import clive.peer.common.MSPeerAddress;
import clive.peer.membership.GradientConfiguration;
import clive.peer.mspeers.JoinMSPeer;
import clive.peer.mspeers.MSConfiguration;
import clive.peer.mspeers.MSPeer;
import clive.peer.mspeers.MSPeerInit;
import clive.peer.mspeers.MSPeerPort;
import clive.peer.mspeers.MessagePort;
import clive.simulator.bwmodel.BwDelayedMessage;
import clive.simulator.bwmodel.Link;
import clive.simulator.snapshot.Snapshot;

import se.sics.kompics.ChannelFilter;
import se.sics.kompics.Component;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import se.sics.kompics.network.Network;
import se.sics.kompics.p2p.bootstrap.BootstrapConfiguration;
import se.sics.kompics.p2p.fd.ping.PingFailureDetectorConfiguration;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

public final class MSSimulator extends ComponentDefinition {
	Positive<MSSimulatorPort> simulator = positive(MSSimulatorPort.class);
	Positive<Network> network = positive(Network.class);
	Positive<Timer> timer = positive(Timer.class);

	private final HashMap<BigInteger, Component> peers;
	private final HashMap<BigInteger, Link> uploadLink;
	private final HashMap<BigInteger, Link> downloadLink;
	private final HashMap<BigInteger, MSPeerAddress> peersAddress;
	private final HashMap<BigInteger, MSPeerAddress> helpers;
	
	// peer initialization state
	private Address peer0Address;
	private BootstrapConfiguration bootstrapConfiguration;
	private GradientConfiguration gradientConfiguration;
	private MSConfiguration msConfiguration;	
	private PingFailureDetectorConfiguration fdConfiguration;

	private int peerIdSequence;
	MSPeerAddress sourcePeerAddress;
	MSPeerAddress helperPeerAddress;

	private BigInteger idSpaceSize;
	private ConsistentHashtable<BigInteger> view;
	private Vector<MSPeerAddress> freeriders;
	
	private int totalJoin = 0;

//-------------------------------------------------------------------	
	public MSSimulator() {
		peers = new HashMap<BigInteger, Component>();
		uploadLink = new HashMap<BigInteger, Link>();
		downloadLink = new HashMap<BigInteger, Link>();
		peersAddress = new HashMap<BigInteger, MSPeerAddress>();
		view = new ConsistentHashtable<BigInteger>();
		freeriders = new Vector<MSPeerAddress>();
		helpers = new HashMap<BigInteger, MSPeerAddress>();

		subscribe(handleInit, control);

		subscribe(handleGenerateReport, timer);
		subscribe(handleDelayedMessage, timer);
		
		subscribe(handleMessageReceived, network);
		
		subscribe(handleMSPeerJoin, simulator);
		subscribe(handleMSPeerFail, simulator);
	}

//-------------------------------------------------------------------	
	Handler<MSSimulatorInit> handleInit = new Handler<MSSimulatorInit>() {
		public void handle(MSSimulatorInit init) {
			peers.clear();
			peerIdSequence = 0;

			peer0Address = init.getPeer0Address();
			bootstrapConfiguration = init.getBootstrapConfiguration();
			gradientConfiguration = init.getGradientConfiguration();
			fdConfiguration = init.getFdConfiguration();
			msConfiguration = init.getMSConfiguration();

			idSpaceSize = gradientConfiguration.getIdentifierSpaceSize();
			
			// generate periodic report
			int snapshotPeriod = msConfiguration.getSnapshotPeriod();
			Snapshot.init();
			SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(snapshotPeriod, snapshotPeriod);
			spt.setTimeoutEvent(new GenerateReport(spt));
			trigger(spt, timer);
		}
	};

//-------------------------------------------------------------------	
	Handler<MSPeerJoin> handleMSPeerJoin = new Handler<MSPeerJoin>() {
		public void handle(MSPeerJoin event) {
			BigInteger id = event.getMSPeerId();
			int bw = event.getBw();
			int seats = event.getSeats();
			int agents = event.getAgents();
			totalJoin++;
			
			// join with the next id if this id is taken
			BigInteger successor = view.getNode(id);
			while (successor != null && successor.equals(id)) {
				id = id.add(BigInteger.ONE).mod(idSpaceSize);
				successor = view.getNode(id);
			}

			Component newPeer = createAndStartNewPeer(id, bw, seats, agents);
			view.addNode(id);
			trigger(new JoinMSPeer(id), newPeer.getPositive(MSPeerPort.class));
		}
	};

//-------------------------------------------------------------------	
	Handler<MSPeerFail> handleMSPeerFail = new Handler<MSPeerFail>() {
		public void handle(MSPeerFail event) {
			BigInteger id = view.getNode(event.getCyclonId());

			if (view.size() == 0) {
				System.err.println("Empty network");
				return;
			}
			
			if (id.equals(Configuration.SOURCE_ID)) {
				System.err.println("Can not remove source ...");
				return;
			}

			if (helpers.containsKey(id)) {
				System.err.println("Can not remove helper ...");
				return;
			}

			view.removeNode(id);
			stopAndDestroyPeer(id);
		}
	};
	
//-------------------------------------------------------------------	
	Handler<MSMessage> handleMessageSent = new Handler<MSMessage>() {
		public void handle(MSMessage message) {
			// message just sent by some peer goes into peer's up pipe
			Link link = uploadLink.get(message.getMSPeerSource().getPeerId());
			if (link == null)
				return; //TODO
			
			long delay = link.addMessage(message);
			
			if (delay == 0) {
				// immediately send to cloud
				trigger(message, network);
				return;
			}
			
			ScheduleTimeout st = new ScheduleTimeout(delay);
			st.setTimeoutEvent(new BwDelayedMessage(st, message, true));
			trigger(st, timer);
		}
	};

//-------------------------------------------------------------------	
	Handler<MSMessage> handleMessageReceived = new Handler<MSMessage>() {
		public void handle(MSMessage message) {
			// message to be received by some peer goes into peer's down pipe
			Link link = downloadLink.get(message.getMSPeerDestination().getPeerId());
			
			if (link == null)
				return;
			
			long delay = link.addMessage(message);
			
			if (delay == 0) {
				// immediately deliver to peer
				Component peer = peers.get(message.getMSPeerDestination().getPeerId());
				trigger(message, peer.getNegative(MessagePort.class));
				return;
			}
			
			ScheduleTimeout st = new ScheduleTimeout(delay);
			st.setTimeoutEvent(new BwDelayedMessage(st, message, false));
			trigger(st, timer);
		}
	};

//-------------------------------------------------------------------	
	Handler<BwDelayedMessage> handleDelayedMessage = new Handler<BwDelayedMessage>() {
		public void handle(BwDelayedMessage delayedMessage) {
			if (delayedMessage.isBeingSent()) {
				// message comes out of upload pipe
				MSMessage message = delayedMessage.getMessage();
				// and goes to the network cloud
				trigger(message, network);
			} else {
				// message comes out of download pipe
				MSMessage message = delayedMessage.getMessage();
				Component peer = peers.get(message.getMSPeerDestination().getPeerId());
				if (peer != null) {
					// and goes to the peer
					trigger(message, peer.getNegative(MessagePort.class));
				}
			}
		}
	};

//-------------------------------------------------------------------	
	private final Component createAndStartNewPeer(BigInteger id, int bw, int seats, int agents) {
		Component peer = create(MSPeer.class);
		int peerId = ++peerIdSequence;
		Address peerAddress = new Address(peer0Address.getIp(), peer0Address.getPort(), peerId);

		MSPeerAddress msPeerAddress = new MSPeerAddress(peerAddress, id);
		
		connect(network, peer.getNegative(Network.class), new MessageDestinationFilter(peerAddress));
		connect(timer, peer.getNegative(Timer.class));

		subscribe(handleMessageSent, peer.getNegative(MessagePort.class));

		trigger(new MSPeerInit(msPeerAddress, seats, agents, bw, freeriders, msConfiguration, bootstrapConfiguration, gradientConfiguration, fdConfiguration, helperPeerAddress, sourcePeerAddress), peer.getControl());

		trigger(new Start(), peer.getControl());
		peers.put(id, peer);
		uploadLink.put(id, new Link(100000));
		downloadLink.put(id, new Link(100000)); //XXX
		peersAddress.put(id, msPeerAddress);

		return peer;
	}

//-------------------------------------------------------------------	
	private final void stopAndDestroyPeer(BigInteger id) {
		Component peer = peers.get(id);

		trigger(new Stop(), peer.getControl());

		subscribe(handleMessageSent, peer.getNegative(Network.class));

		disconnect(network, peer.getNegative(Network.class));
		disconnect(timer, peer.getNegative(Timer.class));

		Snapshot.removePeer(peersAddress.get(id));

		peers.remove(id);
		uploadLink.remove(id);
		downloadLink.remove(id);
		peersAddress.remove(id);

		destroy(peer);
	}

//-------------------------------------------------------------------	
	Handler<GenerateReport> handleGenerateReport = new Handler<GenerateReport>() {
		public void handle(GenerateReport event) {
			Snapshot.report();
		}
	};

//-------------------------------------------------------------------	
	private final static class MessageDestinationFilter extends
			ChannelFilter<Message, Address> {
		public MessageDestinationFilter(Address address) {
			super(Message.class, address, true);
		}

		public Address getValue(Message event) {
			return event.getDestination();
		}
	}
}

