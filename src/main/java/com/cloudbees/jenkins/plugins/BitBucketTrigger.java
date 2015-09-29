package com.cloudbees.jenkins.plugins;

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import hudson.util.StreamTaskListener;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BitBucketTrigger extends Trigger<Job<?, ?>> {

    @DataBoundConstructor
    public BitBucketTrigger() {
    }

    public void onPost(String triggeredByUser) {
        onPost(triggeredByUser, null);
    }

    /**
     * Called when a POST is made.
     */
    public void onPost(String triggeredByUser, Map<String, String> envVars) {
        final String pushBy = triggeredByUser;
        final Map<String, String> environmentVars = envVars;

        getDescriptor().queue.execute(new Runnable() {
            private boolean runPolling() {
                try {
                    StreamTaskListener listener = new StreamTaskListener(getLogFile());
                    try {
                        PrintStream logger = listener.getLogger();
                        long start = System.currentTimeMillis();

                        logger.println("Started on " + DateFormat.getDateTimeInstance().format(new Date()));
                        boolean result = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job).poll(listener).hasChanges();
//                        boolean result = true;

                        logger.println("Done. Took " + Util.getTimeSpanString(System.currentTimeMillis() - start));

                        if (result)
                            logger.println("Changes found");
                        else
                            logger.println("No changes");
                        return result;
                    } catch (Error e) {
                        e.printStackTrace(listener.error("Failed to record SCM polling"));
                        LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                        throw e;
                    } catch (RuntimeException e) {
                        e.printStackTrace(listener.error("Failed to record SCM polling"));
                        LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                        throw e;
                    } finally {
                        listener.close();
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to record SCM polling", e);
                }
                return false;
            }

            public void run() {
                if (runPolling()) {
                    String name = " #" + job.getNextBuildNumber();
                    BitBucketPushCause cause;

                    try {
                        cause = new BitBucketPushCause(getLogFile(), pushBy);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to parse the polling log", e);
                        cause = new BitBucketPushCause(pushBy);
                    }

                    LOGGER.info("Putting vars");

                    Iterator<String> keys = environmentVars.keySet().iterator();
                    String key = "";

                    ParametersDefinitionProperty parametersDefinitionProperty =
                            job.getProperty(ParametersDefinitionProperty.class);

                    if (parametersDefinitionProperty == null) {
                        LOGGER.info("New property");
                        try {
                            parametersDefinitionProperty =
                                    new ParametersDefinitionProperty(new ArrayList<ParameterDefinition>());

                            job.addProperty(parametersDefinitionProperty);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage(), e);
                        }
                    }

                    while (keys.hasNext()) {
                        key = keys.next();
                        String value = environmentVars.get(key);

                        LOGGER.info(key + "=" + value);

                        int offset = -1;

                        for (int i = 0; i < parametersDefinitionProperty.getParameterDefinitions().size(); i++) {
                            if (parametersDefinitionProperty.getParameterDefinitions().get(i).getName().equals(key)) {
                                offset = i;

                                break;
                            }
                        }

                        if (offset == -1)
                            parametersDefinitionProperty.getParameterDefinitions()
                                    .add(new StringParameterDefinition(key, value));
                        else
                            parametersDefinitionProperty.getParameterDefinitions()
                                    .set(offset, new StringParameterDefinition(key, value));
                    }

                    LOGGER.info("All vars");

                    ParameterizedJobMixIn pJob = new ParameterizedJobMixIn() {
                        @Override
                        protected Job asJob() {
                            return job;
                        }
                    };

                    LOGGER.info("Starting");

                    if (pJob.scheduleBuild(cause)) {
                        LOGGER.info("SCM changes detected in " + job.getName() + ". Triggering " + name);
                    } else {
                        LOGGER.info("SCM changes detected in " + job.getName() + ". Job is already in the queue");
                    }
                }
            }

        });
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new BitBucketWebHookPollingAction());
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(),"bitbucket-polling.log");
    }

    /**
     * Check if "bitbucket-polling.log" already exists to initialize it
     */
    public boolean IsLogFileInitialized() {
        File file = new File(job.getRootDir(),"bitbucket-polling.log");
        return file.exists();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Action object for {@link Project}. Used to display the polling log.
     */
    public final class BitBucketWebHookPollingAction implements Action {
        public Job<?,?> getOwner() {
            return job;
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return "BitBucket Hook Log";
        }

        public String getUrlName() {
            return "BitBucketPollLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        /**
         * Writes the annotated log to the given output.
         */
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<BitBucketWebHookPollingAction>(getLogFile(), Charset.defaultCharset(),true,this).writeHtmlTo(0,out.asWriter());
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(Hudson.MasterComputer.threadPoolForRemoting);

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof Job && SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item) != null
                    && item instanceof ParameterizedJobMixIn.ParameterizedJob;
        }

        @Override
        public String getDisplayName() {
            return "Build when a change is pushed to BitBucket";
        }
    }

    private static final Logger LOGGER = Logger.getLogger(BitBucketTrigger.class.getName());
}
