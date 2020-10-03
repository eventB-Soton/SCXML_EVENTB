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
/**
 * 
 */
package ac.soton.scxml.eventb.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xml.type.AnyType;
import org.eventb.emf.core.EventBElement;
import org.eventb.emf.core.EventBNamed;
import org.eventb.emf.core.Project;
import org.eventb.emf.core.machine.Convergence;
import org.eventb.emf.core.machine.Event;
import org.eventb.emf.core.machine.Guard;
import org.eventb.emf.core.machine.Machine;
import org.eventb.emf.core.machine.MachinePackage;

import ac.soton.emf.translator.TranslationDescriptor;
import ac.soton.emf.translator.utils.Find;
import ac.soton.eventb.emf.core.extension.navigator.refiner.AbstractElementRefiner;
import ac.soton.eventb.emf.core.extension.navigator.refiner.ElementRefinerRegistry;
import ac.soton.scxml.ScxmlAssignType;
import ac.soton.scxml.ScxmlDataType;
import ac.soton.scxml.ScxmlFinalType;
import ac.soton.scxml.ScxmlInitialType;
import ac.soton.scxml.ScxmlLogType;
import ac.soton.scxml.ScxmlRaiseType;
import ac.soton.scxml.ScxmlScxmlType;
import ac.soton.scxml.ScxmlStateType;
import ac.soton.scxml.ScxmlTransitionType;
import ac.soton.scxml.eventb.rules.Trigger;
import ac.soton.scxml.eventb.strings.Strings;

/**
 * <p> 
 * Utility methods used in scxml to iUML-B translation rules
 * 
 * </p>
 * 
 * @author cfs
 */
public class Utils {
	
		
	/**
	 * find the containing Project for this element
	 * 
	 * CURRENTLY RETURNS NULL
	 * 
	 * @param machine
	 * @return
	 * @throws IOException
	 */
		public static Project findProject(EObject sourceElement) throws IOException {
//			URI eventBelementUri = eventBelement.getURI();
//			URI projectUri = eventBelementUri.trimFragment().trimSegments(1);
//			ProjectResource projectResource = new ProjectResource();
//			projectResource.setURI(eventBelement.getURI());
//			projectResource.load(null);
//			for (EObject eObject : projectResource.getContents()){
//				if (eObject instanceof Project){
//					return (Project)eObject;
//				}
//			}
			return null;
		}
	
	
	/**
	 * Finds an event for the given transitions combination in the given machine or descriptors
	 *  if no such event is found a new event is created and added to the machine
	 *  
	 * @param machine
	 * @param translatedElements
	 * @param triggerName
	 * @param combi
	 * @return
	 */
	public static Event getOrCreateEvent(Refinement ref, List<TranslationDescriptor> descriptors, Trigger trigger, Set<ScxmlTransitionType> combi, int depth) {
		printCombi("getOrCreateEvent",combi, ref);
		String eventName = getCombiEventName(trigger.getName(), combi);		//PARAMETERS REMOVED... ref.machine, descriptors,
		Event ev = getOrCreateEvent(ref.machine, false, descriptors,eventName); 
		if ("null".equals(trigger.getName())) {
			ev.setComment("<INTERNAL><PRIORITY=3>  UNTRIGGERED TRANSITIONS "+ev.getComment());	//annotate transition events as internal for scenario checker 
		}else if (trigger.isInternal()){
			ev.setComment("<INTERNAL><PRIORITY=2>  INTERNAL TRIGGERED TRANSITIONS "+ev.getComment());	//annotate transition events as internal for scenario checker 
		}else {
			ev.setComment("<INTERNAL><PRIORITY=1>  EXTERNAL TRIGGERED TRANSITIONS "+ev.getComment());	//annotate transition events as internal for scenario checker 
		}
		String refinedEventName = getRefinesName(ref, trigger, combi, depth);
		if (!ev.getRefinesNames().contains(refinedEventName)){
			ev.getRefinesNames().add(refinedEventName);
		}
		if (ev.getRefinesNames().size()==0){
			ev.setExtended(false);
		}else{
			ev.setExtended(true);
		}
		//add trigger guard at the correct refinement level
		//(this is done as a descriptor so that the translator can decide whether it is needed (it may already be there by extension)
		if (!"null".equals(trigger.getName()) && ref.level>=trigger.getRefinementLevel()){
			Guard trig = (Guard) Make.guard(Strings.trigGd_Name, false, Strings.trigGd_Predicate(trigger), Strings.trigGd_Comment);
			descriptors.add(Make.descriptor(ev,MachinePackage.Literals.EVENT__GUARDS,trig,0));
		}
		return ev;
	}
	
