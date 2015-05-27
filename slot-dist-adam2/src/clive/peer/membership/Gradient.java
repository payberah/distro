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
	private boolean lock = false;
	private double[] hitRatio = new double[Configuration.SLOT_RANGE];
	private double v;

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
				hitRatio[i] = 0;
			}		
			
			hitRatio[slots] = 1;
		}
	};

//-------------------------------------------------------------------	
	Handler<Join> handleJoin = new Handler<Join>() {
		public void handle(Join event) {
			self = event.getSelf();
			
			
			LinkedList<MSPeerAddress> insiders = event.getCyclonInsiders();
			
			if (insiders.size() == 0) {
				v = 1;
				trigger(new JoinCompleted(self), gradientPort);

				// schedule shuffling
				SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(shufflePeriod, shufflePeriod);
				spt.setTimeoutEvent(new InitiateShuffle(spt));
				trigger(spt, timerPort);
				return;
			} else
				v = 0;

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

		outstandingShuffles.put(rTimeoutId, randomPeer);

		lock = true;
		double[] nodeRatio = new double[Configuration.SLOT_RANGE];
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			nodeRatio[i] = hitRatio[i];
				
		ShuffleRequest rRequest = new ShuffleRequest(self, randomPeer, rTimeoutId, randomBuffer, nodeRatio, v);

		trigger(rst, timerPort);
		trigger(rRequest, messagePort);
		
		Snapshot.updateNum(self, hitRatio, v);
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

			double[] peerHitRatio = event.getHitRatio();

			if (!lock) {
				double[] nodeRatio = new double[Configuration.SLOT_RANGE];
				for (int i = 0; i < Configuration.SLOT_RANGE; i++)
					nodeRatio[i] = hitRatio[i];
				ShuffleResponse response = new ShuffleResponse(self, peer, event.getRequestId(), toSendRandomBuffer, nodeRatio, LockState.Unlock, v);
				trigger(response, messagePort);
				findHitsDistribution(peerHitRatio);
				v = (v + event.getV()) / 2;
			} else {
				ShuffleResponse response = new ShuffleResponse(self, peer, event.getRequestId(), toSendRandomBuffer, peerHitRatio, LockState.Lock, v);
				trigger(response, messagePort);
			}

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
			
			LockState peerLockState = event.getLockState();
			
			if (peerLockState == LockState.Unlock) {
				double[] peerHitRatio = event.getHitRatio();
				findHitsDistribution(peerHitRatio);
				v = (v + event.getV()) / 2;
				//for (int i = 0; i < Configuration.SLOT_RANGE; i++)
				//	hitRatio[i] = peerHitRatio[i];
			}
			
			lock = false;
		}
	};

//-------------------------------------------------------------------	
	Handler<ShuffleTimeout> handleRandomShuffleTimeout = new Handler<ShuffleTimeout>() {
		public void handle(ShuffleTimeout event) {
			//logger.warn("SHUFFLE TIMED OUT");
		}
	};
	
//-------------------------------------------------------------------	
    private void findHitsDistribution(double[] peerHitRatio) {
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			hitRatio[i] = (hitRatio[i] + peerHitRatio[i]) / 2;
    }
}
