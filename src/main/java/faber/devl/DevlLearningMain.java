package faber.devl;

import java.util.LinkedHashSet;

import faber.devl.DevlRunner.Options;

/**
 * THE LEARNING TOOL. Prepares an agent for a project's job, in three stages (the tutor fading, the practice feedback going), and writes the playbook at the end of each debrief: devl-playbook-<stamp>.json,
 * the release form of what the agent wrote through its playbook artifact (playbook-01), plus the run's log and result.
 *
 *   faber.devl.DevlLearningMain --project=<folder> [--tutor=scripted|human] [--max-cycles=90] [--log-dir=devl-logs] [--watchdog-seconds=90] [--llm-model=ID]
 *
 * --llm-model  the Anthropic model of the agent (default: the one the architecture asks for; any model id the API knows, e.g. claude-haiku-5-5); recorded in the log and in every snapshot. -Dfaber.llm.model=ID does the same.
 * --project   the project's folder in resources/faber/devl/projects (e.g. expense-approval): its files, with standard names, are what sets up the environment (see its README.md)
 * Learning starts from nothing and has no playbook to give: the tool for a playbook that exists is DevlAssessmentMain (and DevlOperationMain).
 */
public final class DevlLearningMain {
    private DevlLearningMain() {}
    static final String USAGE = "DevlLearningMain --project=<folder> [--tutor=scripted|human] [--max-cycles=N] [--log-dir=DIR] [--watchdog-seconds=N] [--llm-model=ID]";

    public static Options parse(String[] args) {
        Options o = new Options(); o.phase = "learning";
        for (String a : args) {
            if (DevlRunner.commonArgument(o, a)) continue;
            if (a.startsWith("--tutor=")) o.tutor = DevlRunner.tutorOf(a.substring(8));
            else if (a.startsWith("--playbook=")) throw new IllegalArgumentException("--playbook is for DevlAssessmentMain or DevlOperationMain: learning starts from nothing and writes the playbook");
            else if (a.startsWith("--model=") || a.equals("--consolidate") || a.startsWith("--only=")) throw new IllegalArgumentException(a.split("=")[0] + " applies to DevlAssessmentMain (and --model to DevlOperationMain): during learning the model stands at version 1, with the world");
            else throw DevlRunner.unknownArgument("DevlLearningMain", a);
        }
        DevlRunner.requireProject(o, USAGE);
        return o;
    }

    public static void main(String[] args) throws Exception { DevlRunner.launch("DevlLearningMain", USAGE, DevlLearningMain::parse, args); }
}
