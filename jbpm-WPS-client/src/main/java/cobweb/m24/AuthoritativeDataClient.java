
package cobweb.m24;



import java.util.HashMap;
import java.util.Map;

import org.geotools.feature.FeatureCollection;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;


public class AuthoritativeDataClient implements WorkItemHandler{
	

		public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
			String wpsURL = (String) workItem.getParameter("wpsURL");
			String wpsProcessID = "pillar.authoritativedata.AuthoritativeDataComparison";
			String catalogURL = (String) workItem.getParameter("catalogURL");
			
			//FeatureCollection fc = (FeatureCollection) workItem.getParameter("wpsCarriedData");

			HashMap <String, Object> wpsInputs = (HashMap<String, Object>) workItem.getParameter("wpsInputs");
			
			GenericWPSClient wpsClient = new GenericWPSClient(wpsURL, wpsProcessID, wpsInputs, catalogURL);
			
			Map<String, Object> results = new HashMap<String,Object>();
		
			FeatureCollection fcOut = ((GTVectorDataBinding) wpsClient.getOutputs().get("qual_result")).getPayload();
			
			System.out.println("authoritative data output " + fcOut.toString());
			
			results.put("output", fcOut);
			
			//results.put("output", "SomeString");
			
			manager.completeWorkItem(workItem.getId(), results);
		}

		public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
			manager.abortWorkItem(workItem.getId());
			
		}
       

}