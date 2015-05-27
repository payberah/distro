package clive.simulator.scenario;

import java.math.BigDecimal;
import java.math.BigInteger;

import clive.main.Configuration;

import se.sics.kompics.p2p.experiment.dsl.distribution.Distribution;

public class DistPareto extends Distribution<BigInteger> {
	private static final long serialVersionUID = 6853092446046319743L;

	public DistPareto() {
		super(Type.OTHER, BigInteger.class);
	}

	
//-------------------------------------------------------------------
	@Override
	public final BigInteger draw() {
		double num = StdRandom.pareto(5) + 1;
		
		if (num >= Configuration.SLOT_RANGE)
			num = Configuration.SLOT_RANGE - 1;
		
		return new BigDecimal(num).toBigInteger();		
	}
	
//-------------------------------------------------------------------
	public static void main(String args[]) {
		int num;
		double d[] = new double[Configuration.SLOT_RANGE];
		DistPareto dist = new DistPareto();

		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			d[i] = 0;

		for (int i = 0; i < 1000; i++) {
			num = dist.draw().intValue();
			d[num]++;
		}
		
		for (int i = 0; i < Configuration.SLOT_RANGE; i++)
			System.out.println("num of peer in group " + i + ": " + d[i]);
	}
}
