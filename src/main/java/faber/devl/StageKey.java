package faber.devl;

/**
 * The six workloads a case provides, one per stage of the process; the framework decides how each is run (the phase, the world it is judged in, the feedback, the tutor, the message), the case
 * decides what is in it.
 *
 *   LEARNING_1  a guided introduction: a few items, one that needs the tutor, one that needs the first rule
 *   LEARNING_2  every kind of item the job brings, once, and the rule that spans items (a duplicate)
 *   LEARNING_3  retention without feedback: one item per rule family, at the boundary that tells the rules apart
 *   CLEAN       held-out items under the world of learning, with no change, no boundary values, no ambiguity: baseline competence. Decided the same in both worlds
 *   CHANGE      items under the CHANGED world: some whose outcome the change moved, and controls that it did not
 *   OPERATION   new items judged in the changed world, some of them moved by the change
 */
public enum StageKey { LEARNING_1, LEARNING_2, LEARNING_3, CLEAN, CHANGE, OPERATION }
