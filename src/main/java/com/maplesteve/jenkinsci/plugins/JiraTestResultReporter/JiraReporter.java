package com.maplesteve.jenkinsci.plugins.JiraTestResultReporter;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.DataBoundConstructor;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.AbstractHttpClient;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.List;

public class JiraReporter extends Notifier {

    private String configProjectKey;
    private String configServerAddress;
    private String configUsername;
    private String configPassword;

    private boolean configDebugFlag;
    private boolean configVerboseDebugFlag;

    private FilePath workspace;

    private static final int JIRA_SUCCESS_CODE = 201;

    private static final String PluginName = new String("[JiraTestResultReporter]");
    private final String pInfo = String.format("%s [INFO]", PluginName);
    private final String pDebug = String.format("%s [DEBUG]", PluginName);
    private final String pVerbose = String.format("%s [DEBUGVERBOSE]", PluginName);
    private final String prefixError = String.format("%s [ERROR]", PluginName);

    @DataBoundConstructor
    public JiraReporter(final String projectKey,
                        final String serverAddress,
                        final String username,
                        final String password,
                        final boolean debugFlag,
                        final boolean verboseDebugFlag) {
        if (serverAddress.endsWith("/")) {
            this.configServerAddress = serverAddress;
        } else {
            this.configServerAddress = serverAddress + "/";
        }

        this.configProjectKey = projectKey;
        this.configUsername = username;
        this.configPassword = password;

        this.configVerboseDebugFlag = verboseDebugFlag;
        if (verboseDebugFlag) {
            this.configDebugFlag = true;
        } else {
            this.configDebugFlag = debugFlag;
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Override
    public boolean perform(final AbstractBuild build,
                           final Launcher launcher,
                           final BuildListener listener) {
        PrintStream logger = listener.getLogger();
        logger.printf("%s Examining test results...", pInfo);
        debugLog(listener,
                 String.format("Build result is %s%n",
                    build.getResult().toString())
                );
        this.workspace = build.getWorkspace();
        debugLog(listener,
                 String.format("%s Workspace is %s%n",
                    pInfo, this.workspace.toString())
                );
//      if (build.getResult() == Result.UNSTABLE) {
            AbstractTestResultAction<?> testResultAction = build.getTestResultAction();
            List<CaseResult> failedTests = testResultAction.getFailedTests();
            printResultItems(failedTests, listener);
            createJiraIssue(failedTests, listener);
//      }
        logger.printf("%s Done.", pInfo);
        return true;
    }

    private void printResultItems(final List<CaseResult> failedTests,
                                  final BuildListener listener) {
        if (!this.configDebugFlag) {
            return;
        }
        PrintStream out = listener.getLogger();
        for (CaseResult result : failedTests) {
            out.printf("%s projectKey: %s%n", pDebug, this.configProjectKey);
            out.printf("%s errorDetails: %s%n", pDebug, result.getErrorDetails());
            out.printf("%s fullName: %s%n", pDebug, result.getFullName());
            out.printf("%s simpleName: %s%n", pDebug, result.getSimpleName());
            out.printf("%s title: %s%n", pDebug, result.getTitle());
            out.printf("%s packageName: %s%n", pDebug, result.getPackageName());
            out.printf("%s name: %s%n", pDebug, result.getName());
            out.printf("%s className: %s%n", pDebug, result.getClassName());
            out.printf("%s failedSince: %d%n", pDebug, result.getFailedSince());
            out.printf("%s status: %s%n", pDebug, result.getStatus().toString());
            out.printf("%s age: %s%n", pDebug, result.getAge());
            out.printf("%s ErrorStackTrace: %s%n", pDebug, result.getErrorStackTrace());

            String affectedFile = result.getErrorStackTrace().replace(this.workspace.toString(), "");
            out.printf("%s affectedFile: %s%n", pDebug, affectedFile);
            out.printf("%s ----------------------------%n", pDebug);
        }
    }

    void debugLog(final BuildListener listener, final String message) {
        if (!this.configDebugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.printf("%s %s%n", pDebug, message);
    }

     void createJiraIssue(final List<CaseResult> failedTests,
                          final BuildListener listener) {
        PrintStream logger = listener.getLogger();
        String url = this.configServerAddress + "rest/api/2/issue/";

        for (CaseResult result : failedTests) {
            if (result.getAge() == 1) {
//          if (result.getAge() > 0) {
                debugLog(listener,
                         String.format("Creating issue in project %s at URL %s%n",
                            this.configProjectKey, url)
                        );
                try {
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                    Credentials creds = new UsernamePasswordCredentials(this.configUsername, this.configPassword);
                    ((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

                    HttpPost postRequest = new HttpPost(url);
                    String jsonPayLoad = new String("{\"fields\": {\"project\": {\"key\": \"" + this.configProjectKey + "\"},\"summary\": \"The test " + result.getName() + " failed " + result.getClassName() + ": " + result.getErrorDetails() + "\",\"description\": \"Test class: " + result.getClassName() + " -- " + result.getErrorStackTrace().replace(this.workspace.toString(), "") + "\",\"issuetype\": {\"name\": \"Bug\"}}}");
                    logger.printf("%s JSON payload: ", pVerbose, jsonPayLoad);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");
                    postRequest.setEntity(params);
                    try {
                        postRequest.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials(this.configUsername, this.configPassword), postRequest));
                    } catch (AuthenticationException a) {
                        a.printStackTrace();
                    }

                    HttpResponse response = httpClient.execute(postRequest);
                    debugLog(listener,
                             String.format("statusLine: %s%n",
                                response.getStatusLine())
                            );
                    debugLog(listener,
                             String.format("statusCode: %d%n",
                                response.getStatusLine().getStatusCode())
                            );
                    if (response.getStatusLine().getStatusCode() != JIRA_SUCCESS_CODE) {
                        throw new RuntimeException("[ERROR] Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                    }

                    httpClient.getConnectionManager().shutdown();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.printf("%s This issue is old; not reporting.%n", pInfo);
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Jira Test Result Reporter";
        }
    }
}

