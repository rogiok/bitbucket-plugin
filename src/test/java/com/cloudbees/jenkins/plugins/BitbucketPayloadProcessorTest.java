package com.cloudbees.jenkins.plugins;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPayloadProcessorTest {

    @Mock private HttpServletRequest request;
    @Mock private BitbucketJobProbe probe;

    private BitbucketPayloadProcessor payloadProcessor;

    @Before
    public void setUp() {
        payloadProcessor = new BitbucketPayloadProcessor(probe);
    }

    @Test
    public void testProcessWebhookPayload() {
        // Set headers so that payload processor will parse as new Webhook payload
        when(request.getHeader("user-agent")).thenReturn("Bitbucket-Webhooks/2.0");
        when(request.getHeader("x-event-key")).thenReturn("repo:push");

        String user = "test_user";
        String url = "https://bitbucket.org/test_user/test_repo";
        HashMap<String, String> envVars = new HashMap<String, String>();
        envVars.put("USER_NAME", user);

        JSONObject payload = new JSONObject()
            .element("actor", new JSONObject()
                .element("username", user))
            .element("repository", new JSONObject()
                .element("links", new JSONObject()
                    .element("html", new JSONObject()
                        .element("href", url))));

        payloadProcessor.processPayload(payload, request);
        
        verify(probe).triggerMatchingJobs(user, url, "git", envVars);
    }

    @Test
    public void testProcessWebhookPayloadWithEnvironmentVariables() {
        // Set headers so that payload processor will parse as new Webhook payload
        when(request.getHeader("user-agent")).thenReturn("Bitbucket-Webhooks/2.0");
        when(request.getHeader("x-event-key")).thenReturn("repo:push");

        String user = "test_user";
        String url = "https://bitbucket.org/test_user/test_repo";

        HashMap<String, String> envVars = new HashMap<String, String>();
        envVars.put("USER_NAME", user);
        envVars.put("BRANCH_NAME", "master");

        JSONObject payload = new JSONObject()
                .element("actor", new JSONObject()
                        .element("username", user))
                .element("repository", new JSONObject()
                        .element("links", new JSONObject()
                                .element("html", new JSONObject()
                                        .element("href", url))))
                .element("push", new JSONObject()
                        .element("changes", new JSONArray()
                                .element(new JSONObject()
                                        .element("new", new JSONObject()
                                                .element("name", "master")))));

        payloadProcessor.processPayload(payload, request);

        verify(probe).triggerMatchingJobs(user, url, "git", envVars);
    }

    @Test
    public void testProcessPostServicePayload() {
        // Ensure header isn't set so that payload processor will parse as old POST service payload
        when(request.getHeader("user-agent")).thenReturn(null);

        JSONObject payload = new JSONObject()
            .element("canon_url", "https://staging.bitbucket.org")
            .element("user", "old_user")
            .element("repository", new JSONObject()
                .element("scm", "git")
                .element("absolute_url", "/old_user/old_repo"));

        payloadProcessor.processPayload(payload, request);

        verify(probe).triggerMatchingJobs("old_user", "https://staging.bitbucket.org/old_user/old_repo", "git");
    }

}
