package org.jenkinsci.plugins.spark.client;

import java.util.HashSet;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.jenkinsci.plugins.spark.SparkRoom;

import net.sf.json.JSONObject;

public final class SparkClient {

	private static final String HTTPS_API_CISCOSPARK_COM_V1 = "https://api.ciscospark.com/v1";
	private static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
	
	private static final String PERSON_EMAIL = "personEmail";
	private static final String TEXT = "text";
	private static final String ROOM_ID = "roomId";
	
	private static final String APPLICATION_JSON = "application/json";
	private static final String UTF_8 = "UTF-8";
	private static final String CONTENT_TYPE = "Content-type";
	private static final String AUTHORIZATION = "Authorization";
	private static final String BEARER = "Bearer ";

	private static HttpClient httpClient = new HttpClient();

	/**
	 * @param sparkRoom
	 * @param content 
	 * @return
	 * @throws Exception
	 */
	public static boolean sent(SparkRoom sparkRoom, String content) throws Exception {
		if (content == null || content.isEmpty())
			return true;

		try {
			PostMethod postMethod = new PostMethod(HTTPS_API_CISCOSPARK_COM_V1 + "/messages");
			postMethod.addRequestHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8);
			postMethod.addRequestHeader(AUTHORIZATION, BEARER + sparkRoom.getToken().trim());

			JSONObject jsonObject = new JSONObject();
			jsonObject.put(ROOM_ID, sparkRoom.getRoomid());
			jsonObject.put(TEXT, content);

			postMethod.setRequestEntity(new StringRequestEntity(jsonObject.toString(), APPLICATION_JSON, UTF_8));
			int statusCode = httpClient.executeMethod(postMethod);

			return isSuccess(statusCode);

		} catch (Exception e) {
			throw e;
		}

	}

	private static boolean isSuccess(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	/**
	 * @param sparkRoom
	 * @param scmCommiterEmails
	 */
	public static void invite(SparkRoom sparkRoom, HashSet<String> scmCommiterEmails) {
		if (scmCommiterEmails == null || scmCommiterEmails.isEmpty())
			return;

		for (String emailStr : scmCommiterEmails) {
			try {
				PostMethod postMethod = new PostMethod(HTTPS_API_CISCOSPARK_COM_V1 + "/memberships");
				postMethod.addRequestHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8);
				postMethod.addRequestHeader(AUTHORIZATION, BEARER + sparkRoom.getToken().trim());

				JSONObject jsonObject = new JSONObject();
				jsonObject.put(ROOM_ID, sparkRoom.getRoomid());
				jsonObject.put(PERSON_EMAIL, emailStr.trim());

				postMethod.setRequestEntity(new StringRequestEntity(jsonObject.toString(), APPLICATION_JSON, UTF_8));
				httpClient.executeMethod(postMethod);

			} catch (Exception e) {
				//ignore it
			}
		}

	}

}