package cobweb.m24;

import java.util.HashMap;

public class main {

	public static void main(String[] args) {
		
		String wpsURL = "http://localhost:8010/wps/WebProcessingService";
		String wpsProcessID = "pillar.authoritativedata.AuthoritativeDataComparison";
		String catalogURL = "http://localhost:8010/geonetwork";
		
		 String variable3 = "http://geoprocessing.demo.52north.org:8080/geoserver/wfs?SERVICE=WFS&VERSION=1.0.0&REQUEST=GetFeature&TYPENAME=topp:tasmania_cities&outputformat=gml3";
		 String variable4 = "http://geoprocessing.demo.52north.org:8080/geoserver/wfs?SERVICE=WFS&VERSION=1.0.0&REQUEST=GetFeature&TYPENAME=topp:tasmania_state_boundaries&outputformat=gml3";
		
		HashMap<String,Object> map = new HashMap<String,Object>();
		
		map.put("inputObservations", variable3);
		map.put("inputAuthoritativeData", variable4);
		
		AuthoritativeWPSClient client = new AuthoritativeWPSClient();
		// TODO Auto-generated method stub

	}

}