	/**
	 * Returns the starting refinement level for this scxml element.
	 * The normal policy for refinement level is that it is annotated or if omitted, the same as its parent. 
	 * (This is the basic behaviour implemented by doGetRefinement Level)
	 * However, for convenience, some types of elements need a different behaviour. 
	 * For example, states are annotated with the refinement level that their nested state-machines appear - and they are first used in their parent statemachines refinement.
	 * Similarly transitions and their content should be used at the refinement level of their parent state.
	 * 
	 * The refinement level of a transition is always the same as its parent (recursively).
	 * The content of a transition is either annotated or the same as its parent transition (recursively).
	 * The refinement level of a state, final or initial is always the same as its parent's annotated level (non-recursively)...
	 * The refinement level is  given in a 'refinement' iumlb:attribute attached to the element,
	 * or, if none, the refinement level of its parent,
	 * or, if none, 0
	 * 
	 * @param scxmlElement
	 * @return
	 */
	public static int getRefinementLevel(Object scxml){
		EObject scxmlElement;
		IumlbScxmlAdapter scxmlAdapter;
		if (scxml instanceof IumlbScxmlAdapter) {
			scxmlElement = ((IumlbScxmlAdapter) scxml).target;
			scxmlAdapter = (IumlbScxmlAdapter) scxml;
		}else {
			scxmlElement = (EObject) scxml;
			scxmlAdapter = new IumlbScxmlAdapter(scxmlElement);
		}
		if (scxmlElement instanceof ScxmlTransitionType) {
			//use these rules but start from the containing state
			return getRefinementLevel(scxmlElement.eContainer());
		}else if (
				scxmlElement instanceof ScxmlRaiseType ||
				scxmlElement instanceof ScxmlAssignType ||
				scxmlElement instanceof AnyType && (
					"parameter".equals(scxmlAdapter.featureName) ||
					"guard".equals(scxmlAdapter.featureName)
					)
				) {
			if (scxmlAdapter.getBasicRefinementLevel()<0) {
				return getRefinementLevel(scxmlElement.eContainer());
			}
		}else if (
				scxmlElement instanceof ScxmlStateType || 
				scxmlElement instanceof ScxmlFinalType ||
				scxmlElement instanceof ScxmlInitialType){
			//use the basic rules for getting refinement level but starting from the states parent
			return Utils.doGetRefinementLevel(scxmlElement.eContainer());
		}
		//default - use the basic rules for getting refinement level
		return Utils.doGetRefinementLevel(scxmlElement);
	}
	
	/**
	 * Returns the starting semantic refinement level for this SCXML element
	 * This is given by the 'refinement' iumlb:attribute attached to the element,
	 * or, if none, the refinement level of its parent,
	 * or, if none, 0
	 * 
	 * @param scxmlElement
	 * @return
	 */
	private static int doGetRefinementLevel(EObject scxmlElement){
		int refinementLevel = new IumlbScxmlAdapter(scxmlElement).getBasicRefinementLevel();
		if (refinementLevel < 0) {
			if (scxmlElement.eContainer()==null){
				refinementLevel=0;
			}else{ 
				refinementLevel= doGetRefinementLevel(scxmlElement.eContainer());
			}
		}
		return refinementLevel;
	}

	
	/**
	 * refine - this makes a new element that refines the abstract one
	 * 
	 * @param sourceElement  - contained in a resource (used as basis for constructing a URI for the abstract element)
	 * @param abstractElement - to be refined (must be contained in a machine but not necessarily in a resource)
	 * @return refined element
	 */
	public static EObject refine(EObject sourceElement, EventBElement abstractElement) {
		URI uri = EcoreUtil.getURI(sourceElement);
		uri = uri.trimFragment().trimSegments(1);
		uri = uri.appendSegment(((Machine)abstractElement.getContaining(MachinePackage.Literals.MACHINE)).getName());
		uri = uri.appendFileExtension("bum");
		uri = uri.appendFragment(abstractElement.getReference());
		AbstractElementRefiner refiner = ElementRefinerRegistry.getRegistry().getRefiner(abstractElement);
		if (refiner == null) 
			return null;
		Map<EObject,EObject> copy = refiner.refine(uri, abstractElement, null);
		EObject refinedElement = copy.get(abstractElement);
		return refinedElement;
	}

