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

	private static HttpClient httpClient = new HttpClient();
 
    public static boolean sent(SparkRoom sparkRoom, String content) throws Exception {
    	 if(content == null || content.isEmpty())
    		 return true;
    	 
         try {
            System.out.println(sparkRoom);
            System.out.println(content);
             
            PostMethod postMethod = new PostMethod(HTTPS_API_CISCOSPARK_COM_V1 + "/messages");
            postMethod.addRequestHeader("Content-type", APPLICATION_JSON_CHARSET_UTF_8);
            postMethod.addRequestHeader("Authorization", "Bearer " + sparkRoom.getToken().trim());
            
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("roomId", sparkRoom.getRoomid());
            jsonObject.put("text", content);
            
            postMethod.setRequestEntity(new StringRequestEntity(jsonObject.toString(), "application/json", "UTF-8"));
            int statusCode = httpClient.executeMethod(postMethod);
            
            return isSuccess(statusCode);
            
         } catch (Exception e) {
            throw e;
        }

    }

	private static boolean isSuccess(int statusCode) {
		return statusCode>=200 && statusCode<300;
	}

	public static void invite(SparkRoom sparkRoom, HashSet<String> scmCommiterEmails) {
		if (scmCommiterEmails == null || scmCommiterEmails.isEmpty())
			return;

		for (String emailStr : scmCommiterEmails) {
			try {
				PostMethod postMethod = new PostMethod(HTTPS_API_CISCOSPARK_COM_V1 + "/memberships");
				postMethod.addRequestHeader("Content-type", APPLICATION_JSON_CHARSET_UTF_8);
				postMethod.addRequestHeader("Authorization", "Bearer " + sparkRoom.getToken().trim());

				JSONObject jsonObject = new JSONObject();
				jsonObject.put("roomId", sparkRoom.getRoomid());
				jsonObject.put("personEmail", emailStr.trim());

				postMethod.setRequestEntity(new StringRequestEntity(jsonObject.toString(), "application/json", "UTF-8"));
				httpClient.executeMethod(postMethod);

			} catch (Exception e) {

			}
		}

	}

}