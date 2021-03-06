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
package ac.soton.scxml.eventb;

import java.util.Collections;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eventb.emf.core.EventBNamedCommentedComponentElement;

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
	 * The components generated by the SCXML generator should still be in the resource set of the editing domain
	 * 
	 * (Note, in theory it should be possible to reload the resources from the EMFRodinDB as it has the same resource set as the SCXML generation,
	 * however, this did not work and appeared to create new resources which were then overwritten by the final save of the SCXML generation)
	 * 
	 * @param sourceElement
	 * @param commandId
	 * @param monitor
	 * @throws CoreException 
	 */
	protected IStatus postProcessing(EObject sourceElement, String commandId, IProgressMonitor monitor) throws Exception {
		IStatus status = Status.OK_STATUS;
		if (sourceElement instanceof DocumentRoot){
			save(monitor);	//save the resources including the new machines/contexts generated from scxml (this is necessary for the UML-B translations below to work properly)
			
			//IProject project = WorkspaceSynchronizer.getFile(sourceElement.eResource()).getProject();
			//EMFRodinDB emfRodinDB = new EMFRodinDB(getEditingDomain());
			
			for (Resource r : getEditingDomain().getResourceSet().getResources()) {
				//reload the resources that were saved above (this is necessary for the UML-B translations below to work properly)
				r.unload();
				r.load(Collections.EMPTY_MAP);
				
				EObject eo = r.getContents().get(0);
				if (eo instanceof EventBNamedCommentedComponentElement) {
					EventBNamedCommentedComponentElement component = (EventBNamedCommentedComponentElement)eo;
					
//		//tried approach of saving between each generate but it does not work any better - save and reload above works better
//					generateDiagrams(component, monitor);
//					save(monitor);
					
					//translate all diagrams
					TranslateAllCommand translateAllCmd = new TranslateAllCommand(getEditingDomain(),component);
					if (translateAllCmd.canExecute()){
						try {
							status = translateAllCmd.execute(null, null);
						} catch (ExecutionException e) {
							e.printStackTrace();
							Activator.logError("Failed to generated elements: "+e.getMessage());					
						}
						if (!status.isOK()){
							String statusMessage = status.getMessage();
							for (IStatus childStatus : status.getChildren()){
								statusMessage = statusMessage+"\n"+childStatus.getMessage();
							}
							Activator.logError("Failed to generated elements: "+statusMessage);
						}else {
							save(monitor);
						}
						
					}else{
						// ignore - probably no diagram in that resource
						//status = Status.CANCEL_STATUS;
					}
				}
			}
			//Also tried doing this using jobs.. but they ran too quickly and also got overwritten by the final save of the scxml generator
//			List<EventBNamedCommentedComponentElement> components = emfRodinDB.loadAllComponents(project.getName());
//			for (EventBNamedCommentedComponentElement cp : components) {
//				ScheduleDiagramGeneration(getEventBRoot(cp));
//			}
		}		
		monitor.done();
		return status;
	}
	
	
	
//	/**
//	 * Generate all diagrams in a component
//	 * This must be done in a RodinCore runnable
//	 * 
//	 * @param monitor
//	 * @throws ExecutionException 
//	 */
//	protected void generateDiagrams(EventBNamedCommentedComponentElement component, IProgressMonitor monitor) {
//		// save all resources that have been modified	
//		final TranslateAllCommand translateAllCmd = new TranslateAllCommand(getEditingDomain(), component);
//		if (translateAllCmd.canExecute()){
//			try {
//				RodinCore.run(new IWorkspaceRunnable() {
//					public void run(final IProgressMonitor monitor) throws CoreException {
//						try {
//							translateAllCmd.execute(monitor, null);
//						} catch (ExecutionException e) {
//							Activator.logError("Failed to translate diagrams to Event-B: "+e.getMessage());
//							e.printStackTrace();
//						}
//					}
//				}, getEventBRoot(component).getRodinProject().getSchedulingRule()
//				, monitor);
//			} catch (RodinDBException e) {
//				Activator.logError("Failed to translate diagrams to Event-B: "+e.getMessage());				
//				e.printStackTrace();
//			}
//		}
//		monitor.done();
//	}
//	
//	
//	private static final String QUALIFIER = "ac.soton.scxml.eventb";
//	private static final QualifiedName COMPONENT_ROOT = new QualifiedName(QUALIFIER, "COMPONENT_ROOT");
//	
//	/**
//	 * Schedule a job to run after the refinement has completed. The job
//	 * will re-generate all diagrams in the component and then save the component
//	 * 
//	 * @param component
//	 */
//	
//	private static void ScheduleDiagramGeneration (IEventBRoot root  ) {
//		Job diagramUpdaterJob = new Job("Updating diagram references for new component name") {
//			public IStatus run(IProgressMonitor monitor) {
//				final EMFRodinDB emfRodinDB = new EMFRodinDB();
//		    	IStatus status = Status.OK_STATUS;
//				EventBNamedCommentedComponentElement component = 
//						(EventBNamedCommentedComponentElement) emfRodinDB.loadEventBComponent((IEventBRoot)getProperty(COMPONENT_ROOT));
//				
//				//translate all diagrams
//				TranslateAllCommand translateAllCmd = new TranslateAllCommand(emfRodinDB.getEditingDomain(),component);
//				if (translateAllCmd.canExecute()){
//					try {
//						status = translateAllCmd.execute(null, null);
//					} catch (ExecutionException e) {
//						e.printStackTrace();
//						Activator.logError("Failed to generated elements: "+e.getMessage());					
//					}
//					if (status.isOK()){
//						try {
//							// save all resources that have been modified
//							SaveResourcesCommand saveCommand = new SaveResourcesCommand(emfRodinDB.getEditingDomain());
//							if (saveCommand.canExecute()){
//									status = saveCommand.execute(monitor, null);
//							}
//						} catch (Exception e) {
//							String statusMessage = status.getMessage();
//							for (IStatus childStatus : status.getChildren()){
//								statusMessage = statusMessage+"\n"+childStatus.getMessage();
//							}
//							Activator.logError("Failed to save elements: "+statusMessage);
//						}
//					}else{
//						String statusMessage = status.getMessage();
//						for (IStatus childStatus : status.getChildren()){
//							statusMessage = statusMessage+"\n"+childStatus.getMessage();
//						}
//						Activator.logError("Failed to generated elements: "+statusMessage);
//					}
//					
//				}else{
//					status = Status.CANCEL_STATUS;
//				}
//		        return status;
//		      }
//		   };
//		diagramUpdaterJob.setRule(root.getRodinProject().getSchedulingRule());
//		diagramUpdaterJob.setPriority(Job.LONG);  // low priority
//		diagramUpdaterJob.setProperty(COMPONENT_ROOT, root);				
//		diagramUpdaterJob.schedule();
//	}
//	
//	
//	private static IEventBRoot getEventBRoot(EventBElement element) {
//		Resource resource = element.eResource();
//		if (resource != null && resource.isLoaded()) {
//			IFile file = WorkspaceSynchronizer.getFile(resource);
//			IRodinProject rodinProject = RodinCore.getRodinDB()
//					.getRodinProject(file.getProject().getName());
//			IEventBRoot root = (IEventBRoot) rodinProject.getRodinFile(
//					file.getName()).getRoot();
//			return root;
//		}
//		return null;
//	}
//	
}
