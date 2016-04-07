/**
 Copyright 2015 Andrei Tuicu

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.jenkinsci.plugins.JiraTestResultReporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.JiraTestResultReporter.config.AbstractFields;
import org.jenkinsci.plugins.JiraTestResultReporter.config.FieldConfigsJsonAdapter;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by tuicu.
 * Map from job name to configurations. A new publisher is created every time save/apply is clicked in the job config
 * page, but the new configurations don't apply to previous build, so we need this map in order for all the publishers
 * to be able to access the last configuration. Implemented as a singleton pattern. The map gets serialized every time
 * a new configuration is added
 */
public class JobConfigMapping {
    private static class JobConfigEntry implements Serializable {
        public static final long serialVersionUID = 6509568994710878311L; //backwards compatibility
        private String projectKey;
        private Long issueType;
        private List<AbstractFields> configs;
        boolean autoRaiseIssue;
        private transient Pattern issueKeyPattern;

        /**
         * Constructor
         * @param projectKey
         * @param issueType
         * @param configs list with the configured fields
         */
        public JobConfigEntry(String projectKey, Long issueType, List<AbstractFields> configs, boolean autoRaiseIssue) {
            this.projectKey = projectKey;
            this.issueType = issueType;
            this.configs = configs;
            this.issueKeyPattern = Pattern.compile(projectKey + "-\\d+");
            this.autoRaiseIssue = autoRaiseIssue;
        }

        /**
         * Getter for the issue type
         * @return
         */
        public Long getIssueType() {
            return issueType;
        }

        /**
         * Getter for the project key
         * @return
         */
        public String getProjectKey() {
            return projectKey;
        }

        /**
         * Getter for the configured fields
         * @return
         */
        public List<AbstractFields> getConfigs() {
            return configs;
        }

        public boolean getAutoRaiseIssue() { return autoRaiseIssue; }

        /**
         * Getter for the issue key pattern
         * @return
         */
        public Pattern getIssueKeyPattern() { return issueKeyPattern; }

        /**
         * Method for resolving transient objects after deserialization. Called by the JVM.
         * See Java documentation for more details.
         * @return this object
         */
        private Object readResolve() {
            this.issueKeyPattern = Pattern.compile(projectKey + "-\\d+");
            return this;
        }
    }
    private static JobConfigMapping instance = new JobConfigMapping();
    private static final String CONFIGS_FILE = "JiraIssueJobConfigs";

    /**
     * Getter for the singleton instance
     * @return
     */
    public static JobConfigMapping getInstance() {
        return instance;
    }
    private HashMap<String, JobConfigEntry> configMap;

    /**
     * Constructor. Will deserialize the existing map, or will create an empty new one
     */
    private JobConfigMapping(){
        configMap = new HashMap<String, JobConfigEntry>();

        for(AbstractProject project : Jenkins.getInstance().getItems(AbstractProject.class)) {
            JobConfigEntry entry = load(project);
            if (entry != null) {
                configMap.put(project.getFullName(), entry);
            }
        }
    }

    /**
     * Constructs the path for the config file
     * @param project
     * @return
     */
    private String getPathToFile(AbstractProject project) {
        return project.getRootDir().toPath().resolve(CONFIGS_FILE).toString();
    }

    private String getPathToJsonFile(AbstractProject project) {
        return project.getRootDir().toPath().resolve(CONFIGS_FILE).toString() + ".json";
    }

