package faber.devl.cases.expense;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;

import faber.devl.WorkItem;

/** One expense claim in the pilot's invented domain (Halden & Co.). duplicateOf is ground truth only — never shown to the agent. */
public final class ExpenseClaim implements WorkItem {
    public final String id, employee, category, date, description;
    public final int amount;
    public final boolean receipt;
    public final Map<String, Object> attrs = new LinkedHashMap<>();
    public final String duplicateOf; // id of an earlier identical claim, or null

    public ExpenseClaim(String id, String employee, String category, int amount, boolean receipt,
                        String date, String description, String duplicateOf, Object... kv) {
        this.id = id; this.employee = employee; this.category = category; this.amount = amount;
        this.receipt = receipt; this.date = date; this.description = description; this.duplicateOf = duplicateOf;
        for (int i = 0; i + 1 < kv.length; i += 2) attrs.put((String) kv[i], kv[i + 1]);
    }

    // ---- WorkItem: what the framework needs to know of a claim
    @Override public String id() { return id; }
    @Override public String group() { return category; }
    /** The right outcome in the world (1: the policy of learning, 2: the changed policy), as the label an agent's decision is compared with. */
    @Override public String expected(int world) { return (world == 1 ? ExpensePolicy.v1() : ExpensePolicy.v2()).decide(this).label(); }

    public String summary() {
        return id + ": " + employee + ", " + category + ", " + amount + " EUR, " + date;
    }

    /** What the agent can see via get_expense. */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        o.put("expense_id", id).put("employee", employee).put("category", category)
         .put("amount_eur", amount).put("receipt_attached", receipt).put("date", date)
         .put("description", description);
        for (var e : attrs.entrySet()) o.put(e.getKey(), e.getValue());
        return o;
    }
}
