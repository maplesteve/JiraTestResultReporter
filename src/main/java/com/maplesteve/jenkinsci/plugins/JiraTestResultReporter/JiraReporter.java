package com.maplesteve.jenkinsci.plugins.JiraTestResultReporter;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
// import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
// import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
// import hudson.model.Result;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.DataBoundConstructor;
// import org.kohsuke.stapler.StaplerRequest;
// import org.kohsuke.stapler.QueryParameter;

import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.AbstractHttpClient;

// import java.io.BufferedReader;
import java.io.IOException;
// import java.io.InputStreamReader;
import java.net.MalformedURLException;
// import javax.servlet.ServletException;

import java.io.PrintStream;
import java.util.List;

public class JiraReporter extends Notifier {

    public final String configProjectKey;
    public final String configServerAddress;
    public final String configUsername;
    public final String configPassword;

    public final boolean configDebugFlag;
    public final boolean configVerboseDebugFlag;

    public FilePath workspace;

    public final static String piName = new String("[JiraTestResultReporter]");
    public final String prefixInfo = String.format("%s [INFO]", piName);
    public final String prefixDebug = String.format("%s [DEBUG]", piName);
    public final String prefixDebugVerbose = String.format("%s [DEBUGVERBOSE]", piName);
    public final String prefixError = String.format("%s [ERROR]", piName);

    @DataBoundConstructor
    public JiraReporter(String projectKey, 
                        String serverAddress, 
                        String username, 
                        String password, 
                        boolean debugFlag, 
                        boolean verboseDebugFlag) {
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
    public boolean perform(AbstractBuild build, 
                            Launcher launcher, 
                            BuildListener listener) {
        PrintStream logger = listener.getLogger();
        logger.printf("%s Examining test results...", prefixInfo);
        debugLog(listener, String.format("Build result is %s%n", build.getResult().toString()));
        this.workspace = build.getWorkspace();
        debugLog(listener, String.format("%s Workspace is %s%n", prefixInfo, this.workspace.toString()));
//      if (build.getResult() == Result.UNSTABLE) {
            AbstractTestResultAction<?> testResultAction = build.getTestResultAction();
            List<CaseResult> failedTests = testResultAction.getFailedTests();
            printResultItems(failedTests, listener);
            createJiraIssue(failedTests, listener);
//      }
        logger.printf("%s Done.", prefixInfo);
        return true;
    }

    private void printResultItems(List<CaseResult> failedTests, BuildListener listener) {
        if (!this.configDebugFlag) {
            return;
        }
        PrintStream oStream = listener.getLogger();
        for (CaseResult result : failedTests) {
            oStream.printf("%s projectKey: %s%n", prefixDebug, this.configProjectKey);
            oStream.printf("%s errorDetails: %s%n", prefixDebug, result.getErrorDetails());
            oStream.printf("%s fullName: %s%n", prefixDebug, result.getFullName());
            oStream.printf("%s simpleName: %s%n", prefixDebug, result.getSimpleName());
            oStream.printf("%s title: %s%n", prefixDebug, result.getTitle());
            oStream.printf("%s packageName: %s%n", prefixDebug, result.getPackageName());
            oStream.printf("%s name: %s%n", prefixDebug, result.getName());
            oStream.printf("%s className: %s%n", prefixDebug, result.getClassName());
            oStream.printf("%s failedSince: %d%n", prefixDebug, result.getFailedSince());
            oStream.printf("%s status: %s%n", prefixDebug, result.getStatus().toString());
            oStream.printf("%s age: %s%n", prefixDebug, result.getAge());
            oStream.printf("%s ErrorStackTrace: %s%n", prefixDebug, result.getErrorStackTrace());

            String affectedFile = result.getErrorStackTrace().replace(this.workspace.toString(), "");
            oStream.printf("%s affectedFile: %s%n", prefixDebug, affectedFile);
            oStream.printf("%s ----------------------------%n", prefixDebug);
        }
    }

    void debugLog(BuildListener listener, String message) {
        if (!this.configDebugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.printf("%s %s%n", prefixDebug, message);
    }

     void createJiraIssue(List<CaseResult> failedTests, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        String url = this.configServerAddress + "rest/api/2/issue/";

        for (CaseResult result : failedTests) {
            if (result.getAge() == 1) {
//          if (result.getAge() > 0) {
                debugLog(listener, String.format("Creating issue in project %s at URL %s%n", this.configProjectKey, url));
//              logger.printf("[JiraTestResultReporter] [DEBUG] Creating issue in project %s at URL %s%n", this.projectKey, url);

                try {
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                    Credentials creds = new UsernamePasswordCredentials(this.configUsername, this.configPassword);
                    ((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, creds);

                    HttpPost postRequest = new HttpPost(url);
                    String jsonPayLoad = new String("{\"fields\": {\"project\": {\"key\": \"" + this.configProjectKey + "\"},\"summary\": \"The test " + result.getName() + " failed " + result.getClassName() + ": " + result.getErrorDetails() + "\",\"description\": \"Test class: " + result.getClassName() + " -- " + result.getErrorStackTrace().replace(this.workspace.toString(), "") + "\",\"issuetype\": {\"name\": \"Bug\"}}}");
                    logger.printf("%s JSON payload: ", prefixDebugVerbose, jsonPayLoad);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");
                    postRequest.setEntity(params);
                    try {
                        postRequest.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials(this.configUsername, this.configPassword), postRequest));
                    } catch (AuthenticationException a) {
                        a.printStackTrace();
                    }

                    HttpResponse response = httpClient.execute(postRequest);
                    debugLog(listener, String.format("statusLine: %s%n", response.getStatusLine()));
                    debugLog(listener, String.format("statusCode: %d%n", response.getStatusLine().getStatusCode()));
                    if (response.getStatusLine().getStatusCode() != 201) {
                        throw new RuntimeException("[ERROR] Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                    }

                    httpClient.getConnectionManager().shutdown();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.printf("%s This issue is old; not reporting.%n", prefixInfo);
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
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Jira Test Result Reporter";
        }
    }
}

