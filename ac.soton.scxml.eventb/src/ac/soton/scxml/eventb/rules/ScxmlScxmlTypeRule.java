/*******************************************************************************
 *  Copyright (c) 2017-2019 University of Southampton.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *   
 *  Contributors:
 *  University of Southampton - Initial implementation
 *******************************************************************************/
package ac.soton.scxml.eventb.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import ac.soton.scxml.ScxmlRaiseType;
import ac.soton.scxml.ScxmlScxmlType;
import ac.soton.scxml.ScxmlTransitionType;
import org.eventb.emf.core.EventBNamedCommentedComponentElement;
import org.eventb.emf.core.Project;
import org.eventb.emf.core.context.Axiom;
import org.eventb.emf.core.context.CarrierSet;
import org.eventb.emf.core.context.Constant;
import org.eventb.emf.core.context.Context;
import org.eventb.emf.core.machine.Action;
import org.eventb.emf.core.machine.Event;
import org.eventb.emf.core.machine.Guard;
import org.eventb.emf.core.machine.Invariant;
import org.eventb.emf.core.machine.Machine;
import org.eventb.emf.core.machine.Parameter;
import org.eventb.emf.core.machine.Variable;

import ac.soton.emf.translator.TranslationDescriptor;
import ac.soton.emf.translator.configuration.IRule;
import ac.soton.eventb.emf.core.extension.navigator.refiner.AbstractElementRefiner;
import ac.soton.eventb.emf.core.extension.navigator.refiner.ElementRefinerRegistry;
import ac.soton.eventb.statemachines.Statemachine;
import ac.soton.scxml.eventb.strings.Strings;
import ac.soton.scxml.eventb.utils.IumlbScxmlAdapter;
import ac.soton.scxml.eventb.utils.Make;
import ac.soton.scxml.eventb.utils.Utils;

public class ScxmlScxmlTypeRule extends AbstractSCXMLImporterRule implements IRule {
			
