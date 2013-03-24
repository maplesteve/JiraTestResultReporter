package com.maplesteve.jenkinsci.plugins.JiraTestResultReporter;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.model.Result;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import javax.servlet.ServletException;

import java.io.PrintStream;
import java.util.List;

public class JiraReporter extends Notifier {

    public final String projectKey;
    public final String serverAddress;
    public final String username;
    public final String password;

    public final boolean debugFlag;
    public final boolean verboseDebugFlag;

    public FilePath workspace;
    @DataBoundConstructor
    public JiraReporter(String projectKey, String serverAddress, String username, String password, boolean debugFlag, boolean verboseDebugFlag) {
        if (serverAddress.endsWith("/")) {
            this.serverAddress = serverAddress;
        } else {
            this.serverAddress = serverAddress + "/";
        }

        this.projectKey = projectKey;
        this.username = username;
        this.password = password;

        this.verboseDebugFlag = verboseDebugFlag;       
        if (verboseDebugFlag) {
            this.debugFlag = true;
        } else {
            this.debugFlag = debugFlag;
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        logger.println("[JiraTestResultReporter] [INFO] Examining test results...");
        debugLog(listener, String.format("Build result is %s%n", build.getResult().toString()));
        this.workspace = build.getWorkspace();
        debugLog(listener, String.format("[JiraTestResultReporter] [INFO] Workspace is %s%n", this.workspace.toString()));
//      if (build.getResult() == Result.UNSTABLE) {
            AbstractTestResultAction<?> testResultAction = build.getTestResultAction();
            List<CaseResult> failedTests = testResultAction.getFailedTests();
            printResultItems(failedTests, listener);
            createJiraIssue(failedTests, listener);
//      }
        logger.println("[JiraTestResultReporter] [INFO] Done.");
        return true;
    }

    private void printResultItems(List<CaseResult> failedTests, BuildListener listener) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream oStream = listener.getLogger();
        for (CaseResult result : failedTests) {
            oStream.printf("[JiraTestResultReporter] [DEBUG] projectKey: %s%n", this.projectKey);
            oStream.printf("[JiraTestResultReporter] [DEBUG] errorDetails: %s%n", result.getErrorDetails());
            oStream.printf("[JiraTestResultReporter] [DEBUG] fullName: %s%n", result.getFullName());
            oStream.printf("[JiraTestResultReporter] [DEBUG] simpleName: %s%n", result.getSimpleName());
            oStream.printf("[JiraTestResultReporter] [DEBUG] title: %s%n", result.getTitle());
            oStream.printf("[JiraTestResultReporter] [DEBUG] packageName: %s%n", result.getPackageName());
            oStream.printf("[JiraTestResultReporter] [DEBUG] name: %s%n", result.getName());
            oStream.printf("[JiraTestResultReporter] [DEBUG] className: %s%n", result.getClassName());
            oStream.printf("[JiraTestResultReporter] [DEBUG] failedSince: %d%n", result.getFailedSince());
            oStream.printf("[JiraTestResultReporter] [DEBUG] status: %s%n", result.getStatus().toString());
            oStream.printf("[JiraTestResultReporter] [DEBUG] age: %s%n", result.getAge());
            oStream.printf("[JiraTestResultReporter] [DEBUG] ErrorStackTrace: %s%n", result.getErrorStackTrace());
            
            String affectedFile = result.getErrorStackTrace().replace(this.workspace.toString(), "");
            oStream.printf("[JiraTestResultReporter] [DEBUG] affectedFile: %s%n", affectedFile);
            oStream.printf("[JiraTestResultReporter] [DEBUG] ----------------------------%n");
        }
    }

    void debugLog(BuildListener listener, String message) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.print("[JiraTestResultReporter] [DEBUG] ");
        logger.println(message);
    }

     void createJiraIssue(List<CaseResult> failedTests, BuildListener listener) {
        PrintStream logger = listener.getLogger();
        String url = serverAddress + "rest/api/2/issue/";

        for (CaseResult result : failedTests) {
            if (result.getAge() == 1) {
//          if (result.getAge() > 0) {
                
                debugLog(listener, String.format("Creating issue in project %s at URL %s%n", this.projectKey, url));
//              logger.printf("[JiraTestResultReporter] [DEBUG] Creating issue in project %s at URL %s%n", this.projectKey, url);

                try {
                    DefaultHttpClient httpClient = new DefaultHttpClient();
                
                    Credentials creds = new UsernamePasswordCredentials(this.username, this.password);
                    ((AbstractHttpClient) httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, creds);
                
                    HttpPost postRequest = new HttpPost(url);
//                     String jsonPayLoad = new String("{\"fields\": {\"project\": {\"key\": \"" + this.projectKey + "\"},\"summary\": \"The test " + result.getName() + " failed " + result.getClassName() + ": " + result.getErrorDetails() + "\",\"description\": \"Test class: " + result.getClassName() + " -- " + result.getErrorStackTrace() + "\",\"issuetype\": {\"name\": \"Bug\"}}}");
                    String jsonPayLoad = new String("{\"fields\": {\"project\": {\"key\": \"" + this.projectKey + "\"},\"summary\": \"The test " + result.getName() + " failed " + result.getClassName() + ": " + result.getErrorDetails() + "\",\"description\": \"Test class: " + result.getClassName() + " -- " + result.getErrorStackTrace().replace(this.workspace.toString(), "") + "\",\"issuetype\": {\"name\": \"Bug\"}}}");
                    logger.printf("[JiraTestResultReporter] [DEBUGVERBOSE] JSON payload: ",jsonPayLoad);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");
                    postRequest.setEntity(params);
                    try {
                        postRequest.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials(this.username, this.password), postRequest));
                    } catch (AuthenticationException a) {
                        a.printStackTrace();
                    }

                    HttpResponse response = httpClient.execute(postRequest);
                    debugLog(listener, String.format("statusLine: %s%n", response.getStatusLine()));
//                  logger.printf("[JiraTestResultReporter] [DEBUG] statusLine: %s%n", response.getStatusLine());
                    debugLog(listener, String.format("statusCode: %d%n", response.getStatusLine().getStatusCode()));
//                  logger.printf("[JiraTestResultReporter] [DEBUG] statusCode: %d%n", response.getStatusLine().getStatusCode());
                    if (response.getStatusLine().getStatusCode() != 201) {
                        throw new RuntimeException("[JiraTestResultReporter] [ERROR] Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                    }

//                  BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
//                  String output;
//                  logger.println("Output from Server .... \n");
//                  while ((output = br.readLine()) != null) {
//                      logger.println(output);
//                  }
                    httpClient.getConnectionManager().shutdown();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                logger.printf("[JiraTestResultReporter] [INFO] This issue is old; not reporting.%n");
            }
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
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

