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

import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eventb.emf.core.context.CarrierSet;
import org.eventb.emf.core.context.Context;
import org.eventb.emf.core.context.ContextPackage;

import ac.soton.emf.translator.TranslationDescriptor;
import ac.soton.emf.translator.configuration.IRule;
import ac.soton.emf.translator.utils.Find;
import ac.soton.eventb.statemachines.State;
import ac.soton.eventb.statemachines.StatemachinesPackage;
import ac.soton.scxml.ScxmlDataType;
import ac.soton.scxml.ScxmlDatamodelType;
import ac.soton.scxml.ScxmlPackage;
import ac.soton.scxml.ScxmlScxmlType;
import ac.soton.scxml.ScxmlStateType;
import ac.soton.scxml.eventb.strings.Strings;
import ac.soton.scxml.eventb.utils.IumlbScxmlAdapter;
import ac.soton.scxml.eventb.utils.Make;
import ac.soton.scxml.eventb.utils.Utils;

public class ScxmlDataTypeCarrierSetRule extends AbstractSCXMLImporterRule implements IRule {

	private ScxmlScxmlType scxmlContainer=null;
	
	private Context context = null;
	
	@Override
	public boolean enabled(final EObject sourceElement) throws Exception  {
		if ("Refinement".equals(((ScxmlDataType)sourceElement).getId())){
			return false;
		}
		if (!(new IumlbScxmlAdapter((ScxmlDataType)sourceElement).isCarrierSet())) {
			return false;
		}
		scxmlContainer = (ScxmlScxmlType) Find.containing(ScxmlPackage.Literals.SCXML_SCXML_TYPE, sourceElement);
		return scxmlContainer!=null;
	}
	
	@Override
	public boolean dependenciesOK(EObject sourceElement, final List<TranslationDescriptor> generatedElements) throws Exception  {
		ScxmlDataType scxml = (ScxmlDataType)sourceElement;
		int refinementLevel = Utils.getRefinementLevel(sourceElement);	
		
		context  = (Context) Find.translatedElement(generatedElements, null, null, ContextPackage.Literals.CONTEXT, Utils.getContextName(scxmlContainer,refinementLevel));
		if (context == null)  return false;
		
		State state = null;
		if (isOwnedByState(scxml)){
			String stateName = ((ScxmlStateType)Find.containing(ScxmlPackage.Literals.SCXML_STATE_TYPE, scxml)).getId();
			state = (State) Find.translatedElement(generatedElements, null, null, StatemachinesPackage.Literals.STATE, stateName);
			if (state==null) return false;
		}
		return true;
	}

	@Override
	public List<TranslationDescriptor> fire(EObject sourceElement, List<TranslationDescriptor> generatedElements) throws Exception {
		ScxmlDataType scxml = (ScxmlDataType)sourceElement;
		String sname = Strings.LOCATION(scxml);
		CarrierSet carrierSet = (CarrierSet) Make.set(sname, "generated from DataItem");
		context.getSets().add(carrierSet);
		
		//TODO: the expression could be an enumeration list.. but then we need to parse the list, generate a constant for each and add a partition
//		String value = scxml.getExpr();
//		if (value!=null && !"".equals(value)) {
//			Axiom valueAxiom = (Axiom) Make.axiom(sname+"_value", Strings.VALUE_PREDICATE(scxml), "generated from DataItem");
//			context.getAxioms().add(valueAxiom);
//		}
		
		return Collections.emptyList();
	}
	
	
	private boolean isOwnedByState(EObject scxml){
		EObject owner = scxml.eContainer();
		if (owner instanceof ScxmlStateType) return true;
		if (owner instanceof ScxmlDatamodelType) return isOwnedByState(owner);
		return false;
	}
	
}