	@Override
	public List<TranslationDescriptor> fire(EObject sourceElement, List<TranslationDescriptor> generatedElements) throws Exception {
		
		// Reset the storage
		storage.reset();

		ScxmlScxmlType scxml = (ScxmlScxmlType)sourceElement;
		
		//find triggers and set up data for them in the storage
		//(triggers are only represented in the scxml model by string attributes of transitions 
		// so much of their details are implicit - hence the need to calculate this before the translation starts)
		Map<String, Trigger> triggers =  findTriggers(scxml);
		storage.stash("triggers", triggers);
		
		int depth = getRefinementDepth(sourceElement);
		List<TranslationDescriptor> ret = new ArrayList<TranslationDescriptor>();
		String fileName = scxml.eResource().getURI().toPlatformString(true);

		String statechartName = scxml.getName()+"_sm";
		Project project = Utils.findProject(sourceElement);
		
		//get the basis Context
		Context context = getBasisContext();
		ret.add(Make.descriptor(project, components, context ,1));
		
		//get the basis Machine
		Machine machine = getBasisMachine(context);
		ret.add(Make.descriptor(project, components, machine ,1));
		
		String allTriggers = null;
		String allExternals = null;
		String allInternals = null;
		//create the refinement chain of machines
		for (int i=0; i<=depth; i++){
			
			// make a new machine by refining the previous level
			machine = (Machine) refine (scxml, machine, Utils.getMachineName(scxml,i), Strings.generatedFromFileComment(fileName));
			//set all events as extended rather than copied and add extra guards to 'future' events
			for (Event e : machine.getEvents()){
				if (!e.getRefines().isEmpty()){
					e.setExtended(true);
					e.getParameters().clear();
					e.getGuards().clear();
					e.getActions().clear();
					//add guards for next level
					if (Strings.futureExternalTriggersEventName.equals(e.getName())){
						Guard e1_g1 = (Guard) Make.guard(Strings.e1_g1_Name+i, false, 
								Strings.generalRaisedExternalTriggerGuardPredicate(i), //Strings.e1_g1_Predicate+i, 
								Strings.e1_g1_Comment);
						e.getGuards().add(e1_g1);
					//prevent future triggered transitions from triggering on triggers that are now defined	
					}else if (Strings.consumeTriggerEventName.equals(e.getName())) {
						String newTriggers = getTriggersForRefinement(triggers, i);
						if (newTriggers!=null) {
							Guard e3_g2 = (Guard) Make.guard(Strings.e3_g2_Name+i,  false,
									Strings.e3_g2_Predicate(newTriggers), 
									Strings.e3_g2_Comment);
							e.getGuards().add(e3_g2);
						}
					}
				}else{
					System.out.println("Non refined event: "+e.getName());
				}
			}

			//create the descriptor to put the machine in the project
			ret.add(Make.descriptor(project, components, machine ,1));
			// make a new context by refining the previous level
			context = (Context) refine (scxml, context, Utils.getContextName(scxml,i), Strings.generatedFromFileComment(fileName));
			//create the descriptor to put the machine in the project
			ret.add(Make.descriptor(project, components, context ,1));
			
			// all levels see the corresponding context which extends the basis context
			machine.getSeesNames().add(context.getName()); //Strings.basisContextName);

			//if this is the first level add a state-machine (subsequent levels will copy it by refinement)
			if (i==0){
				Statemachine statemachine = (Statemachine) Make.statemachine(statechartName, tkind, "");
				machine.getExtensions().add(statemachine);
			}
			
			//add any invariants that are for this refinement level
			List<IumlbScxmlAdapter> invs = new IumlbScxmlAdapter(scxml).getinvariants();
			for (IumlbScxmlAdapter inv : invs){
				int refLevel = inv.getBasicRefinementLevel();
				if (refLevel==-1) refLevel = 0;
				if (refLevel==i){
					String name = (String)inv.getAnyAttributeValue("name");
					String derived = (String)inv.getAnyAttributeValue("derived");
					String trigger = (String)inv.getAnyAttributeValue("trigger");
					String predicate = (String)inv.getAnyAttributeValue("predicate");
					String comment = (String)inv.getAnyAttributeValue("comment");
					Invariant invariant =  (Invariant) Make.invariant(name,Boolean.parseBoolean(derived),Strings.SCXMLSTATE_INV_PREDICATE(trigger, predicate),comment); 
					machine.getInvariants().add(invariant);
				}
			}
			
			//define triggers in contexts and 
			// add any external trigger raising events needed at this level
			Constant futureintenaltriggers = (Constant) Make.constant(Strings.internalTriggersName(i), "");
			context.getConstants().add(futureintenaltriggers);
			Constant futureexternaltriggers = (Constant) Make.constant(Strings.externalTriggersName(i), "");
			context.getConstants().add(futureexternaltriggers);
			String internals = null;
			String externals = null;
			for (String tname : triggers.keySet()){
				if (tname == "null") continue;
				Trigger t = triggers.get(tname);
				if (t.getRefinementLevel()==i){
					Constant trigConst = (Constant) Make.constant(t.getName(), "trigger");
					context.getConstants().add(trigConst);
					if (t.isExternal()){
						externals=externals==null? t.getName(): externals+"},{"+t.getName();
						Event e = (Event) Make.event("ExternalTriggerEvent_"+t.getName());
						e.getRefinesNames().add(Strings.futureExternalTriggersEventName);
						e.setExtended(true);
						Guard g = (Guard) Make.guard(
								Strings.specificRaisedExternalTriggerGuardName, false, 
								Strings.specificRaisedExternalTriggerGuardPredicate(t.getName()), 
								Strings.specificRaisedExternalTriggerGuardComment);
						e.getGuards().add(g);
						machine.getEvents().add(e);
					}else{
						internals=internals==null? t.getName(): internals+"},{"+t.getName();
					}
				}
			}
			Axiom eax = (Axiom) Make.axiom(Strings.externalTriggerAxiomName(i), Strings.externalTriggerDefinitionAxiomPredicate(i, externals), "");
			context.getAxioms().add(eax);
			Axiom iax = (Axiom) Make.axiom(Strings.internalTriggerAxiomName(i), Strings.internalTriggerDefinitionAxiomPredicate(i, internals), "");
			context.getAxioms().add(iax);
			if (externals!=null){
				allExternals = allExternals==null? externals : allExternals+"},{"+externals;
				allTriggers = allTriggers==null? externals : allTriggers+"},{"+externals;
			}
			if (internals!=null){
				allInternals = allInternals==null? internals : allInternals+"},{"+internals;
				allTriggers = allTriggers==null? internals : allTriggers+"},{"+internals;
			}
			if (i>0 && (externals!=null || internals!=null)) {
				//This axiom tells ProB how to setup the complete set of all internal and external triggers
				Axiom prob_setup = (Axiom) Make.axiom(Strings.probSetupTriggersAxiomName(i), Strings.probSetupTriggersAxiomPredicate(i, allTriggers), "help ProB");
				context.getAxioms().add(prob_setup);
			}
				
		}
		return ret;
	}

