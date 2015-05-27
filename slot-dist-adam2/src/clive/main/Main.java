package clive.main;

import clive.simulator.scenario.Scenario;
import clive.simulator.scenario.ScenarioChurn;

public class Main {
	public static void main(String[] args) throws Throwable {
		Configuration configuration = new Configuration();
		configuration.set();
		
		Scenario scenario = new ScenarioChurn();
		scenario.setSeed(0);
		scenario.simulate();
	}
}
