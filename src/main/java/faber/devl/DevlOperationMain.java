package faber.devl;

import java.util.LinkedHashSet;

import faber.devl.DevlRunner.Options;

/**
 * THE OPERATION TOOL. A fresh instance made from a playbook does the job after the change (a queue in the changed world), with the model at version 2 (--model=in-step) or left behind (--model=stale).
 * The playbook is frozen at work. It writes no snapshot: what a run shows is read from its log and result.
 *
 *   faber.devl.DevlOperationMain --project=<folder> --playbook=FILE [--model=in-step|stale] [--max-cycles=90] [--log-dir=devl-logs] [--watchdog-seconds=90] [--llm-profile=NAME] [--llm-model=ID] [--prompt-overlay=ID]
 */
public final class DevlOperationMain {
    private DevlOperationMain() {}
    static final String USAGE = "DevlOperationMain --project=<folder> --playbook=FILE [--model=in-step|stale] [--max-cycles=N] [--log-dir=DIR] [--watchdog-seconds=N] [--llm-profile=NAME] [--llm-model=ID] [--prompt-overlay=ID]";

    public static Options parse(String[] args) {
        Options o = new Options(); o.phase = "operation";
        for (String a : args) {
            if (DevlRunner.commonArgument(o, a)) continue;
            if (a.startsWith("--playbook=")) o.playbook = a.substring(11);
            else if (a.startsWith("--model=")) o.model = DevlRunner.modelOf(a.substring(8));
            else if (a.equals("--consolidate") || a.startsWith("--only=")) throw new IllegalArgumentException(a.split("=")[0] + " applies to DevlAssessmentMain");
            else if (a.startsWith("--tutor=")) throw new IllegalArgumentException("--tutor applies to DevlLearningMain: there is no tutor at work");
            else throw DevlRunner.unknownArgument("DevlOperationMain", a);
        }
        DevlRunner.requireProject(o, USAGE);
        if (o.playbook == null) throw new IllegalArgumentException("DevlOperationMain needs --playbook=FILE: a fresh instance is made from a playbook\nusage: " + USAGE);
        return o;
    }

    public static void main(String[] args) throws Exception { DevlRunner.launch("DevlOperationMain", USAGE, DevlOperationMain::parse, args); }
}
