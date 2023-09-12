package com.example.foregroundservice;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {
    public static final String TAG = HttpUtil.class.getSimpleName();
    public static final int POST = 1;
    public static final int GET = 2;
    public static final int PUT = 3;
    public static final int DELETE = 4;

    private static String convertHttpType(int type) {
        switch (type) {
            case POST:
                return "POST";
            case GET:
                return "GET";
            case PUT:
                return "PUT";
            case DELETE:
                return "DELETE";
            default:
                return "UNKNOWN";
        }
    }

    public static void callApi(JSONArray params, int type) {
        if (type < POST || type > DELETE) return;

        HttpURLConnection conn = null;
        StringBuilder buffer = null;
        String readLine = null;

        BufferedWriter bufferedWriter = null;
        BufferedReader bufferedReader = null;

        try{
            URL url = new URL("http://localhost:8080/data/timeseries/");

            conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod(convertHttpType(type));
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Transfer-Encoding", "chunked");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);

            bufferedWriter = new BufferedWriter(
                    new OutputStreamWriter(conn.getOutputStream(), "UTF-8"));
            bufferedWriter.write(params.toString());
            bufferedWriter.flush();
            bufferedWriter.close();

            buffer = new StringBuilder();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                bufferedReader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                while ((readLine = bufferedReader.readLine()) != null) {
                    buffer.append(readLine).append("\n");
                }
            } else {
                buffer.append("\"code\" : \"" + conn.getResponseCode()+"\"");
                buffer.append(", \"message\" : \"" + conn.getResponseMessage()+"\"");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bufferedWriter != null) bufferedWriter.close();
                if (bufferedReader != null) bufferedReader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "result: " + buffer.toString());
    }
}
