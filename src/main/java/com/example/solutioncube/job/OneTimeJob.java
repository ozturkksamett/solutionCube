package com.example.solutioncube.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.solutioncube.job.task.AlarmRulesTask;
import com.example.solutioncube.job.task.FloorPlansTask;
import com.example.solutioncube.job.task.SensorCountersTask;
import com.example.solutioncube.job.task.SensorsTask;
import com.example.solutioncube.job.task.TrackersTask;
import com.example.solutioncube.job.task.Zones;

@Component
public class OneTimeJob {

	@Autowired
	JobParameter jobParameter;
	
	@Autowired
	SensorsTask sensorsTask;
	
	@Autowired
	AlarmRulesTask alarmRulesTask;

	@Autowired
	SensorCountersTask sensorCountersTask;
	
	@Autowired
	TrackersTask trackersTask;
	
	@Autowired
	FloorPlansTask floorPlansTask;
	
	@Autowired
	Zones zones;
	
	public void execute() {
		System.out.println("job parameter başladı");
		jobParameter.generateJobParameter();
		
		System.out.println("Sensor task başladı");
		
		sensorsTask.executeOneTime();
		alarmRulesTask.executeOneTime();
		sensorCountersTask.executeOneTime();
		trackersTask.executeOneTime();
		floorPlansTask.executeOneTime();
		zones.executeOneTime();
		
	}
}
