package JiraTestResultReporter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.FormValidation;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.json.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JiraReporter extends Notifier {

    public String projectKey;
    public String serverAddress;
    public String username;
    public String password;

    public boolean debugFlag;
    public boolean verboseDebugFlag;
    public boolean createAllFlag;
    public boolean resolveIssueFlag;

    private transient FilePath workspace;

    private static final int JIRA_SUCCESS_CODE_OK = 200;
    private static final int JIRA_SUCCESS_CODE_CREATED = 201;
    private static final int JIRA_SUCCESS_CODE_NOCONTENT = 204;

    private static final String PluginName = new String("[JiraTestResultReporter]");
    private final String pInfo = String.format("%s [INFO]", PluginName);
    private final String pDebug = String.format("%s [DEBUG]", PluginName);
    private final String pVerbose = String.format("%s [DEBUGVERBOSE]", PluginName);
    private final String prefixError = String.format("%s [ERROR]", PluginName);

    // TODO this can be parameterized to take Resolve / Close or any other valid state
    private final String jiraUpdateStatus = "Resolve Issue";

    private String jiraResolveTransitionId = null;

    @DataBoundConstructor
    public JiraReporter(String projectKey,
                        String serverAddress,
                        String username,
                        String password,
                        boolean createAllFlag,
                        boolean debugFlag,
                        boolean verboseDebugFlag,
                        boolean resolveIssueFlag) {

        if (serverAddress.endsWith("/")) {
            this.serverAddress = serverAddress;
        } else {
            this.serverAddress = serverAddress + "/";
        }

        this.projectKey = projectKey;
        this.username = username;
        this.password = password;

        this.verboseDebugFlag = verboseDebugFlag;
        this.debugFlag = verboseDebugFlag || debugFlag;

        this.createAllFlag = createAllFlag;
        this.resolveIssueFlag = resolveIssueFlag;
        //this.checkAllSuccessful = checkAllSuccessful;
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
        logger.printf("%s Examining test results...%n", pInfo);
        debugLog(listener,
                String.format("Build result is %s%n",
                        build.getResult().toString())
        );
        this.workspace = build.getWorkspace();
        debugLog(listener,
                String.format("%s Workspace is %s%n", pInfo, this.workspace.getRemote())
        );
        TestResultAction testResultAction = (TestResultAction) build.getAction(AbstractTestResultAction.class);
        if (testResultAction == null) {
            logger.printf("%s no test results found; nothing to do.%n", pInfo);
        } else {
            List<CaseResult> failedTests = testResultAction.getFailedTests();
            List<CaseResult> passedTests = testResultAction.getPassedTests();

            printResultItems(failedTests, listener);
            createJiraIssue(failedTests, build, listener);

            if (resolveIssueFlag) {
                logger.printf("%s Resolving successful tests...%n", pInfo);
                resolveJiraIssue(passedTests, build, listener);
            }

            logger.printf("%s Done.%n", pInfo);
        }
        return true;
    }

    private void printResultItems(final List<CaseResult> failedTests,
                                  final BuildListener listener) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream out = listener.getLogger();
        for (CaseResult result : failedTests) {
            out.printf("%s projectKey: %s%n", pDebug, this.projectKey);
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

            String affectedFile = result.getErrorStackTrace().replace(this.workspace.getRemote(), "");
            out.printf("%s affectedFile: %s%n", pDebug, affectedFile);
            out.printf("%s ----------------------------%n", pDebug);
        }
    }


    private void resolveJiraIssue(final List<CaseResult> passedTests, AbstractBuild build, final BuildListener listener) {
        /*
        Get all the JIRA for given project which status is OPEN.
        Filter the JIRA issue based on summary and resolve the issue for correct match
         */

        // Create a map of successful tests
        PrintStream logger = listener.getLogger();

        Set<String> setOfSuccessfulTests = new HashSet<String>();
        for (CaseResult test : passedTests) {
            // We get age of all successful tests as 0. Disabling this logic till we get this fixed
            //if (checkAllSuccessful || test.getAge() == 1) {
            setOfSuccessfulTests.add(issueSummary(test.getName()));
            //}
        }

        if (setOfSuccessfulTests.size() == 0) {
            logger.printf("%s Cannot find any new/valid successful tests to be resolved.%n", pInfo);
            return;
        }

        String jiraUpdateUrl = this.serverAddress + "rest/api/2/issue/{0}/transitions";

        DefaultHttpClient httpClient = getDefaultHttpClient();

        try {

            String searchString = URLEncoder.encode(MessageFormat.format("project = {0} AND status = Open", projectKey), "utf-8");
            String jiraSearchUrl = MessageFormat.format("{0}rest/api/2/search?jql={1}",
                    serverAddress,
                    searchString);

            HttpGet httpGet = new HttpGet(jiraSearchUrl);
            httpGet.addHeader(getAuthenticationHeader(httpGet));
            HttpResponse response = httpClient.execute(httpGet);
            validateHttpResponse(response, listener, JIRA_SUCCESS_CODE_OK);

            JsonReader jsonReader = Json.createReader(response.getEntity().getContent());
            JsonObject jsonObject = jsonReader.readObject();
            jsonReader.close();

            JsonArray issues = jsonObject.getJsonArray("issues");
            logger.printf("%s Found %d Open issues for project %s.%n", pInfo, issues.size(), projectKey);

            for (int i = 0; i < issues.size(); i++) {

                JsonObject issue = issues.getJsonObject(i);
                String summary = getSummaryFromIssue(issue);

                if (setOfSuccessfulTests.contains(summary)) {
                    // There is a match. The current issue should be resolved.
                    String issueId = issue.getString("key");
                    String updateUrl = MessageFormat.format(jiraUpdateUrl, issueId);
                    String jsonPayLoad = getTransitionPayload(issueId, build, listener);
                    HttpPost postRequest = new HttpPost(updateUrl);

                    logger.printf("%s Resolving issue %s.%n", pInfo, issueId);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");

                    postRequest.setEntity(params);
                    postRequest.addHeader(getAuthenticationHeader(postRequest));

                    response = httpClient.execute(postRequest);
                    validateHttpResponse(response, listener, JIRA_SUCCESS_CODE_NOCONTENT);

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (AuthenticationException e) {
            e.printStackTrace();
        } finally {
            httpClient.getConnectionManager().shutdown();
        }
    }

    void createJiraIssue(final List<CaseResult> failedTests,
                         final AbstractBuild build,
                         final BuildListener listener) {
        PrintStream logger = listener.getLogger();
        String url = this.serverAddress + "rest/api/2/issue/";

        DefaultHttpClient httpClient = getDefaultHttpClient();

        for (CaseResult result : failedTests) {
            if ((result.getAge() == 1) || (this.createAllFlag)) {
                debugLog(listener,
                        String.format("Creating issue in project %s at URL %s%n",
                                this.projectKey, url)
                );
                try {

                    HttpPost postRequest = new HttpPost(url);
                    String summary = issueSummary(result.getName());

                    String description = "Test class: " + result.getClassName() + "\n\n" +
                            "Jenkins job: " + build.getUrl() + "\n\n" +
                            "{noformat}\n" + result.getErrorDetails() + "\n{noformat}\n\n" +
                            "{noformat}\n" + result.getErrorStackTrace().replace(this.workspace.getRemote(), "") + "\n{noformat}\n\n";
                    JsonObjectBuilder issuetype = Json.createObjectBuilder().add("name", "Bug");
                    JsonObjectBuilder project = Json.createObjectBuilder().add("key", this.projectKey);
                    JsonObjectBuilder fields = Json.createObjectBuilder().add("project", project)
                            .add("summary", summary)
                            .add("description", description)
                            .add("issuetype", issuetype);
                    JsonObjectBuilder payload = Json.createObjectBuilder().add("fields", fields);
                    StringWriter stWriter = new StringWriter();
                    JsonWriter jsonWriter = Json.createWriter(stWriter);
                    jsonWriter.writeObject(payload.build());
                    jsonWriter.close();
                    String jsonPayLoad = stWriter.toString();

                    logger.printf("%s Reporting issue.%n", pInfo);
                    StringEntity params = new StringEntity(jsonPayLoad);
                    params.setContentType("application/json");
                    postRequest.setEntity(params);


                    postRequest.addHeader(getAuthenticationHeader(postRequest));

                    HttpResponse response = httpClient.execute(postRequest);
                    validateHttpResponse(response, listener, JIRA_SUCCESS_CODE_CREATED);

                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (AuthenticationException e) {
                    e.printStackTrace();
                } finally {
                    httpClient.getConnectionManager().shutdown();
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

        public FormValidation doCheckProjectKey(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must provide a project key.");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckServerAddress(@QueryParameter String value) {
            if (value.isEmpty()) {
                return FormValidation.error("You must provide an URL.");
            }

            try {
                new URL(value);
            } catch (final MalformedURLException e) {
                return FormValidation.error("This is not a valid URL.");
            }

            return FormValidation.ok();
        }
    }


    private DefaultHttpClient getDefaultHttpClient() {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        Credentials creds = new UsernamePasswordCredentials(this.username, this.password);
        (httpClient).getCredentialsProvider().setCredentials(AuthScope.ANY, creds);
        return httpClient;
    }

    private String getSummaryFromIssue(JsonObject issue) {
        return issue.getJsonObject("fields").getString("summary");
    }

    private String issueSummary(String methodName) {
        return MessageFormat.format("Test {0} failed", methodName);
    }

    private String getTransitionPayload(String issueID, AbstractBuild build, final BuildListener listener) {
        //"transitions": { "id": "5" }
        if (jiraResolveTransitionId == null) {
            String transitionUrl = MessageFormat.format("{0}rest/api/2/issue/{1}/transitions?expand=transitions.fields",
                    serverAddress,
                    issueID);

            DefaultHttpClient httpClient = getDefaultHttpClient();
            try {

                HttpGet httpGet = new HttpGet(transitionUrl);
                httpGet.addHeader(getAuthenticationHeader(httpGet));
                HttpResponse response = httpClient.execute(httpGet);

                JsonReader jsonReader = Json.createReader(response.getEntity().getContent());
                JsonObject jsonObject = jsonReader.readObject();
                jsonReader.close();

                JsonArray jsonArray = jsonObject.getJsonArray("transitions");
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject transitionEntity = jsonArray.getJsonObject(i);
                    if (jiraUpdateStatus.equals(transitionEntity.getString("name"))) {
                        jiraResolveTransitionId = transitionEntity.getString("id");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (AuthenticationException e) {
                e.printStackTrace();
            } finally {
                httpClient.getConnectionManager().shutdown();
            }

            if (jiraResolveTransitionId == null) {
                debugLog(listener, MessageFormat.format("Couldn't get the id for jira status -'{0}'", jiraUpdateStatus));
            }
        }

        String comment = MessageFormat.format("Test case fixed in build# {0}.", build.getNumber());

        JsonObjectBuilder payload = Json.createObjectBuilder()
                .add("transition", Json.createObjectBuilder().add("id", jiraResolveTransitionId))
                .add("update", Json.createObjectBuilder().
                        add("comment", Json.createArrayBuilder().
                                add(Json.createObjectBuilder().
                                        add("add", Json.createObjectBuilder().add("body", comment)))));

        StringWriter stWriter = new StringWriter();
        JsonWriter jsonWriter = Json.createWriter(stWriter);
        jsonWriter.writeObject(payload.build());
        jsonWriter.close();
        return stWriter.toString();
    }

    private void validateHttpResponse(HttpResponse response, BuildListener listener, int HTTP_CODE) {
        debugLog(listener,
                String.format("statusLine: %s%n",
                        response.getStatusLine())
        );
        debugLog(listener,
                String.format("statusCode: %d%n",
                        response.getStatusLine().getStatusCode())
        );
        if (response.getStatusLine().getStatusCode() != HTTP_CODE) {
            throw new RuntimeException(this.prefixError + " Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
        }
    }

    private org.apache.http.Header getAuthenticationHeader(HttpRequest httpRequest) throws AuthenticationException {
        return new BasicScheme().authenticate(
                new UsernamePasswordCredentials(this.username, this.password),
                httpRequest,
                new BasicHttpContext());
    }

    void debugLog(final BuildListener listener, final String message) {
        if (!this.debugFlag) {
            return;
        }
        PrintStream logger = listener.getLogger();
        logger.printf("%s %s%n", pDebug, message);
    }

}

