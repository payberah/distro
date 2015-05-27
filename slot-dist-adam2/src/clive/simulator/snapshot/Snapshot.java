package clive.simulator.snapshot;

import java.text.DecimalFormat;
import java.util.HashMap;

import clive.main.Configuration;
import clive.peer.common.Buffer;
import clive.peer.common.MSPeerAddress;

public class Snapshot {
	private static DecimalFormat df = new DecimalFormat("#.###");
	private static HashMap<MSPeerAddress, PeerInfo> peers = new HashMap<MSPeerAddress, PeerInfo>();

	private static int counter = 0;
	private static int totalLoad = 0;
	private static String FILENAME = "alberto-uniform-churn-0.1.out";
	
//-------------------------------------------------------------------
	public static void init() {
		FileIO.write("", FILENAME);
	}
	
//-------------------------------------------------------------------
	public static void addPeer(MSPeerAddress address, int bw, Buffer buffer, long birthTime) {
		peers.put(address, new PeerInfo(buffer, bw, birthTime));
	}

//-------------------------------------------------------------------
	public static void removePeer(MSPeerAddress address) {
		peers.remove(address);
	}
	
//-------------------------------------------------------------------
	public static void updateNum(MSPeerAddress address, double[] ratio, double v) {
		PeerInfo peerInfo = peers.get(address);
		
		if (peerInfo == null)
			return;
		
		peerInfo.updateRatio(ratio, v);
	}

//-------------------------------------------------------------------
	public static void updateLoad(int load) {
		totalLoad += load;
	}

//-------------------------------------------------------------------
	public static void report() {
		String str = new String();
		str += "total number of peers: " + counter + " " + (peers.size() - 1) + "\n";
		str += "clive current time: " + counter + "\n";
		str += reportSlotDist();
		str += reportV();
		str += "total load: " + counter + " " + totalLoad + "\n";
		str += "###\n";
		
		System.out.println(str);
		
		FileIO.append(str, FILENAME);
		counter++;
	}
	
//-------------------------------------------------------------------
	private static String reportSlotDist() {
		PeerInfo peerInfo;
		String str = new String();
		double[] total = new double[Configuration.SLOT_RANGE];
		double[] hitRatio = new double[Configuration.SLOT_RANGE];
		double[] realTotal = new double[Configuration.SLOT_RANGE];
		double[] realRatio = new double[Configuration.SLOT_RANGE];
		double[] avgErr = new double[Configuration.SLOT_RANGE];
		double maxErr = 0;
		double sumErr = 0;
		double localErr = 0;
		double localAvgErr = 0;
		double err;
		int count = peers.size();
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++) {
			total[i] = 0;
			hitRatio[i] = 0;
			realTotal[i] = 0;
			realRatio[i] = 0;
			avgErr[i] = 0;
		}

		// find the real distribution
		for (MSPeerAddress peer : peers.keySet()) {
			peerInfo = peers.get(peer);
			int realSlot = peerInfo.getBW();
			realTotal[realSlot] += 1;
		}
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			realRatio[i] = realTotal[i] / count;

		// find avg. node distribution		
		for (MSPeerAddress peer : peers.keySet()) {
			peerInfo = peers.get(peer);

			double[] d = peerInfo.getRatio();
			if (d == null)
				continue;
			
			localErr = 0;
			for (int i = 0; i < Configuration.SLOT_RANGE; i++) {
				total[i] += d[i];
				
				err = Math.abs(realRatio[i] - d[i]);
				localErr += err;				
			}
			
			localAvgErr = localErr / Configuration.SLOT_RANGE;
			sumErr += localAvgErr;
			
			if (localAvgErr > maxErr)
				maxErr = localAvgErr;
		}
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			hitRatio[i] = total[i] / count;

		double maxDiff = 0;
		double diff = 0;
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++) {
			diff = Math.abs(realRatio[i] - hitRatio[i]);
			
			if (diff > maxDiff)
				maxDiff = diff;
		}
		
		str += "real distribution: "; 
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			str += df.format(realRatio[i]) + ", ";

		str += "\n";
		
		str += "distribution: "; 
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			str += df.format(hitRatio[i]) + ", ";

		str += "\n";

		str += "max ks distance: " + counter + " " + maxErr + "\n";  
		str += "avg ks distance: " + counter + " " + (sumErr / (double)count) + "\n";
		str += "totalmaxks distance: " + counter + " " + maxDiff + "\n";
		
		return str;
	}

//-------------------------------------------------------------------
	private static String reportV() {
		PeerInfo peerInfo;
		String str = new String();
		double sum = 0;
		int count = peers.size();
		
		for (MSPeerAddress peer : peers.keySet()) {
			peerInfo = peers.get(peer);
			sum += peerInfo.getV();
		}
		
		str += "v: " + counter + " " + ((double)count / sum) + "\n";
		
		return str;
	}

}
