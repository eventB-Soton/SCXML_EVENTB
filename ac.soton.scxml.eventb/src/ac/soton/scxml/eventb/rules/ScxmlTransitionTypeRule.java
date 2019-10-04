/*******************************************************************************
 *  Copyright (c) 2016 University of Southampton.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eventb.emf.core.EventBNamed;
import org.eventb.emf.core.machine.Action;
import org.eventb.emf.core.machine.Event;
import org.eventb.emf.core.machine.Guard;
import org.eventb.emf.core.machine.Machine;
import org.eventb.emf.core.machine.MachinePackage;

import ac.soton.emf.translator.TranslationDescriptor;
import ac.soton.emf.translator.configuration.IRule;
import ac.soton.emf.translator.utils.Find;
import ac.soton.eventb.emf.core.extension.coreextension.TypedParameter;
import ac.soton.eventb.statemachines.AbstractNode;
import ac.soton.eventb.statemachines.Statemachine;
import ac.soton.eventb.statemachines.StatemachinesPackage;
import ac.soton.eventb.statemachines.Transition;
import ac.soton.scxml.ScxmlAssignType;
import ac.soton.scxml.ScxmlInitialType;
import ac.soton.scxml.ScxmlPackage;
import ac.soton.scxml.ScxmlRaiseType;
import ac.soton.scxml.ScxmlScxmlType;
import ac.soton.scxml.ScxmlStateType;
import ac.soton.scxml.ScxmlTransitionType;
import ac.soton.scxml.eventb.strings.Strings;
import ac.soton.scxml.eventb.utils.IumlbScxmlAdapter;
import ac.soton.scxml.eventb.utils.Make;
import ac.soton.scxml.eventb.utils.Refinement;
import ac.soton.scxml.eventb.utils.Utils;

/**
 * This rules translates SCXML transitions, excluding initial transitions,
 * into iUML-B transitions.
 * 
 * @author cfs
 *
 */
public class ScxmlTransitionTypeRule extends AbstractSCXMLImporterRule implements IRule {

	private List<Refinement> refinements = new ArrayList<Refinement>();

	@Override
	public boolean enabled(final EObject sourceElement) throws Exception  {
		ScxmlScxmlType scxmlContainer = (ScxmlScxmlType) Find.containing(ScxmlPackage.Literals.SCXML_SCXML_TYPE, sourceElement);
		return scxmlContainer!=null && !(sourceElement.eContainer() instanceof ScxmlInitialType);
	}
	
	@Override
	public boolean dependenciesOK(EObject sourceElement, final List<TranslationDescriptor> generatedElements) throws Exception  {
		ScxmlScxmlType scxmlContainer = (ScxmlScxmlType) Find.containing(ScxmlPackage.Literals.SCXML_SCXML_TYPE, sourceElement);
		ScxmlStateType stateContainer = (ScxmlStateType) Find.containing(ScxmlPackage.Literals.SCXML_STATE_TYPE, sourceElement.eContainer().eContainer());
		if (sourceElement.eContainer().eContainer().eClass() ==ScxmlPackage.Literals.SCXML_PARALLEL_TYPE && 
				stateContainer!=null) {
			if(stateContainer.eContainer().eClass() == ScxmlPackage.Literals.SCXML_STATE_TYPE ){
				stateContainer = (ScxmlStateType) stateContainer.eContainer();
			}else {
				stateContainer = null;
			}
		}
		refinements.clear();
		int refinementLevel = Utils.getRefinementLevel(sourceElement); 
		int depth = getRefinementDepth(sourceElement);		
		String parentSmName = (stateContainer==null? scxmlContainer.getName() : stateContainer.getId())+"_sm";
		
		for (int i=refinementLevel; i<=depth; i++){
			Refinement ref = new Refinement();
			ref.level = i;
			Machine m = (Machine) Find.translatedElement(generatedElements, null, null, MachinePackage.Literals.MACHINE, Utils.getMachineName(scxmlContainer,i));
			ref.machine = m;
			if (ref.machine == null) 
				return false;
			
			ref.statemachine = (Statemachine) Find.element(m, null, null, StatemachinesPackage.Literals.STATEMACHINE, parentSmName);
			if (ref.statemachine == null) 
				return false;
			
			EObject container = sourceElement.eContainer();
			String sourceStateName = container instanceof ScxmlStateType? 
										((ScxmlStateType)sourceElement.eContainer()).getId() :
										null;
			ref.source = (AbstractNode) Find.element(m, null, null, StatemachinesPackage.Literals.ABSTRACT_NODE, sourceStateName);
			if (ref.source == null) 
				return false;	
			String targetStateName = ((ScxmlTransitionType) sourceElement).getTarget().get(0);		//we only support single target - ignore the rest
			ref.target = (AbstractNode) Find.element(m, null, null, StatemachinesPackage.Literals.ABSTRACT_NODE, targetStateName);
			if (ref.target == null) 
				return false;
			refinements.add(ref);
		}
		return true;
	}

