package clive.simulator.scenario;

import clive.main.Configuration;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

@SuppressWarnings("serial")
public class ScenarioJoinOnly extends Scenario {
	private static SimulationScenario scenario = new SimulationScenario() {{
		final DistUniform distro = new DistUniform();
		//final DistExp distro = new DistExp();
		//final DistPareto distro = new DistPareto();
		
		StochasticProcess sourceJoin = new StochasticProcess() {{
			eventInterArrivalTime(exponential(100));
			raise(1, Operations.sourceJoin(50));
		}};

		StochasticProcess helperJoin = new StochasticProcess() {{
			eventInterArrivalTime(exponential(100));
			raise(1, Operations.passiveHelperJoin());
		}};
		
		StochasticProcess nodesJoin = new StochasticProcess() {{
			eventInterArrivalTime(exponential(1));
			raise(10000, Operations.msJoin(Configuration.PARENT_SIZE), distro, uniform(20));
		}};

		sourceJoin.start();
		helperJoin.startAfterTerminationOf(10, sourceJoin);
		nodesJoin.startAfterTerminationOf(2000, sourceJoin);
	}};
	
//-------------------------------------------------------------------
	public ScenarioJoinOnly() {
		super(scenario);
	} 
}