	/**
	 * returns a comma separated list of triggers raised at refinement level i
	 * @param triggers
	 * @param i
	 * @return
	 */
	private String getTriggersForRefinement(Map<String, Trigger> triggers, int i) {
		String ret = null;
		for (String tname : triggers.keySet()){
			if (tname == "null") continue;
			Trigger t = triggers.get(tname);
			if (t.getRefinementLevel()==i){
				ret = ret==null? tname : ret+","+tname;
			}
		}
		return ret;
	}

	/**
	 * @param component
	 * @return
	 */
	private EventBNamedCommentedComponentElement refine(EObject sourceElement, EventBNamedCommentedComponentElement component, String refineName, String comment) {
		URI baseUri = EcoreUtil.getURI(sourceElement).trimFragment().trimSegments(1);
		String fileExtension = 	component instanceof Machine? "bum" :
								component instanceof Context? "buc" :
															"xmb" ;
		URI abstractUri = baseUri.appendSegment(component.getName()).appendFileExtension(fileExtension);
		abstractUri = abstractUri.appendFragment(component.getReference());
		
		//URI concreteResourceUri = baseUri.appendSegment(refineName).appendFileExtension(fileExtension);

		AbstractElementRefiner refiner = ElementRefinerRegistry.getRegistry().getRefiner(component);
		Map<EObject,EObject> copy = refiner.refineWithComponentName(abstractUri,  component, refineName);
		EventBNamedCommentedComponentElement refinement = (EventBNamedCommentedComponentElement) copy.get(component);
		//refinement.setName(refineName);
		refinement.setComment(comment);		
		return refinement;
	}
	

	/**
	 * calculate trigger data for the Scxml model
	 * 
	 * @param scxml
	 * @throws Exception 
	 */
	private Map<String, Trigger> findTriggers(ScxmlScxmlType scxml) throws Exception {
		Map<String, Trigger> triggers = new HashMap<String,Trigger>();
		//iterate over the entire model looking for transitions
		//(trigger names are defined by string attribute 'event' of a transition and
		// also by a transition's collection of ScxmlRaiseType elements).
		TreeIterator<EObject> it = scxml.eAllContents();
		while (it.hasNext()){
			EObject next = it.next(); 
			if (next instanceof ScxmlTransitionType){ 
				ScxmlTransitionType scxmlTransition = (ScxmlTransitionType)next;
				
				//record any trigger that triggers this transition (including the null trigger)
				String triggerName = scxmlTransition.getEvent();
				if (triggerName==null || triggerName.trim().length()==0) triggerName = "null"; //make sure it is not an empty identifier
				Trigger trigger = triggers.get(triggerName);
				if (trigger == null){
					trigger = new Trigger(triggerName);
					triggers.put(triggerName, trigger);
				}		
				trigger.addTriggeredTransition(scxmlTransition);
					
				//record any triggers raised by this transition
				EList<ScxmlRaiseType> raises = scxmlTransition.getRaise();
				for (ScxmlRaiseType raise : raises){
					String raisedTriggerName = raise.getEvent();
					Trigger raisedTrigger = triggers.get(raisedTriggerName);
					if (raisedTrigger == null){
						raisedTrigger = new Trigger(raisedTriggerName);
						triggers.put(raisedTriggerName, raisedTrigger);
					}		
					raisedTrigger.addRaisedByTransition(raise);
				}
			}
		}	
		return triggers;
	}
	
	
	/*********************************************************
	 * The remainder generates the basis context and machine
	 *********************************************************/
	
	/**
	 * @return
	 */
	private Context getBasisContext() {
		Context basis = (Context) Make.context(Strings.basisContextName, "(generated for SCXML)");
		CarrierSet triggerSet = (CarrierSet) Make.set(Strings.triggerSetName, "all possible triggers");
		basis.getSets().add(triggerSet);
		Constant const1 = (Constant) Make.constant(Strings.internalTriggersName, "all possible internal triggers");
		basis.getConstants().add(const1);
		Constant const2 = (Constant) Make.constant(Strings.externalTriggersName, "all possible external triggers");
		basis.getConstants().add(const2);
		Axiom ax = (Axiom) Make.axiom(Strings.triggerPartitionAxiomName, Strings.triggerPartitionAxiomPredicate, "");
		basis.getAxioms().add(ax);
		return basis;
	}
	
