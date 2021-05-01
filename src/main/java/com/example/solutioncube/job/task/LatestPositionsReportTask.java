package com.example.solutioncube.job.task;

import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.solutioncube.common.Task;
import com.example.solutioncube.common.TaskParameter;

@Component
public class LatestPositionsReportTask {

	@Autowired
	Task task; 

	private final String BASE_COLLECTION_NAME = "Trackers";
	private final String COLLECTION_NAME = this.getClass().getName().substring(34, this.getClass().getName().length() - 4);
	private final String URI = "https://api.triomobil.com/facility/v1/reports/indoor/positions/latest?trackerId=%s";
  
	public void executeDaily(TaskParameter taskParameter) {
		
		List<String> trackers = taskParameter.getMongoTemplate().findAll(String.class, BASE_COLLECTION_NAME);
		
		for (String tracker : trackers) {
			
			JSONObject trackerJSONObject = new JSONObject(tracker);
			String trackerId = trackerJSONObject.getString("_id");
			taskParameter.setUri(String.format(URI, trackerId));
			taskParameter.setCollectionName(COLLECTION_NAME);
			task.execute(taskParameter);
		}		
	}
}