	/**
	 * @param name
	 * @return
	 */
	public static String getMachineName(ScxmlScxmlType scxmlContainer, int refinementLevel) {
		return scxmlContainer.getName()+"_"+refinementLevel;
	}

	/**
	 * @param scxml
	 * @param i
	 * @return
	 */
	public static String getContextName(ScxmlScxmlType scxmlContainer, int refinementLevel) {
		return getMachineName(scxmlContainer,refinementLevel)+"_ctx";
	}

	/**
	 * gets the type set of an ScxmlData item
	 * @param scxml
	 * @return
	 */
	public static String getType(ScxmlDataType scxml) {
		String type = (String) new IumlbScxmlAdapter(scxml).getAnyAttributeValue("type");
		//TODO: Use rodin keyboard converter here
		if (type!=null && type.length()>0) {
			type = type.trim();
			if ("NAT".equals(type)) type = "\u2115";
			if ("INT".equals(type)) type = "\u2124";
		}else{
			//fallback if no iumlb:type attribute provided
			String expr = scxml.getExpr();
			try{ Integer.parseInt(expr);
				type = "\u2124"; //Integer
			}catch (NumberFormatException e) {
				if (expr==null) type = "<null>";
				if ("true".equals(expr) || "false".equals(expr)){
					type = "BOOL";
				}	
			}
		}
		return Strings.TYPE_PREDICATE(scxml.getId(),type);
	}	
	
///////////////////////////////////////////////////////////
// PRIVATE/////////////////////////////////////////////////
///////////////////////////////////////////////////////////	
	
	/**
	 * This finds an event in the given machine by name by looking in the following (in this order)
	 *  the given machine's events
	 *  the given list of GenerationDescriptors
	 *  if no such event is found a new event is created and added to the machine
	 *  
	 * @param machine
	 * @param descriptors
	 * @param eventName
	 * @return
	 */
	private static Event getOrCreateEvent(Machine machine, boolean extended, List<TranslationDescriptor> descriptors, String eventName) {
		Event ev = (Event) findNamedElement(machine.getEvents(), eventName);
		if (ev==null) ev =  (Event) Find.translatedElement(descriptors, machine, MachinePackage.Literals.MACHINE__EVENTS, eventName);
		if (ev==null) {
			ev = (Event) Make.event(eventName, extended, Convergence.ORDINARY, Collections.<String> emptyList(), "");
			machine.getEvents().add(ev);
		}
		return ev;
	}
	
	/**
	 * Find by name, an element in a list of EventBNamed elements
	 * @param collection
	 * @param name
	 * @return
	 */
		public static EventBNamed findNamedElement(EList<? extends EventBNamed> collection, String name){
			for (EventBNamed element : collection){
				if (name.equals(element.getName())) return element;
			}
			return null;
		}
		
	/**
	 * finds the appropriate refined event name for an event of this combi
	 * 
	 * @param ref
	 * @param descriptors
	 * @param trigger
	 * @param combi
	 * @return
	 */
	private static String getRefinesName(Refinement ref, Trigger trigger, Set<ScxmlTransitionType> combi, int depth) {
		//set the event refinement up
		String refinedEventName = null;						
		Set<ScxmlTransitionType> refinedCombi = findRefinedCombi(trigger, combi, ref, depth);
		if (refinedCombi.size()>0){   
			refinedEventName =  Utils.getCombiEventName(trigger.getName(), refinedCombi); ///parameters removed... ref.machine, descriptors, ;
		}else{
			if ("null".equals(trigger.getName())){
					refinedEventName = Strings.untriggeredEventName;
			}else{
				refinedEventName = Strings.consumeTriggerEventName;								
			}
		}
		return refinedEventName;
	}

