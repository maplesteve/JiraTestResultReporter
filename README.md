JiraTestResultReporter
======================

**Hello! Thank you for your interest in contributing to this plugin! If you were looking for the previous versions, please checkout the 1.x branch**

## WARNING: 2.x verions of this plugin are not compatible with the previous 1.x version. What this means is that your configurations related to this plugin will not be imported from previous versions when you do an upgrade.

### What is does
This plugin allows you to create and/or link issues from Jira to failed tests in Jenkins. The creation/linking is done directly in the Jenkins interface. For the creation of the issues you can supply a template for what is going to be added in most of the issue's fields. 

### Global Configuration
Before doing anything else, the global configurations must be done.
In order to do these go to **Manage Jenkins -> Configure System -> JiraTestResultReporter** and enter here the JIRA server url the username and password. It is highly recommended that you click the Validate Settings button every time you make any changes here.
Also from here you can configure the global templates for Summary and Description, by clicking on the Advanced button. These templates will be used to create issues if they are not overridden in the job configuration.

![](img/global-config.png)

### Job Configuration
The first thing we need to do here is enabling the plugin:
 * **Freestyle projects** and **Multi-configuration projects**

     First, JUnit test reports need to be enabled by going to **Add post-build action -> Publish JUnit test report**. Then check the box next to **JiraTestResultReporter** in the **Additional test report features section**.

 * **Maven Project**

     **Add post-build action -> Additional test report features** -> check the box next to **JiraTestResultReporter**.

**Configuration:**

Insert the **Project key** in the respective field. Again, highly recommended to push the Validate Settings.

After setting the project key the **Issue type** select will be populated with the available issue types for that specific project.

If you check the **Auto raise issue** check box, this plugin will create and link issues for all the failing tests in new builds that don't already have linked issues.
 
![](img/job-config1.png)

Only after configuring the fields above, if you want you can override the **Summary** and **Description** values by clicking the **Advanced** button. 
If you want, here you can configure all available fields for that specific issue type. Due to Jenkins interface development limitations, you have to search for the desired field from the four available types of fields, after clicking the Add Field Configuration.

**Important: Do not leave empty values for fields, if you didn't find the desired field in the current chosen option, delete it before trying the next one.**

Finally, I cannot say that this is recommended ( although it is (smile) ), **read the help tag for the Validate Fields** and if the warning there is not a problem for you click the button.

![](img/job-config2.png)
![](img/job-config3.png)

### Usage
After building the project, go to the test results page. Next to the test cases you will see a **blue plus button**, next to a **No issue** message. If you want to **create an issue**, or **link an existing one**, click the blue plus button and choose the desired option. For **unlinking** an issue, click the **red x button**.

**When creating, linking and unlinking issues, you it is recommended that wait for the page to reload, before doing something else for another test.** Errors will be shown inline, if any.

![](img/test-interface.png)

Finally, your issues are created and you can see them by clicking the links directly from the Jenkins interface.


![](img/jira-issue.png)


### Variables
For text fields in the Job Configuration and Global Confinguration (Summary and Description only) you can use variables that will expand to the appropriate value when the issue is created in JIRA. You can use all the environment variables defined by Jenkins (see [link](https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project)). Additionaly, this plugin can expand a set of predefined variables that expose information about the test.

![](img/variables.png)
