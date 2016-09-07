package cobweb.m24;

import org.jbpm.bpmn2.handler.SignallingTaskHandlerDecorator;
import org.kie.api.KieBase;

import net.opengis.examples.packet.GMLPacketDocument;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.process.WorkItemHandler;
import org.n52.wps.io.datahandler.parser.GML3BasicParser;

public class LosProcessTest {
	/**
	 * @author Julian Rosser
	 * @param args
	 * 
	 *            Main class harness for testing LoS / JKW workflow
	 */

	public static void main(String args[]) {

		KieServices ks = KieServices.Factory.get();
		KieContainer kContainer = ks.getKieClasspathContainer();
		KieBase kbase = kContainer.getKieBase("kbase");
		KieSession ksession = kContainer.newKieSession("ksession-process");

		String eventType = "error-code";
		SignallingTaskHandlerDecorator signallingTaskWrapper = new SignallingTaskHandlerDecorator(
				GenericWorkItemHandlerClient.class, eventType);
		signallingTaskWrapper
		.setWorkItemExceptionParameterName(ExceptionServiceHandler.exceptionParameterName);

		
		ksession.getWorkItemManager().registerWorkItemHandler(
				"GetLineOfSight", signallingTaskWrapper);
		
		ksession.getWorkItemManager().registerWorkItemHandler(
				"LaplacePhotoBlurCheck", signallingTaskWrapper);
		
		ksession.getWorkItemManager().registerWorkItemHandler(
				"Pillar5ProximitySuitabilityPolygonScore", signallingTaskWrapper);				
		
		ksession.getWorkItemManager().registerWorkItemHandler(
				"AttributeRange", signallingTaskWrapper);
		
		
		/**
		 * use this to start a defined process, this can be found in
		 * /src/main/resources
		 */
		//ksession.startProcess("cobweb.m24.qaqc_knotweed_laplace");
		//ksession.startProcess("cobweb.m24.qaqc_knotweed_los_laplace");
		//ksession.startProcess("cobweb.m24.qaqc_knotweed_los_laplace_suitability");
		
		ksession.startProcess("cobweb.m24.qaqc_knotweed_los_filter_laplace_suitability");
		
	}

}