	/**
	 * @return
	 */
	private Machine getBasisMachine(Context basisContext) {
		Machine basis = (Machine) Make.machine(Strings.basisMachineName, "(generated for SCXML)");
		basis.getSeesNames().add(Strings.basisContextName);
		
		Variable v1 = (Variable) Make.variable(Strings.internalQueueName, "internal trigger queue");
		basis.getVariables().add(v1);
		Variable v2 = (Variable) Make.variable(Strings.externalQueueName, "external trigger queue");
		basis.getVariables().add(v2);
		Variable v3 = (Variable) Make.variable(Strings.completionFlagName, "run to completion flag");
		basis.getVariables().add(v3);
		Variable v4 = (Variable) Make.variable(Strings.dequeuedTriggerSetName, "dequeued trigger for this run");
		basis.getVariables().add(v4);

		Invariant i1 = (Invariant) Make.invariant(Strings.internalQueueTypeName, Strings.internalQueueTypePredicate, "internal trigger queue");
		basis.getInvariants().add(i1);
		Invariant i2 = (Invariant) Make.invariant(Strings.externalQueueTypeName, Strings.externalQueueTypePredicate, "external trigger queue");
		basis.getInvariants().add(i2);
		Invariant i3 = (Invariant) Make.invariant(Strings.queueDisjunctionName, Strings.queueDisjunctionPredicate, "queues are disjoint");
		basis.getInvariants().add(i3);
		Invariant i4 = (Invariant) Make.invariant(Strings.completionFlagTypeName, Strings.completionFlagTypePredicate, "completion flag");
		basis.getInvariants().add(i4);
		Invariant i5 = (Invariant) Make.invariant(Strings.dequeuedTriggerSetTypeName, Strings.dequeuedTriggerSetTypePredicate, "dequeued triggers");
		basis.getInvariants().add(i5);
		Invariant i6 = (Invariant) Make.invariant(Strings.oneDequeuedTriggerInvariantName, Strings.oneDequeuedTriggerInvariantPredicate, "at most one dequeued trigger");
		basis.getInvariants().add(i6);
		
		Event initialisation = (Event) Make.event("INITIALISATION");
		basis.getEvents().add(initialisation);
		
		Action a1 = (Action) Make.action(Strings.initInternalQName, Strings.initInternalQAction, "internal Q is initially empty");
		initialisation.getActions().add(a1);
		Action a2 = (Action) Make.action(Strings.initExternalQName, Strings.initExternalQAction, "external Q is initially empty");
		initialisation.getActions().add(a2);		
		Action a3 = (Action) Make.action(Strings.setNotComplete_actionName, Strings.setNotComplete_actionExpression, "completion is initially FALSE");
		initialisation.getActions().add(a3);
		Action a4 = (Action) Make.action(Strings.initDequeuedTriggerSetName, Strings.initDequeuedTriggerSetAction, "dequeued triggers is initially empty");
		initialisation.getActions().add(a4);
		
		{//abstract basis for future events that raise an external trigger
		Event e1 = (Event) Make.event(Strings.futureExternalTriggersEventName, Strings.futureExternalTriggersEventComment);
		Parameter p1 = (Parameter) Make.parameter(Strings.raisedExternalTriggersParameterName, Strings.raisedExternalTriggersParameterComment);
		e1.getParameters().add(p1);
		Guard e1_g1 = (Guard) Make.guard(Strings.e1_g1_Name, false, Strings.e1_g1_Predicate, Strings.e1_g1_Comment);
		e1.getGuards().add(e1_g1);
		Action e1_a1 = (Action) Make.action(Strings.e1_a1_Name, Strings.e1_a1_Action, Strings.e1_a1_Comment);
		e1.getActions().add(e1_a1);
		basis.getEvents().add(e1);
		}

		{//this is the event that dequeues an internal trigger
		Event e2i = (Event) Make.event(Strings.dequeueInternalTriggerEventName, "<INTERNAL><PRIORITY=9>");		//annotate as internal with low priority for scenario checker
		Parameter e2i_p1 = (Parameter) Make.parameter(Strings.dequeuedInternalTriggerParameterName, Strings.dequeuedInternalTriggerParameterComment);
		e2i.getParameters().add(e2i_p1);	
		Guard e2i_g1 = (Guard) Make.guard(Strings.e2i_g1_Name, false, Strings.e2i_g1_Predicate, Strings.e2i_g1_Comment);
		e2i.getGuards().add(e2i_g1);
		Guard e2i_g2 = (Guard) Make.guard(Strings.hasNoDequeuedTriggers_guardName, false, Strings.hasNoDequeuedTriggers_guardPredicate, Strings.hasNoDequeuedTriggers_guardComment);
		e2i.getGuards().add(e2i_g2);
		Guard e2i_g3 = (Guard) Make.guard(Strings.isComplete_guardName, false, Strings.isComplete_guardPredicate, Strings.isComplete_guardComment);
		e2i.getGuards().add(e2i_g3);
		Action e2i_a1 = (Action) Make.action(Strings.e2i_a1_Name, Strings.e2i_a1_Action, Strings.e2i_a1_Comment);
		e2i.getActions().add(e2i_a1);
		Action e2i_a2 = (Action) Make.action(Strings.e2i_a2_Name, Strings.e2i_a2_Action, Strings.e2i_a2_Comment);
		e2i.getActions().add(e2i_a2);
		Action e2i_a3 = (Action) Make.action(Strings.setNotComplete_actionName, Strings.setNotComplete_actionExpression, Strings.setNotComplete_actionComment);
		e2i.getActions().add(e2i_a3);
		basis.getEvents().add(e2i);
		}
		{//this is the event that dequeues an external trigger
		Event e2e = (Event) Make.event(Strings.dequeueExternalTriggerEventName, "<INTERNAL><PRIORITY=9>");		//annotate as internal with low priority for scenario checker
		Parameter e2e_p1 = (Parameter) Make.parameter(Strings.dequeuedExternalTriggerParameterName, Strings.dequeuedExternalTriggerParameterComment);
		e2e.getParameters().add(e2e_p1);	
		Guard e2e_g1 = (Guard) Make.guard(Strings.e2e_g1_Name, false, Strings.e2e_g1_Predicate, Strings.e2e_g1_Comment);
		e2e.getGuards().add(e2e_g1);
		Guard e2e_g2 = (Guard) Make.guard(Strings.hasNoDequeuedTriggers_guardName, false, Strings.hasNoDequeuedTriggers_guardPredicate, Strings.hasNoDequeuedTriggers_guardComment);
		e2e.getGuards().add(e2e_g2);
		Guard e2e_g3 = (Guard) Make.guard(Strings.isComplete_guardName, false, Strings.isComplete_guardPredicate, Strings.isComplete_guardComment);
		e2e.getGuards().add(e2e_g3);
		Guard e2e_g4 = (Guard) Make.guard(Strings.e2e_g4_Name, false, Strings.e2e_g4_Predicate, Strings.e2e_g4_Comment);
		e2e.getGuards().add(e2e_g4);
		Action e2e_a1 = (Action) Make.action(Strings.e2e_a1_Name, Strings.e2e_a1_Action, Strings.e2e_a1_Comment);
		e2e.getActions().add(e2e_a1);
		Action e2e_a2 = (Action) Make.action(Strings.e2e_a2_Name, Strings.e2e_a2_Action, Strings.e2e_a2_Comment);
		e2e.getActions().add(e2e_a2);
		Action e2e_a3 = (Action) Make.action(Strings.setNotComplete_actionName, Strings.setNotComplete_actionExpression, Strings.setNotComplete_actionComment);
		e2e.getActions().add(e2e_a3);
		basis.getEvents().add(e2e);
		}
		{//abstract basis for future events that represent triggered transitions 
		Event e3 = (Event) Make.event(Strings.consumeTriggerEventName, Strings.consumeTriggerEventComment);
		Parameter p3_1 = (Parameter) Make.parameter(Strings.consumedTriggerParameterName, Strings.consumedTriggerParameterComment);
		e3.getParameters().add(p3_1);
		Parameter p3_2 = (Parameter) Make.parameter(Strings.raisedInternalTriggersParameterName, Strings.raisedInternalTriggersParameterComment);
		e3.getParameters().add(p3_2);
		Guard e3_g1 = (Guard) Make.guard(Strings.e3_g1_Name, false, Strings.e3_g1_Predicate, Strings.e3_g1_Comment);
		e3.getGuards().add(e3_g1);
		Guard e3_g3 = (Guard) Make.guard(Strings.isNotComplete_guardName, false, Strings.isNotComplete_guardPredicate, Strings.isNotComplete_guardComment);
		e3.getGuards().add(e3_g3);
		Guard e3_g4 = (Guard) Make.guard(Strings.raisedInternalTriggersGuardName, false, Strings.raisedInternalTriggersGuardPredicate, Strings.raisedInternalTriggersGuardComment);
		e3.getGuards().add(e3_g4);
		Action e3_a1 = (Action) Make.action(Strings.clearDequeuedTriggers_actionName, Strings.clearDequeuedTriggers_actionExpression, Strings.clearDequeuedTriggers_actionComment);
		e3.getActions().add(e3_a1);
		Action e3_a3 = (Action) Make.action(Strings.raisedInternalTriggersActionName, Strings.raisedInternalTriggersActionAction, Strings.raisedInternalTriggersActionComment);
		e3.getActions().add(e3_a3);
		basis.getEvents().add(e3);
		}
		{//discard a trigger if no transitions enabled by it
		Event e4 = (Event) Make.event(Strings.noTriggeredTransitionsEnabledEventName, "<INTERNAL><PRIORITY=9>  - "+ Strings.noTriggeredTransitionsEnabledEventComment);
		Guard e4_g1 = (Guard) Make.guard(Strings.isNotComplete_guardName, false, Strings.isNotComplete_guardPredicate, Strings.isNotComplete_guardComment);
		e4.getGuards().add(e4_g1);
		Guard e4_g2 = (Guard) Make.guard(Strings.hasDequeuedTriggers_guardName, false, Strings.hasDequeuedTriggers_guardPredicate, Strings.hasDequeuedTriggers_guardComment);
		e4.getGuards().add(e4_g2);
		Action e4_a1 = (Action) Make.action(Strings.clearDequeuedTriggers_actionName, Strings.clearDequeuedTriggers_actionExpression, Strings.clearDequeuedTriggers_actionComment);
		e4.getActions().add(e4_a1);
		basis.getEvents().add(e4);
		}
		{//This is the basis of untriggered transitions firing
		Event e5 = (Event) Make.event(Strings.untriggeredEventName);
		Parameter p5_1 = (Parameter) Make.parameter(Strings.raisedInternalTriggersParameterName, Strings.raisedInternalTriggersParameterComment);
		e5.getParameters().add(p5_1);
		Guard e5_g1 = (Guard) Make.guard(Strings.isNotComplete_guardName, false, Strings.isNotComplete_guardPredicate, Strings.isNotComplete_guardComment);
		e5.getGuards().add(e5_g1);
		Guard e5_g2 = (Guard) Make.guard(Strings.hasNoDequeuedTriggers_guardName, false, Strings.hasNoDequeuedTriggers_guardPredicate, Strings.hasNoDequeuedTriggers_guardComment);
		e5.getGuards().add(e5_g2);
		Guard e5_g3 = (Guard) Make.guard(Strings.raisedInternalTriggersGuardName, false, Strings.raisedInternalTriggersGuardPredicate, Strings.raisedInternalTriggersGuardComment);
		e5.getGuards().add(e5_g3);
		Action e5_a2 = (Action) Make.action(Strings.raisedInternalTriggersActionName, Strings.raisedInternalTriggersActionAction, Strings.raisedInternalTriggersActionComment);
		e5.getActions().add(e5_a2);
		basis.getEvents().add(e5);
		}
		{//This is the inner completion event that fires when untriggered transitions are completed
		Event e6 = (Event) Make.event(Strings.completionEventName, "<INTERNAL><PRIORITY=9>");		//annotate as internal with low priority for scenario checker
		Guard e6_g1 = (Guard) Make.guard(Strings.isNotComplete_guardName, false, Strings.isNotComplete_guardPredicate, Strings.isNotComplete_guardComment);
		e6.getGuards().add(e6_g1);
		Guard e6_g2 = (Guard) Make.guard(Strings.hasNoDequeuedTriggers_guardName, false, Strings.hasNoDequeuedTriggers_guardPredicate, Strings.hasNoDequeuedTriggers_guardComment);
		e6.getGuards().add(e6_g2);
		Action e6_a1 = (Action) Make.action(Strings.setComplete_actionName, Strings.setComplete_actionExpression, Strings.setComplete_actionComment);
		e6.getActions().add(e6_a1);
		basis.getEvents().add(e6);
		}
		return basis;
	}
	
}
