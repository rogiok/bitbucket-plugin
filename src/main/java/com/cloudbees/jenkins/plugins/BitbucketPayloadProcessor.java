package com.cloudbees.jenkins.plugins;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import net.sf.json.JSONObject;

public class BitbucketPayloadProcessor {

    private final BitbucketJobProbe probe;

    public BitbucketPayloadProcessor(BitbucketJobProbe probe) {
        this.probe = probe;
    }

    public BitbucketPayloadProcessor() {
        this(new BitbucketJobProbe());
    }

    public void processPayload(JSONObject payload, HttpServletRequest request) {

        System.out.println("User-Agent: " + getHeaderWithCaseInsensitive(request, "User-Agent"));
        System.out.println("X-Event-Key: " + getHeaderWithCaseInsensitive(request, "X-Event-Key"));
        System.out.println("JSON payload: " + payload.toString());

        if ("Bitbucket-Webhooks/2.0".equals(getHeaderWithCaseInsensitive(request, "User-Agent"))) {
            if ("repo:push".equals(getHeaderWithCaseInsensitive(request, "X-Event-Key"))) {
                LOGGER.info("Processing new Webhooks payload");
                processWebhookPayload(payload);
            }
        } else {
            LOGGER.info("Processing old POST service payload");
            processPostServicePayload(payload);
        }
    }

    private String getHeaderWithCaseInsensitive(HttpServletRequest request, String headerName) {

        String value = request.getHeader(headerName);

        if (value == null)
            value = request.getHeader(headerName.toLowerCase());

        return value;
    }

    private void processWebhookPayload(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        LOGGER.info("Received commit hook notification for "+repo);

        HashMap<String, String> envVars = new HashMap<String, String>();

        String user = payload.getJSONObject("actor").getString("username");
        String url = repo.getJSONObject("links").getJSONObject("html").getString("href");
        String scm = repo.has("scm") ? repo.getString("scm") : "git";

        envVars.put("USER_NAME", user);

        if (payload.has("push"))
            envVars.put("BRANCH_NAME", payload.getJSONObject("push").getJSONArray("changes").getJSONObject(0).getJSONObject("new").getString("name"));

        probe.triggerMatchingJobs(user, url, scm, envVars);
    }

/*
{
    "canon_url": "https://bitbucket.org",
    "commits": [
        {
            "author": "marcus",
            "branch": "master",
            "files": [
                {
                    "file": "somefile.py",
                    "type": "modified"
                }
            ],
            "message": "Added some more things to somefile.py\n",
            "node": "620ade18607a",
            "parents": [
                "702c70160afc"
            ],
            "raw_author": "Marcus Bertrand <marcus@somedomain.com>",
            "raw_node": "620ade18607ac42d872b568bb92acaa9a28620e9",
            "revision": null,
            "size": -1,
            "timestamp": "2012-05-30 05:58:56",
            "utctimestamp": "2012-05-30 03:58:56+00:00"
        }
    ],
    "repository": {
        "absolute_url": "/marcus/project-x/",
        "fork": false,
        "is_private": true,
        "name": "Project X",
        "owner": "marcus",
        "scm": "git",
        "slug": "project-x",
        "website": "https://atlassian.com/"
    },
    "user": "marcus"
}
*/
    private void processPostServicePayload(JSONObject payload) {
        JSONObject repo = payload.getJSONObject("repository");
        LOGGER.info("Received commit hook notification for "+repo);

        String user = payload.getString("user");
        String url = payload.getString("canon_url") + repo.getString("absolute_url");
        String scm = repo.getString("scm");

        probe.triggerMatchingJobs(user, url, scm);
    }

    private static final Logger LOGGER = Logger.getLogger(BitbucketPayloadProcessor.class.getName());

}
