# SCXML_EVENTB
SCXML to iUML-B/Event-B translation

Provides a translation into iUML-B state-machines which are then used to generate Event-B.
The translation supports the notion of refinement levels which must be annotated on the SCXML statechart hierarchy.
The translation supports 'Run to Completion' semantics of SCXML using non-determinism to allow for future details, particularly the guard strengthening (hence weakening of completion) that is implied by adding substates at the source of a transition.
