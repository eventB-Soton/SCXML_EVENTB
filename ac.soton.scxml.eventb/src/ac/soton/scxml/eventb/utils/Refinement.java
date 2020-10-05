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

import org.eventb.emf.core.machine.Machine;

import ac.soton.eventb.statemachines.AbstractNode;
import ac.soton.eventb.statemachines.Statemachine;

/**
 * <p>
 * record structure for data related to refinement levels in the scxml translation
 * </p>
 * 
 * @author cfs
 * @version
 * @see
 * @since
 */
public class Refinement {

		public int level = 0;
		public Machine machine = null;
		public Statemachine statemachine = null;
		public AbstractNode source = null;
		public AbstractNode target = null;	
	
}