	@Override
	public List<TranslationDescriptor> fire(EObject sourceElement, List<TranslationDescriptor> translatedElements) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, Trigger> triggerStore = (Map<String, Trigger>) storage.fetch("triggers");
		ScxmlTransitionType scxmlTransition = ((ScxmlTransitionType) sourceElement);
		IumlbScxmlAdapter adaptedScxmlTransition = new IumlbScxmlAdapter(scxmlTransition);
		
		int finalised = adaptedScxmlTransition.getFinalised();
		
		for (Refinement ref : refinements){
			
			//add a transition in the iUML-B statemachine
			Transition transition = Make.transition(ref.source, ref.target, "");
			ref.statemachine.getTransitions().add(transition);
			
			String completionGuard = "\u00ac("+ref.source.getName()+"= TRUE";
			
			//This next section creates the Event-B events that represent the possible Scxml transition synchronisations
			//
			// (triggers are called events in SCXML). 
			String scxmlTransitionEvent = scxmlTransition.getEvent();	
			if (scxmlTransitionEvent==null || scxmlTransitionEvent.trim().length() == 0){ scxmlTransitionEvent ="null";}

			Set<Event> toFinalise = new HashSet<Event>();
			
			//FIXME: currently we support multiple triggers of a transition - possibly this is not needed? 
			String[] triggerNames = scxmlTransitionEvent.split(" ");
			System.out.println("triggers: "+triggerNames.length);
			for (String triggerName : triggerNames){
				System.out.println("trigger[0]: "+triggerName);
				Trigger trigger = triggerStore.get(triggerName);
				if (trigger==null){
					continue;
				}
				
				for (Set<ScxmlTransitionType> combi : trigger.getTransitionCombinations(ref.level, getRefinementDepth(sourceElement))){
					if (combi != null) {
						if (combi.contains(scxmlTransition)) { 
							Event event = Utils.getOrCreateEvent(ref, translatedElements, trigger, combi, getRefinementDepth(sourceElement));
							//create/find an event to elaborate
							transition.getElaborates().add(event);
						}
					}
				}
				
				//add a guard to define the triggers that are raised by this transition
				String raiseList = "";
				// set parameter value for raised triggers
				for (ScxmlRaiseType raise : scxmlTransition.getRaise()){
					if(Utils.getRefinementLevel(raise) <= ref.level){
						raiseList = raiseList.length()==0? raise.getEvent() : raiseList + ","+raise.getEvent();
					}
				}	
				// no guard needed if there are no raised triggers.. unless..
				// refinement has been finalised.. in which case we specify that no future triggers will ever be raised by this event
				if (!"".equals(raiseList) || finalised>0){  
					raiseList = "".equals(raiseList)? "\u2205" : "{"+raiseList+" }";
					Guard guard =  (Guard) Make.guard(
							Strings.specificRaisedInternalTriggersGuardName+ref.level,false,
							Strings.specificRaisedInternalTriggersGuardPredicate(raiseList, -1), //finalised),  //finalising trigger raising does not work
							Strings.specificRaisedInternalTriggersGuardComment); 
					transition.getGuards().add(guard);
				}
				if ("null".contentEquals(trigger.getName())) {
					completionGuard = completionGuard+" \u2227 "+ "SCXML_dt=\u2205"	;
				}else {
					completionGuard = completionGuard+" \u2227 "+ trigger.getName()+ "\u2208SCXML_dt" ;
				}
			}
			
			//add any explicit parameters of the scxml transition
			List<IumlbScxmlAdapter> prms = new IumlbScxmlAdapter(scxmlTransition).getAnyChildren("parameter");
			for (IumlbScxmlAdapter prm : prms){
				int rl = Utils.getRefinementLevel(prm);
				if (rl <= ref.level){
					String name = (String)prm.getAnyAttributeValue("name");
					String type = (String)prm.getAnyAttributeValue("type");
					String comment = (String)prm.getAnyAttributeValue("comment");
					TypedParameter parameter =  (TypedParameter) Make.typedParameter(name,Strings.convertToRodin(type),comment); 
					transition.getParameters().add(parameter);
				}
			}
			
			//add any explicit guards of the scxml transition
			List<IumlbScxmlAdapter> gds = new IumlbScxmlAdapter(scxmlTransition).getGuards();
			for (IumlbScxmlAdapter gd : gds){
				int rl = Utils.getRefinementLevel(gd);
				if (rl <= ref.level){
					String name = (String)gd.getAnyAttributeValue("name");
					boolean derived = Boolean.parseBoolean((String)gd.getAnyAttributeValue("derived"));
					String predicate = Strings.PREDICATE((String)gd.getAnyAttributeValue("predicate"));
					String comment = (String)gd.getAnyAttributeValue("comment");
					Guard guard =  (Guard) Make.guard(name,derived,predicate,comment); 
					if (!derived) {
						completionGuard = completionGuard + " \u2227 "+ predicate;
					}
					transition.getGuards().add(guard);
				}
			}
			
			//add any explicit actions of the scxml transition (assigns in SCXML)
			int i=0;
			for (ScxmlAssignType assign : scxmlTransition.getAssign()){
				if(Utils.getRefinementLevel(assign) <= ref.level){
					Action action = (Action) Make.action(transition.getLabel()+"_act_"+i, Strings.ASSIGN_ACTION(assign), "SCXML transition assign");
					transition.getActions().add(action);
					i++;
				}
			}	
	
			completionGuard = completionGuard+ ")";
			if (finalised>0 && ref.level>=finalised ) {
				if ("null".equals(scxmlTransitionEvent)) {
					toFinalise.add((Event) Find.element(ref.machine, ref.machine, MachinePackage.Literals.MACHINE__EVENTS, MachinePackage.Literals.EVENT, Strings.completionEventName));
				}else {
					toFinalise.add((Event) Find.element(ref.machine, ref.machine, MachinePackage.Literals.MACHINE__EVENTS, MachinePackage.Literals.EVENT, Strings.noTriggeredTransitionsEnabledEventName));
				}
				for (Event event : toFinalise){
					if (event != null) {
						String completionGuardName = Strings.CompletionGuardName(transition.getLabel());
						if (findNamedElementInEvent(ref, event, MachinePackage.Literals.GUARD, completionGuardName)==null) {
							Guard guard = (Guard) Make.guard(completionGuardName, completionGuard);
							event.getGuards().add(guard);
						}
					}
				}
			}
			
		}
		return Collections.emptyList();
	}

	/**
	 * Find element in event with extension	
	 * @param ref 
	 */
		public EventBNamed findNamedElementInEvent(Refinement ref, Event event, EClass eClass, String name){
			for (EObject element : event.getAllContained(eClass, true)){
				if (element instanceof EventBNamed && name.equals(((EventBNamed)element).getName())) 
					return (EventBNamed) element;
			}
			int refinementsIndex = ref.level - refinements.get(0).level; //levels start from >0 so need to adjust index
			if (event.isExtended() & refinementsIndex>0) {
				Refinement abst = refinements.get(refinementsIndex-1);
				Event refinedEvent = event.getRefines().get(0);
				if (refinedEvent.eIsProxy()) {		// cannot resolve because resources have not been saved yet.
					refinedEvent = (Event) Utils.findNamedElement(abst.machine.getEvents(), event.getName());
				}
				return findNamedElementInEvent(abst, refinedEvent, eClass, name);
			}else {
				return null;
			}
		}
		
}
