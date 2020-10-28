/*******************************************************************************
 * Copyright (c) 2020 University of Southampton.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    University of Southampton - initial API and implementation
 *******************************************************************************/
package ac.soton.scxml.eventb.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import ac.soton.scxml.ScxmlDataType;
import ac.soton.scxml.ScxmlDatamodelType;
import ac.soton.scxml.ScxmlPackage;
import ac.soton.scxml.ScxmlScxmlType;
import ac.soton.scxml.ScxmlStateType;
import org.eventb.emf.core.machine.Action;
import org.eventb.emf.core.machine.Event;
import org.eventb.emf.core.machine.Invariant;
import org.eventb.emf.core.machine.Machine;
import org.eventb.emf.core.machine.MachinePackage;
import org.eventb.emf.core.machine.Variable;

import ac.soton.emf.translator.TranslationDescriptor;
import ac.soton.emf.translator.configuration.IRule;
import ac.soton.emf.translator.utils.Find;
import ac.soton.eventb.statemachines.State;
import ac.soton.eventb.statemachines.StatemachinesPackage;
import ac.soton.scxml.eventb.strings.Strings;
import ac.soton.scxml.eventb.utils.IumlbScxmlAdapter;
import ac.soton.scxml.eventb.utils.Make;
import ac.soton.scxml.eventb.utils.Utils;

public class ScxmlDataTypeRule extends AbstractSCXMLImporterRule implements IRule {

	private ScxmlScxmlType scxmlContainer=null;
	
	private List<RefinementLevelDescriptor> refinements = new ArrayList<RefinementLevelDescriptor>();
	private class RefinementLevelDescriptor {
		//private int level = -1;
		private Machine machine = null;
		private Event initialisation = null;	
	}
	
	@Override
	public boolean enabled(final EObject sourceElement) throws Exception  {
		if ("Refinement".equals(((ScxmlDataType)sourceElement).getId())){
			return false;
		}
		if (!(new IumlbScxmlAdapter((ScxmlDataType)sourceElement).isVariable())) {
			return false;
		}
		scxmlContainer = (ScxmlScxmlType) Find.containing(ScxmlPackage.Literals.SCXML_SCXML_TYPE, sourceElement);
		return scxmlContainer!=null;
	}
	
	@Override
	public boolean dependenciesOK(EObject sourceElement, final List<TranslationDescriptor> generatedElements) throws Exception  {
		ScxmlDataType scxml = (ScxmlDataType)sourceElement;
		refinements.clear();
		int refinementLevel = Utils.getRefinementLevel(sourceElement);
		int depth = getRefinementDepth(sourceElement);		
		
		for (int i=refinementLevel; i<=depth; i++){
			Machine m = (Machine) Find.translatedElement(generatedElements, null, null, MachinePackage.Literals.MACHINE, Utils.getMachineName(scxmlContainer,i));	
			if (m == null)  return false;
			Event initialisation = (Event) Find.translatedElement(generatedElements, m, events, MachinePackage.Literals.EVENT, "INITIALISATION");
			if (initialisation == null) return false;
			State state = null;
			if (isOwnedByState(scxml) && i==refinementLevel){
				String stateName = ((ScxmlStateType)Find.containing(ScxmlPackage.Literals.SCXML_STATE_TYPE, scxml)).getId();
				state = (State) Find.translatedElement(generatedElements, null, null, StatemachinesPackage.Literals.STATE, stateName);
				if (state==null) return false;
			}
			
			RefinementLevelDescriptor ref = new RefinementLevelDescriptor();
			//ref.level = i;
			ref.machine = m;
			ref.initialisation = initialisation;
			refinements.add(ref);
		}
		return true;
	}

	@Override
	public List<TranslationDescriptor> fire(EObject sourceElement, List<TranslationDescriptor> generatedElements) throws Exception {
		ScxmlDataType scxml = (ScxmlDataType)sourceElement;
		String vname = Strings.LOCATION(scxml);
		boolean done = false; //flag used to ensure we only create type invariants and initialisation once
		for (RefinementLevelDescriptor ref : refinements){
			Variable variable =  (Variable) Make.variable(vname, "");
			ref.machine.getVariables().add(variable);
			if (done == false){
				Invariant invariant =  (Invariant) Make.invariant(vname+"_type", Utils.getType(scxml), "");
				ref.machine.getInvariants().add(invariant);
				//Also only need to do intialisation once because of extension
				Action initAction =  (Action) Make.action(vname+"_init", Strings.INIT_ACTION(scxml), "");
				ref.initialisation.getActions().add(initAction);
				done=true;
			}		
		}
		return Collections.emptyList();
	}
	
	private boolean isOwnedByState(EObject scxml){
		EObject owner = scxml.eContainer();
		if (owner instanceof ScxmlStateType) return true;
		if (owner instanceof ScxmlDatamodelType) return isOwnedByState(owner);
		return false;
	}
	
}
