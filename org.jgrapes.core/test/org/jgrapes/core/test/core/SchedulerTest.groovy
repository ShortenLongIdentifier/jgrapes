package org.jgrapes.core.test.core;

import java.time.Instant

import org.jgrapes.core.Components
import org.jgrapes.core.Components.Timer

import spock.lang.Specification

class SchedulerTest extends Specification {

	void "Basic Scheduler Test"() {
		setup: "Initialize controlled variables"
		boolean hit1 = false;
		boolean hit2 = false;
		
		when: "Schedule and wait for first"
		Closure<Void> setHit1 = { Instant scheduledFor -> hit1 = true };
		Components.schedule(setHit1,
			Instant.now().plusMillis(500));
		Components.schedule({ scheduledFor -> hit2 = true },
			Instant.now().plusMillis(1000));
		Timer timer3 = Components.schedule({ scheduledFor -> hit1 = false },
			Instant.now().plusMillis(1500));
		Thread.sleep(750);
		
		then: "First set, second not"
		hit1;
		!hit2;
		
		when: "Waited longer"
		Thread.sleep(500);
		
		then:
		hit1;
		hit2;
		
		when: "Cancel and wait"
		timer3.cancel();
		Thread.sleep(750);
		
		then: "Nothing happened"
		hit1;
		hit2;
	}

}
