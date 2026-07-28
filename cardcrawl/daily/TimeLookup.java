package com.megacrit.cardcrawl.daily;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class TimeLookup {
    private static final Logger logger = LogManager.getLogger(TimeLookup.class.getName());

    public static volatile boolean isDone = false;

    private static final String timeServer = "https://hyi3lwrhf5.execute-api.us-east-1.amazonaws.com/prod/time";
    private static volatile AtomicInteger retryCount = new AtomicInteger(1);

    private static final int MAX_RETRY = 2;

    private static final int WAIT_TIME_CAP = 2;

    private static void makeHTTPReq(String url) {
        HttpRequestBuilder requestBuilder = new HttpRequestBuilder();

        Net.HttpRequest httpRequest = requestBuilder.newRequest().method("GET").url(url)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("User-Agent", "curl/7.43.0").timeout(5000).build();
        Gdx.net.sendHttpRequest(httpRequest, new Net.HttpResponseListener() {
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String status = String.valueOf(httpResponse.getStatus().getStatusCode());
                String result = httpResponse.getResultAsString();
                if (!status.startsWith("2")) {
                    TimeLookup.logger
                            .info("Query to sts-time-server failed: status_code=" + status + " result=" + result);
                }
                TimeLookup.logger.info("Time server response: " + result);
                long serverTime = Long.parseLong(result);
                TimeLookup.isDone = true;
                TimeHelper.setTime(serverTime, false);
            }

            public void failed(Throwable t) {
                TimeLookup.logger.info("http request failed: " + t.toString());
                TimeLookup.logger.info("retry count: " + TimeLookup.retryCount);

                if (TimeLookup.retryCount.get() > 2) {
                    TimeLookup.logger.info("Failed to lookup time. Switching to OFFLINE MODE!");
                    long localTime = System.currentTimeMillis() / 1000L;
                    TimeHelper.setTime(localTime, true);
                    return;
                }
                long waitTime = (long) Math.pow(2.0D, TimeLookup.retryCount.get());
                TimeLookup.logger.info("wait time: " + waitTime);
                if (waitTime > 2L) {
                    waitTime = 2L;
                }
                TimeLookup.logger.info(
                        "Retry " + TimeLookup.retryCount.get() + ": waiting " + waitTime + " seconds for time lookup");
                TimeLookup.retryCount.getAndIncrement();
                try {
                    Thread.sleep(waitTime * 1000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    TimeLookup.logger.info("Thread interrupted!");
                }
                TimeLookup.makeHTTPReq("https://hyi3lwrhf5.execute-api.us-east-1.amazonaws.com/prod/time");
            }

            public void cancelled() {
                TimeLookup.logger.info("http request cancelled.");
            }
        });
    }

    public static void fetchDailyTimeAsync() {
        makeHTTPReq("https://hyi3lwrhf5.execute-api.us-east-1.amazonaws.com/prod/time");
    }
}

/*
 * Location:
 * E:\代码\SlayTheSpire\desktop-1.0.jar!\com\megacrit\cardcrawl\daily\TimeLookup.
 * class Java compiler version: 8 (52.0) JD-Core Version: 1.1.3
 */