    /**
     * Looks for configurations from a previous version of the plugin and tries to load them
     * and save them in the new format
     * @param project
     * @return the loaded JobConfigEntry, or null if there was no file, or it could not be loaded
     */
    private JobConfigEntry loadBackwardsCompatible(AbstractProject project) {
        try {
            FileInputStream fileIn = new FileInputStream(getPathToFile(project));
            ObjectInputStream in = new ObjectInputStream(fileIn);
            JobConfigEntry entry = (JobConfigEntry) in.readObject();
            in.close();
            fileIn.close();
            JiraUtils.log("Found and successfully loaded configs from a previous version for job: "
                    + project.getFullName());
            //make sure we have the configurations from previous versions in the new format
            save(project, entry);
            return entry;
        } catch (FileNotFoundException e) {
           //Nothing to do
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Found configs from a previous version, but was unable to load them for project "
                    + project.getFullName(), e);
        }
        return null;
    }

    /**
     * Loads the JobConfigEntry from the file associated with the project
     * @param project
     * @return the loaded JobConfigEntry, or null if there was no file, or it could not be loaded
     */
    private JobConfigEntry load(AbstractProject project) {
        JobConfigEntry entry = null;
        try{
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(AbstractFields.class, new FieldConfigsJsonAdapter())
                    .create();
            FileInputStream fileIn = new FileInputStream(getPathToJsonFile(project));
            JsonReader reader = new JsonReader(new InputStreamReader(fileIn, "UTF-8"));

            entry = gson.fromJson(reader, JobConfigEntry.class);
            reader.close();
            fileIn.close();

            return (JobConfigEntry) entry.readResolve();
        } catch (FileNotFoundException e) {
            entry = loadBackwardsCompatible(project);
            if(entry == null) {
                JiraUtils.log("No configs found for project " + project.getFullName());
            }
        } catch (Exception e) {
            JiraUtils.logError("ERROR: Could not load configs for project " + project.getFullName(), e);
        }
        return entry;
    }

    /**
     * Method for saving the map, called every time the map changes
     */
    private void save(AbstractProject project, JobConfigEntry entry) {
        try {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(AbstractFields.class, new FieldConfigsJsonAdapter())
                    .create();
            FileOutputStream fileOut = new FileOutputStream(getPathToJsonFile(project));
            JsonWriter writer = new JsonWriter(new OutputStreamWriter(fileOut, "UTF-8"));
            writer.setIndent("  ");
            gson.toJson(entry, JobConfigEntry.class, writer);
            writer.close();
            fileOut.close();
        }
        catch (Exception e) {
            JiraUtils.logError("ERROR: Could not save project map", e);
        }
    }

    /**
     * Method for setting the last configuration made for a project
     * @param project
     * @param projectKey
     * @param issueType
     * @param configs
     */
    public synchronized void saveConfig(AbstractProject project,
                                        String projectKey,
                                        Long issueType,
                                        List<AbstractFields> configs,
                                        boolean autoRaiseIssue) {
        JobConfigEntry entry = new JobConfigEntry(projectKey, issueType, configs, autoRaiseIssue);
        configMap.put(project.getFullName(), entry);
        save(project, entry);
    }

    private JobConfigEntry getJobConfigEntry(AbstractProject project) {
        if(!configMap.containsKey(project.getFullName())) {
            JobConfigEntry entry = load(project);
            if(entry != null) {
                configMap.put(project.getFullName(), entry);
            }
        }
        return configMap.get(project.getFullName());
    }

    /**
     * Getter for the last configured fields
     * @param project
     * @return
     */
    public List<AbstractFields> getConfig(AbstractProject project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getConfigs() : null;
    }

    /**
     * Getter for the last configured issue type
     * @param project
     * @return
     */
    public Long getIssueType(AbstractProject project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getIssueType() : null;
    }

    /**
     * Getter for the last configured project key
     * @param project
     * @return
     */
    public String getProjectKey(AbstractProject project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getProjectKey() : null;
    }

    public boolean getAutoRaiseIssue(AbstractProject project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getAutoRaiseIssue() : false;
    }

    /**
     * Getter for the issue key pattern, used to validate user input
     * @param project
     * @return
     */
    public Pattern getIssueKeyPattern(AbstractProject project) {
        JobConfigEntry entry = getJobConfigEntry(project);
        return entry != null ? entry.getIssueKeyPattern() : null;
    }
}