	/**
	 * finds a combi that the given combi should refine
	 * 
	 * @param triggerName 
	 * @param combi
	 * @param ref
	 * @return
	 * @throws Exception 
	 */
	private static Set<ScxmlTransitionType> findRefinedCombi(Trigger trigger, Set<ScxmlTransitionType> combi, Refinement ref, int depth)  {
		if (ref.level==0) return Collections.emptySet();
		//first find all possible candidates that are subsets in the previous refinement level (can include itself)
		Set<Set<ScxmlTransitionType>> possibles = new HashSet<Set<ScxmlTransitionType>>();
		for (Set<ScxmlTransitionType> otherCombi : trigger.getTransitionCombinations(ref.level-1, depth)){
			if (isSubset(combi,otherCombi)){
				possibles.add(otherCombi);
			}
		}
		// now find the biggest subset out of these possibles
		Set<ScxmlTransitionType> refinedCombi = Collections.emptySet();
		for (Set<ScxmlTransitionType> poss : possibles){
			if (isSubset(poss, refinedCombi)){
				refinedCombi = poss;
			}
		}
		return refinedCombi;
	}


	/**
	 * @param combi
	 * @param otherCombi
	 * @return
	 */
	private static boolean isSubset(Set<ScxmlTransitionType> combi, Set<ScxmlTransitionType> otherCombi) {
		for (ScxmlTransitionType tr : otherCombi){
			if (!combi.contains(tr)){
				return false;
			}
		}
		return true;
	}
	
	/**
	 * constructs a name from a trigger name and a set of transitions.
	 * Any transitions that do not have a ScxmlStateType source are ignored.
	 * 
	 * @param triggerName
	 * @param combi
	 * @return
	 */
	private static String getCombiEventName(String triggerName, Set<ScxmlTransitionType> combi) {
		String eventName = (triggerName == null || triggerName.length()==0 || "null".equals(triggerName))? "" : triggerName;
		for (ScxmlTransitionType tr : combi){
			if (tr.eContainer() instanceof ScxmlStateType){
				String basicEventName = Utils.getBasicEventName(tr);
				eventName = eventName.length()==0 ?  basicEventName : eventName+ "__" + basicEventName;
			}
		}
		return eventName;
	}
	
	/**
	 * This gets the name of an event that should be elaborated by an iUML-B transition when it is generated from the given ScxmlTransition. 
	 * DO NOT USE THIS FOR INITIAL TRANSITIONS -> returns null if the source is not a real ScxmlStateType
	 * 
	 * the event name is obtained by the following methods (in order of precedence):
	 * 
	 * a) the transition has an iuml-b:label attribute
	 * b) the transition has a log label
	 * c) if none of the above provide any labels a default 'source_targets' format is used
	 * 
	 * 
	 * @param scxmlTransition
	 * @return
	 */
	private static String getBasicEventName(ScxmlTransitionType scxmlTransition){

		EObject source = scxmlTransition.eContainer();
		
		//add initialisation events if source is an initial state
		if (!(source instanceof ScxmlStateType)) {
			return null;
		}
	
		// iuml-b label
		IumlbScxmlAdapter adapter = new IumlbScxmlAdapter(scxmlTransition);
		Object label = adapter.getAnyAttributeValue("label");
		if (label instanceof String){
			return (String) label;
		}
		
		//log label
		for (ScxmlLogType log : scxmlTransition.getLog()){
			String logLabel = log.getLabel();
			if (logLabel != null && logLabel.length()>0) {
				return logLabel;
			}
		}
			
		//if no names found default to 'source_targets' format
		String eventName = ((ScxmlStateType)source).getId();
		for (String targetName : scxmlTransition.getTarget()){
			eventName=eventName+"_"+targetName;
		}
		return eventName;
	}
	
	
	//FIXME: for debugging only
	private static void printCombi(String string, Set<ScxmlTransitionType> combi, Refinement ref){
		System.out.println("******Combi*******");
		System.out.println("Refinement Level: "+ref.level+" :: code: "+string);
		for (ScxmlTransitionType tr:combi){
			String source = 
					tr.eContainer() instanceof ScxmlStateType? ((ScxmlStateType)tr.eContainer()).getId() :
					(tr.eContainer() instanceof ScxmlInitialType? "Initial" : //((ScxmlInitialType)tr.eContainer()).getId() :
					tr.eContainer().toString());
			String target = 
					tr.getTarget() instanceof ScxmlStateType? ((ScxmlStateType)tr.eContainer()).getId() :
					(tr.getTarget() instanceof ScxmlFinalType? "Final" : //((ScxmlInitialType)tr.eContainer()).getId() :
					tr.getTarget().toString());
			System.out.println("transition:: "
					+ "trigger="+tr.getEvent()+
					", source="+source+
					", target="+target);			
		}
		System.out.println("xxxxxxxxxxxxxxxxx");
	}

}
