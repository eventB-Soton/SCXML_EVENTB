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
/**
 * 
 */
package ac.soton.scxml.eventb.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.FeatureMap;

/**
 * <p>
 * 
 * </p>
 * 
 * @author cfs
 * @version
 * @see
 * @since
 */
public class IumlbScxmlAdapter {
	
	  /**
	   * The adapted SCXML element.
	   */
	  protected EObject target = null;
	  
	  protected Integer refinementLevel = null;

	  protected String featureName = null;
	  
		 /**
	   * Creates an instance.
	   */
	  public IumlbScxmlAdapter()
	  {
	    //adapt(target);
	  }
	  
	 /**
	   * Creates an instance that adapts target eObject
	   */
	  public IumlbScxmlAdapter(EObject target)
	  {
	    adapt(target);
	  }

	  public IumlbScxmlAdapter(EObject target, String featureName) 
	  {
		  adapt(target);
		  this.featureName = featureName;
	  }

	/**
	   * makes this instance adapt the given target
	   * @param target
	   */
	  public IumlbScxmlAdapter adapt(EObject target){
		    this.target = target;
		    return this;
	  }
	  
	 /**
	  * returns the value of the attribute with the given name contained in
	  * the anyAttribute feature map...
	  * ... or null if no such attribute is found.
	  * @param attributeName
	  * @return attribute value object
	  */
	  
	  public Object getAnyAttributeValue(String attributeName){
			EStructuralFeature anyAttributeFeature  = target.eClass().getEStructuralFeature("anyAttribute");
			if (anyAttributeFeature==null) return null;
			FeatureMap fm = (FeatureMap) target.eGet(anyAttributeFeature);
			for (int i=0; i< fm.size(); i++){
				EStructuralFeature sf = fm.getEStructuralFeature(i);
				if (attributeName.equals(sf.getName())){
					return fm.getValue(i);
				}
			}
			return null;	  
	  }
	  
	 /**
	  * returns a list of adapters wrapping EObjects that are the value of the features with the given name contained in
	  * the any feature map...
	  * ... or an empty list if none are found.
	  * @param attributeName
	  * @return attribute value object
	  */
	  
	  public List<IumlbScxmlAdapter> getAnyChildren(String featureName){
			List<IumlbScxmlAdapter> ret = new ArrayList<IumlbScxmlAdapter>();
			EStructuralFeature anyFeature  = target.eClass().getEStructuralFeature("any");
			if (anyFeature==null) return ret;
			FeatureMap fm = (FeatureMap) target.eGet(anyFeature);
			for (int i=0; i< fm.size(); i++){
				EStructuralFeature sf = fm.getEStructuralFeature(i);
				if (featureName.equals(sf.getName()) && fm.getValue(i) instanceof EObject){
					ret.add(new IumlbScxmlAdapter((EObject)fm.getValue(i),featureName));
				}
			}
			return ret;	  
	  }
	  
	  
	
	/**
	 * Returns the refinement attribute value for this SCXML element
	 * if none, or the attribute string doesn't parse as an int, returns -1.
	 * 
	 * @param scxmlElement
	 * @return
	 */
	public int getBasicRefinementLevel(){		
		Object level = getAnyAttributeValue("refinement");
		if (level instanceof String) {
			try {
				return Integer.parseInt((String)level);
			}catch (Exception e){
				return -1;
			}
		}else{
			return -1;
		}
	}

	
	public List<IumlbScxmlAdapter> getinvariants() {		
		return getAnyChildren("invariant");
	}

	/**
	 * @return
	 */
	public List<IumlbScxmlAdapter> getGuards() {
		return getAnyChildren("guard");	
	}

	public boolean isVariable() {
		Object dataKind = getAnyAttributeValue("dataKind");
		return (!(dataKind instanceof String) ||  "Variable".equalsIgnoreCase(((String)dataKind).trim()));
	}
	
	public boolean isConstant() {
		Object dataKind = getAnyAttributeValue("dataKind");
		return (dataKind instanceof String && "Constant".equalsIgnoreCase(((String)dataKind).trim()));
	}
	
	public boolean isCarrierSet() {
		Object dataKind = getAnyAttributeValue("dataKind");
		return (dataKind instanceof String && "CarrierSet".equalsIgnoreCase(((String)dataKind).trim()));
	}

	public Integer getFinalised() {
		Object finalised = getAnyAttributeValue("finalised");
		if (finalised instanceof String) {
			try {
				return Integer.parseInt((String)finalised);
			}catch (Exception e){
				return -1;
			}
		}else{
			return -1;
		}
	}
	
}
