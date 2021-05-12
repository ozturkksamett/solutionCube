package com.solutioncube.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.BasicDBObject;
import com.solutioncube.pojo.ApiResponse;
import com.solutioncube.pojo.TaskParameter;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class Task {

	private static final Logger logger = LoggerFactory.getLogger(Task.class);

	private MongoTemplate mongoTemplate;
	
	public static int requestCount = 0;

	public synchronized void execute(TaskParameter taskParameter) {
		
		this.mongoTemplate = taskParameter.getMongoTemplate();		
		
		try {

			executeTask(taskParameter);
		} catch (Exception e) {
	
			logger.error("\nError while executing task"
					+ "\nTask Parameter: " + taskParameter.toString() 
					+ "\nException: " + e.getMessage());			
		}
	}

	private void executeTask(TaskParameter taskParameter) {

		ApiResponse apiResponse = callApi(taskParameter);
		JSONArray jsonArray = new JSONArray(apiResponse.getResponseBody());
		saveJsonArray(taskParameter, jsonArray);
		
		String uri = taskParameter.getUri();
		Map<String, String> params = getParams(uri);
		if (params.containsKey("_perPage")) {

			int page = params.get("_page") != null ? Integer.parseInt(params.get("_page")) : 1;			
			boolean isResultHasNextPage = determineIfIsResultHasNextPage(params, apiResponse); 			
			while (isResultHasNextPage) {

				params.put("_page", Integer.toString(++page));
				uri = setParams(uri, params);
				taskParameter.setUri(uri);
				try {

					apiResponse = callApi(taskParameter);					
					jsonArray = new JSONArray(apiResponse.getResponseBody());
					saveJsonArray(taskParameter, jsonArray);
					isResultHasNextPage = determineIfIsResultHasNextPage(params, apiResponse);
				} catch (Exception e) {
			
					logger.error("\nError while reexecuting task(pagination)"
							+ "\nTask Parameter: " + taskParameter.toString() 
							+ "\nException: " + e.getMessage());
					isResultHasNextPage = false;
				}
			}
		} else if (params.containsKey("_limit")) {

			boolean isResultTooLarge = Boolean.parseBoolean(apiResponse.getHeaders().get("x-trio-result-set-too-large"));
			while (isResultTooLarge) {

				try {

					jsonArray = new JSONArray(apiResponse.getResponseBody());					
					String lastTimestamp = jsonArray.getJSONObject(jsonArray.length() - 1).getString("ts");
					params.put("ts.since", lastTimestamp);
					uri = setParams(uri, params);
					taskParameter.setUri(uri);
					apiResponse = callApi(taskParameter);
					logger.info(taskParameter.getCollectionName() + " Request Count: "+(++requestCount));					
					jsonArray = new JSONArray(apiResponse.getResponseBody());
					jsonArray.remove(0);
					saveJsonArray(taskParameter, jsonArray);
					isResultTooLarge = Boolean.parseBoolean(apiResponse.getHeaders().get("x-trio-result-set-too-large"));

					String remainingRequestCount = apiResponse.getHeaders().get("X-RateLimit-Remaining");
					String remainingTimeToResetRequestCount = apiResponse.getHeaders().get("X-RateLimit-Reset");
					logger.info(taskParameter.getCollectionName() + " Remaining Request Count: "+remainingRequestCount);
					logger.info(taskParameter.getCollectionName() + " Remaining Time To Reset Request Count: "+remainingTimeToResetRequestCount);
					if(Integer.parseInt(remainingRequestCount) < 10) {
						int sleepTime = (Integer.parseInt(remainingTimeToResetRequestCount));
						logger.info("Sleeping.. " + sleepTime + " seconds");
						wait(sleepTime*1000);						
					}
				} catch (Exception e) {
					
					isResultTooLarge = false;
					logger.error("\nError while reexecuting task(limit)"
							+ "\nTask Parameter: " + taskParameter.toString() 
							+ "\nException: " + e.getMessage());					
				}
			}
		}
	}

	private ApiResponse callApi(TaskParameter taskParameter) {

		logger.info(taskParameter.getCollectionName() + " Calling Api..");
		
		Request request = new Request.Builder().url(taskParameter.getUri()).get().addHeader("authorization", taskParameter.getToken()).build();
		
		OkHttpClient client = new OkHttpClient();
		client.setConnectTimeout(60, TimeUnit.MINUTES);
		client.setReadTimeout(60, TimeUnit.MINUTES);

		ApiResponse apiResponse = null;
		try {
						
			Response response = client.newCall(request).execute();			
			apiResponse = new ApiResponse(response.body().string(), response.headers());
			new JSONArray(apiResponse.getResponseBody());
			return apiResponse;
		} catch (Exception e) {
			
			logger.error("\nError while calling api"
					+ "\nTask Parameter: " + taskParameter.toString()
					+ "\nApi Response:" + apiResponse.toString()
					+ "\nException: " + e.getMessage());
		}

		return apiResponse;
	}

	private boolean determineIfIsResultHasNextPage(Map<String, String> params, ApiResponse apiResponse) {

		String isResultHasNextPageHeader = apiResponse.getHeaders().get("X-TRIO-Result-Set-Has-Next-Page");
		boolean isResultHasNextPage = isResultHasNextPageHeader == null ? false : Boolean.parseBoolean(isResultHasNextPageHeader);
		
		String totalCountHeader = apiResponse.getHeaders().get("x-trio-total-count");
		int totalCount = totalCountHeader == null ? 0 : Integer.parseInt(totalCountHeader);
		int page = params.get("_page") != null ? Integer.parseInt(params.get("_page")) : 1;
		int perPage = Integer.parseInt(params.get("_perPage"));
		int pageCount = (totalCount % perPage) == 0 ? (totalCount / perPage) : (totalCount / perPage) + 1;
		
		return isResultHasNextPage || page < pageCount;
	}

	private String setParams(String uri, Map<String, String> params) {

		uri = uri.split("\\?")[0];
		List<String> uriParams = new ArrayList<String>();
		for (String paramKey : params.keySet()) {
			uriParams.add(paramKey + "=" + params.get(paramKey));
		}
		if (uriParams.size() > 0) {

			uri += "?" + String.join("&", uriParams);
		}
		return uri;
	}

	private Map<String, String> getParams(String uri) {

		Map<String, String> params = new HashMap<String, String>();
		String[] uriParams = uri.split("\\?")[1].split("&");
		for (String uriParam : uriParams) {
			params.put(uriParam.split("=")[0], uriParam.split("=")[1]);
		}
		return params;
	}

	private void saveJsonArray(TaskParameter taskParameter, JSONArray jsonArray) {

		logger.info(taskParameter.getCollectionName() + " Saving Json Array Length: "+jsonArray.length());
		
		List<BasicDBObject> basicDBObjectList = new ArrayList<BasicDBObject>();
		
		for (int i = 0; i < jsonArray.length(); i++) {

			JSONObject jsonObject = jsonArray.getJSONObject(i);

			if (taskParameter.getId() != null) {

				jsonObject.put(taskParameter.getIdColumnName(), taskParameter.getId());
			}
			
			BasicDBObject basicDBObject = BasicDBObject.parse(jsonObject.toString());
			basicDBObjectList.add(basicDBObject);
		}
		
		try {

			mongoTemplate.insert(basicDBObjectList, taskParameter.getCollectionName());
			logger.info(taskParameter.getCollectionName() + " saved successfully.");
		} catch (Exception e) {

			logger.error("\nError while saving"
					+ "\nTaskParameter: " + taskParameter.toString() 
					+ "\nException: " + e.getMessage());
		}		
	}	
}