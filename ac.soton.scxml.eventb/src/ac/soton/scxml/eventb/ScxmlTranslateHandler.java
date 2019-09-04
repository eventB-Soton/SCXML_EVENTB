/*******************************************************************************
 *  Copyright (c) 2016-2019 University of Southampton.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *   
 *  Contributors:
 *  University of Southampton - Initial implementation
 *******************************************************************************/
package ac.soton.scxml.eventb;

import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.workspace.util.WorkspaceSynchronizer;
import org.eventb.core.IEventBRoot;
import org.eventb.emf.core.EventBElement;
import org.eventb.emf.core.EventBNamedCommentedComponentElement;
import org.eventb.emf.persistence.EMFRodinDB;
import org.eventb.emf.persistence.SaveResourcesCommand;
import org.rodinp.core.IRodinProject;
import org.rodinp.core.RodinCore;

import ac.soton.emf.translator.eventb.handler.EventBTranslateHandler;
import ac.soton.eventb.emf.diagrams.generator.commands.TranslateAllCommand;
import ac.soton.scxml.DocumentRoot;

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
public class ScxmlTranslateHandler extends EventBTranslateHandler {	
	
	/**
	 * This is overridden to schedule the iUML-B translators after SCXML translation has finished.
	 * The translations must be run in scheduled jobs
	 * 
	 * @param sourceElement
	 * @param commandId
	 * @param monitor
	 * @throws CoreException 
	 */
	protected IStatus postProcessing(EObject sourceElement, String commandId, IProgressMonitor monitor) throws Exception {
		IStatus status = Status.OK_STATUS;
		if (sourceElement instanceof DocumentRoot){
			IProject project = WorkspaceSynchronizer.getFile(sourceElement.eResource()).getProject();
			EMFRodinDB emfRodinDB = new EMFRodinDB(getEditingDomain());
			List<EventBNamedCommentedComponentElement> components = emfRodinDB.loadAllComponents(project.getName());
			for (EventBNamedCommentedComponentElement cp : components) {
				ScheduleDiagramGeneration(getEventBRoot(cp));
			}
		}		
		monitor.done();
		return status;
	}
	
	
	private static final String QUALIFIER = "ac.soton.scxml.eventb";
	private static final QualifiedName COMPONENT_ROOT = new QualifiedName(QUALIFIER, "COMPONENT_ROOT");
	
	/**
	 * Schedule a job to run after the refinement has completed. The job
	 * will re-generate all diagrams in the component and then save the component
	 * 
	 * @param component
	 */
	
	private static void ScheduleDiagramGeneration (IEventBRoot root  ) {
		Job diagramUpdaterJob = new Job("Updating diagram references for new component name") {
			public IStatus run(IProgressMonitor monitor) {
				final EMFRodinDB emfRodinDB = new EMFRodinDB();
		    	IStatus status = Status.OK_STATUS;
				EventBNamedCommentedComponentElement component = 
						(EventBNamedCommentedComponentElement) emfRodinDB.loadEventBComponent((IEventBRoot)getProperty(COMPONENT_ROOT));
				
				//translate all diagrams
				TranslateAllCommand translateAllCmd = new TranslateAllCommand(emfRodinDB.getEditingDomain(),component);
				if (translateAllCmd.canExecute()){
					try {
						status = translateAllCmd.execute(null, null);
					} catch (ExecutionException e) {
						e.printStackTrace();
						Activator.logError("Failed to generated elements: "+e.getMessage());					
					}
					if (status.isOK()){
						try {
							// save all resources that have been modified
							SaveResourcesCommand saveCommand = new SaveResourcesCommand(emfRodinDB.getEditingDomain());
							if (saveCommand.canExecute()){
									status = saveCommand.execute(monitor, null);
							}
						} catch (Exception e) {
							String statusMessage = status.getMessage();
							for (IStatus childStatus : status.getChildren()){
								statusMessage = statusMessage+"\n"+childStatus.getMessage();
							}
							Activator.logError("Failed to save elements: "+statusMessage);
						}
					}else{
						String statusMessage = status.getMessage();
						for (IStatus childStatus : status.getChildren()){
							statusMessage = statusMessage+"\n"+childStatus.getMessage();
						}
						Activator.logError("Failed to generated elements: "+statusMessage);
					}
					
				}else{
					status = Status.CANCEL_STATUS;
				}
		        return status;
		      }
		   };
		diagramUpdaterJob.setRule(root.getSchedulingRule());
		diagramUpdaterJob.setPriority(Job.LONG);  // low priority
		diagramUpdaterJob.setProperty(COMPONENT_ROOT, root);				
		diagramUpdaterJob.schedule();
	}
	
	
	private static IEventBRoot getEventBRoot(EventBElement element) {
		Resource resource = element.eResource();
		if (resource != null && resource.isLoaded()) {
			IFile file = WorkspaceSynchronizer.getFile(resource);
			IRodinProject rodinProject = RodinCore.getRodinDB()
					.getRodinProject(file.getProject().getName());
			IEventBRoot root = (IEventBRoot) rodinProject.getRodinFile(
					file.getName()).getRoot();
			return root;
		}
		return null;
	}
	
}
