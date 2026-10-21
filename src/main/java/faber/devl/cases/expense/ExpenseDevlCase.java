package faber.devl.cases.expense;

import java.util.List;

import faber.devl.CaseData;
import faber.devl.CaseWorld;
import faber.devl.DevlCase;
import faber.devl.StageKey;
import faber.environment.Workspace;

/**
 * The expense-approval case: employee expense claims at the invented company Halden & Co., judged by a policy that changes between two versions. Everything about this domain is in this
 * package and in its resource folder (faber/devl/projects/expense-approval): the claims and the queues, the policy and its two versions, the expense system the agent works with, the curated domain
 * model, the rule probes, and the data file that completes the framework's texts. The framework knows none of it.
 */
public final class ExpenseDevlCase implements DevlCase {

    static final String SYSTEM_ID = "expense-system-01";
    private final CaseData data;

    /** Built by faber.devl.Projects from the project folder's data. */
    public ExpenseDevlCase(CaseData data) { this.data = data; }

    @Override public CaseData data() { return data; }

    @Override public List<ExpenseClaim> workload(StageKey key) {
        switch (key) {
            case LEARNING_1: return ExpenseQueues.stage1();
            case LEARNING_2: return ExpenseQueues.stage2();
            case LEARNING_3: return ExpenseQueues.stage3();
            case CLEAN:      return ExpenseQueues.cleanAssessment();
            case CHANGE:     return ExpenseQueues.policyChange();
            case OPERATION:  return ExpenseQueues.operation();
            default: throw new IllegalArgumentException("no workload for " + key);
        }
    }

    @Override public CaseWorld provision(Workspace workspace) {
        workspace.registerType(ExpenseSystemArtifact.manual(), null);
        ExpenseSystemArtifact system = new ExpenseSystemArtifact(SYSTEM_ID, workspace);
        workspace.provision(SYSTEM_ID, ExpenseSystemArtifact.type, system);
        return system;
    }
}
