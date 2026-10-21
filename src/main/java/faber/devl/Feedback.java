package faber.devl;

/**
 * How a case's system tells the agent whether a decision was right — a property of a STAGE, not of a case: not at all (assessment, operation), at once with the reason (practice, in
 * learning), or later and only for a wrong decision, quoting the rule in force (audit, in the assessment of a changed world).
 */
public enum Feedback { NONE, PRACTICE, AUDIT }
