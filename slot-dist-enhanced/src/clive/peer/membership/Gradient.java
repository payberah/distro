package clive.peer.membership;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import clive.main.Configuration;
import clive.peer.common.MSPeerAddress;
import clive.peer.common.MSPeerDescriptor;
import clive.peer.common.RandomView;
import clive.peer.mspeers.MessagePort;
import clive.simulator.snapshot.Snapshot;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

public final class Gradient extends ComponentDefinition {
	Negative<GradientPeerSampling> gradientPort = negative(GradientPeerSampling.class);

	Positive<MessagePort> messagePort = positive(MessagePort.class);
	Positive<Timer> timerPort = positive(Timer.class);

	private MSPeerAddress self;

	private int shuffleLength;
	private long shufflePeriod;
	private long shuffleTimeout;

	private RandomView randomView;
	
	private int slots;
	
	private boolean joining;

	private HashMap<UUID, MSPeerAddress> outstandingShuffles;
	
	//private AvgData avgData;
	private double[] hits = new double[Configuration.SLOT_RANGE];
	private double[] hitRatio = new double[Configuration.SLOT_RANGE];

//-------------------------------------------------------------------	
	public Gradient() {
		outstandingShuffles = new HashMap<UUID, MSPeerAddress>();

		subscribe(handleInit, control);
		subscribe(handleJoin, gradientPort);
		subscribe(handleInitiateShuffle, timerPort);
		subscribe(handleRandomShuffleTimeout, timerPort);
		
		subscribe(handleRandomShuffleRequest, messagePort);
		subscribe(handleRandomShuffleResponse, messagePort);
	}

//-------------------------------------------------------------------	
	Handler<GradientInit> handleInit = new Handler<GradientInit>() {
		public void handle(GradientInit init) {
			shuffleLength = init.getConfiguration().getShuffleLength();
			shufflePeriod = init.getConfiguration().getShufflePeriod();
			shuffleTimeout = init.getConfiguration().getShuffleTimeout();
			randomView = init.getRandomView();
			slots = init.getSlots();
			
			for (int i = 0; i < Configuration.SLOT_RANGE; i++) {
				hits[i] = 0;
				hitRatio[i] = 0;
			}
		}
	};

//-------------------------------------------------------------------	
	Handler<Join> handleJoin = new Handler<Join>() {
		public void handle(Join event) {
			self = event.getSelf();

			if (!self.getPeerId().equals(Configuration.SOURCE_ID))
				hits[slots] = 1;
			
			LinkedList<MSPeerAddress> insiders = event.getCyclonInsiders();
			
			if (insiders.size() == 0) {
				// I am the first peer
				trigger(new JoinCompleted(self), gradientPort);

				// schedule shuffling
				SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
				spt.setTimeoutEvent(new InitiateShuffle(spt));
				trigger(spt, timerPort);
				return;
			}

			MSPeerAddress peer = insiders.poll();
			initiateShuffle(1, peer);
			joining = true;
		}
	};

//-------------------------------------------------------------------	
	private void initiateShuffle(int shuffleSize, MSPeerAddress randomPeer) {
		// send the random view to a random peer
		ArrayList<MSPeerDescriptor> randomDescriptors = randomView.selectToSendAtActive(shuffleSize - 1, randomPeer);
		randomDescriptors.add(new MSPeerDescriptor(self, slots));
		DescriptorBuffer randomBuffer = new DescriptorBuffer(self, randomDescriptors);
		
		ScheduleTimeout rst = new ScheduleTimeout(shuffleTimeout);
		rst.setTimeoutEvent(new ShuffleTimeout(rst, randomPeer));
		UUID rTimeoutId = rst.getTimeoutEvent().getTimeoutId();

		findLocalDistribution();
		outstandingShuffles.put(rTimeoutId, randomPeer);
		ShuffleRequest rRequest = new ShuffleRequest(self, randomPeer, rTimeoutId, randomBuffer);

		trigger(rst, timerPort);
		trigger(rRequest, messagePort);
	}

//-------------------------------------------------------------------	
	Handler<InitiateShuffle> handleInitiateShuffle = new Handler<InitiateShuffle>() {
		public void handle(InitiateShuffle event) {
			randomView.incrementDescriptorAges();
			
			MSPeerAddress randomPeer = randomView.selectPeerToShuffleWith();
			
			if (randomPeer != null)
				initiateShuffle(shuffleLength, randomPeer);			
		}
	};

//-------------------------------------------------------------------	
	Handler<ShuffleRequest> handleRandomShuffleRequest = new Handler<ShuffleRequest>() {
		public void handle(ShuffleRequest event) {
			MSPeerAddress peer = event.getMSPeerSource();
			DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
			DescriptorBuffer toSendRandomBuffer = new DescriptorBuffer(self, randomView.selectToSendAtPassive(receivedRandomBuffer.getSize(), peer));
			randomView.selectToKeep(peer, receivedRandomBuffer.getDescriptors());

			if (peer == null || self == null)
				return;


			ShuffleResponse response = new ShuffleResponse(self, peer, event.getRequestId(), toSendRandomBuffer);
			trigger(response, messagePort);

			findHitsDistribution(receivedRandomBuffer.getDescriptors());
			findLocalDistribution();
		}
	};

//-------------------------------------------------------------------	
	Handler<ShuffleResponse> handleRandomShuffleResponse = new Handler<ShuffleResponse>() {
		public void handle(ShuffleResponse event) {
			if (joining) {
				joining = false;
				trigger(new JoinCompleted(self), gradientPort);

				// schedule shuffling
				SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
				spt.setTimeoutEvent(new InitiateShuffle(spt));
				trigger(spt, timerPort);
			}

			// cancel shuffle timeout
			UUID shuffleId = event.getRequestId();
			if (outstandingShuffles.containsKey(shuffleId)) {
				outstandingShuffles.remove(shuffleId);
				CancelTimeout ct = new CancelTimeout(shuffleId);
				trigger(ct, timerPort);
			}

			MSPeerAddress peer = event.getMSPeerSource();
			DescriptorBuffer receivedRandomBuffer = event.getRandomBuffer();
			randomView.selectToKeep(peer, receivedRandomBuffer.getDescriptors());			

			findHitsDistribution(receivedRandomBuffer.getDescriptors());
		}
	};

//-------------------------------------------------------------------	
	Handler<ShuffleTimeout> handleRandomShuffleTimeout = new Handler<ShuffleTimeout>() {
		public void handle(ShuffleTimeout event) {
			//logger.warn("SHUFFLE TIMED OUT");
		}
	};
	
//-------------------------------------------------------------------	
	Handler<HitsTimeout> handleHitsTimeout = new Handler<HitsTimeout>() {
		public void handle(HitsTimeout event) {
			findLocalDistribution();
			
			for (int i = 0; i < Configuration.SLOT_RANGE; i++)
				hits[i] = 0;

			hits[slots] = 1;
		}
	};
	
//-------------------------------------------------------------------	
    private void findLocalDistribution() {
		double count = 0;
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			count += hits[i];
		
		
		if (count > 0) {
			for (int i = 0; i < Configuration.SLOT_RANGE; i++)
				hitRatio[i] = hits[i] / count;
		}
		
		Snapshot.updateNum(self, hitRatio);
    }
    
//-------------------------------------------------------------------	
    private void findHitsDistribution(ArrayList<MSPeerDescriptor> nodes) {
    	for (MSPeerDescriptor desc : nodes)
    		hits[desc.getMSPeerAddress().getSlot()]++;
    }
}
