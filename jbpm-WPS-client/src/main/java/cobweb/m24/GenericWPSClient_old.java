package cobweb.m24;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.opengis.wps.x100.CapabilitiesDocument;
import net.opengis.wps.x100.ExecuteDocument;
import net.opengis.wps.x100.ExecuteResponseDocument;
import net.opengis.wps.x100.InputDescriptionType;
import net.opengis.wps.x100.OutputDescriptionType;
import net.opengis.wps.x100.ProcessBriefType;
import net.opengis.wps.x100.ProcessDescriptionType;

import org.drools.core.process.core.datatype.impl.type.StringDataType;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.feature.FeatureCollection;
import org.n52.wps.client.ExecuteResponseAnalyser;
import org.n52.wps.client.WPSClientException;
import org.n52.wps.client.WPSClientSession;
import org.n52.wps.commons.WPSConfig;
import org.n52.wps.io.data.GenericFileData;
import org.n52.wps.io.data.GenericFileDataWithGT;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.data.binding.complex.GTRasterDataBinding;
import org.n52.wps.io.data.binding.complex.GTVectorDataBinding;
import org.n52.wps.io.data.binding.complex.GenericFileDataBinding;
import org.n52.wps.io.data.binding.complex.GenericFileDataWithGTBinding;
import org.n52.wps.io.data.binding.complex.GeotiffBinding;
import org.n52.wps.io.data.binding.literal.*;
import org.n52.wps.io.data.binding.complex.PlainStringBinding;
public class GenericWPSClient_old {
	/**
	 * @author Sam Meek, Julian Rosser. This is the main class that executes the WPS and handles
	 *         the result returned as a HashMap. It is currently written to deal
	 *         with vector data. Raster data inputs are passed as a link and parsed server side.
	 * 		   Raster data outputs can be passed as links e.g. using executeBuilder.setAsReference(true).	  		   
	 *         Vector processing hardcoded to JSON (application/json) but could be changed
	 *         quite easily to implement the default.
	 *         Rough support for WPS4R R scripts where process id is like org.n52.wps.server.r.
	 */

	String wpsURL;
	String wpsProcessID;
	HashMap<String, Object> wpsInputs;
	HashMap<String, Object> outputs;
	Map<String, Object> wpsOutputs;
	String catalogURL;
	FeatureCollection featureCollection;
	FeatureCollection inputFeatureCollection;

	String tempDir;

	public GenericWPSClient_old(String wpsURL, String wpsProcessID,
			HashMap<String, Object> wpsInputs, String catalogURL) {

		this.wpsURL = wpsURL;
		this.wpsProcessID = wpsProcessID;
		this.wpsInputs = wpsInputs;
		this.catalogURL = catalogURL;

		System.out.println("WPS URL " + wpsURL);
		System.out.println("WPS Process ID " + wpsProcessID);
		/**
		 * this is a hard coded path for the Linux installation and should be
		 * passed as a variable
		 */
		// WPSConfig.getInstance("/usr/local/apache-tomcat-7.0.55/webapps/wps/config/wps_config_geotools.xml");
		WPSConfig
		.getInstance("C:\\Program Files\\Apache Software Foundation\\Tomcat 7.0\\webapps\\wps\\config\\wps_config_geotools.xml"); // For

		// tempDir = "/tmp/tomcat7-tomcat7-tmp/"; // linux
		tempDir = "C:\\tmp\\"; // windows

		try {
			CapabilitiesDocument capabilitiesDocument = requestGetCapabilities(wpsURL); //Doesn't appear to be used
			ProcessDescriptionType describeProcessDocument = requestDescribeProcess(wpsURL, wpsProcessID);

			//outputs = executeProcess(wpsURL, wpsProcessID,	describeProcessDocument, wpsInputs);
			outputs = executeProcessAsLinks(wpsURL, wpsProcessID,	describeProcessDocument, wpsInputs);

		} catch (WPSClientException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * @param url
	 *            - WPS url
	 * @return capabilities - the capabilities document of the WPS
	 * @throws WPSClientException
	 *             - a 52 North class to handle WPS exceptions
	 */

	public CapabilitiesDocument requestGetCapabilities(String url) throws WPSClientException {
		System.out.println("Requesting get capabilities document...");
		WPSClientSession wpsClient = WPSClientSession.getInstance();
		wpsClient.connect(url);
		CapabilitiesDocument capabilities = wpsClient.getWPSCaps(url);
		ProcessBriefType[] processList = capabilities.getCapabilities()
				.getProcessOfferings().getProcessArray();
		//For debugging...
		for (ProcessBriefType process : processList) {
			// System.out.println(process.getIdentifier().getStringValue());
		}
		return capabilities;
	}


	public ProcessDescriptionType requestDescribeProcess(String url,String processID) throws IOException {
		System.out.println("Requesting describe process document...");		
		WPSClientSession wpsClient = WPSClientSession.getInstance();
		ProcessDescriptionType processDescription = wpsClient.getProcessDescription(url, processID);
		InputDescriptionType[] inputList = processDescription.getDataInputs().getInputArray();
		for (InputDescriptionType input : inputList) {
			System.out.println("Describe process identifier: " + input.getIdentifier().getStringValue());
		}
		return processDescription;
	}
	
		
	/**
	 * 
	 * @param url
	 *            - WPS url
	 * @param processID
	 *            - process description
	 * @return outputs - hashmap of the results (as a URL links to data (e.g. not FeatureCollections)
	 * @throws IOException
	 *             - this needs replacing
	 */
	public HashMap<String, Object> executeProcessAsLinks(String url, String processID,	ProcessDescriptionType processDescription,	HashMap<String, Object> inputs) {
		org.n52.wps.client.ExecuteRequestBuilder executeBuilder = new org.n52.wps.client.ExecuteRequestBuilder(processDescription);
		System.out.println("Trying to execute process...");
		HashMap<String, Object> result = new HashMap<String, Object>();
		
		//Loop over the inputs (data and params) and create the ExectuteRequest   
		for (InputDescriptionType input : processDescription.getDataInputs().getInputArray()) {
			String inputName = input.getIdentifier().getStringValue();
			Object inputValue = inputs.get(inputName);
			
			//Handle literal data
			if (input.getLiteralData() != null) {
				System.out.println("WPS URL " + wpsURL);
				if (inputValue instanceof String) {
					executeBuilder.addLiteralData(inputName, (String) inputValue);
				}
			
			//Handle as ComplexData ie vectors, rasters
			} else if (input.getComplexData() != null) {		
				System.out.println("Generic WPS Client HERE 3 " + inputName	+ " " + inputValue + " ");											
				// System.out.println("Here 4 " + inputValue.toString());
				// Complexdata by value
				
										
				if (inputValue instanceof FeatureCollection	|| inputValue instanceof GTVectorDataBinding) {
					System.out.println("instance of FeatureCollection || ObjectDataType " + inputName);
					// IData data = new GTVectorDataBinding(
					// (FeatureCollection) inputValue);
					IData data = (IData) inputValue;
					try {
						executeBuilder.addComplexData((String) inputName, data,	null, null, "application/json");
					} catch (WPSClientException e) {
						System.out.println("add complex data exception " + e);
						e.printStackTrace();
					}
				}
				if (inputName.equals("inputSurfaceModel")) {
					//Hard-coded handling for .asc type inputs
					System.out.println("got an inputSurfaceModel");					
					executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "text/plain");
				} else if (inputName.equals("inputRasterModel")) {
					//Hard-coded handling of raster data inputs e.g. GeoTiff
					System.out.println("got an inputRasterModel");									
					//IData tempGenericData = (IData) inputValue;
					//Object tempInputValue = ((GenericFileDataBinding) inputValue);
					if (inputValue instanceof String) {
						System.out.println("Handling inputRasterModel as a string (i.e. URL of a raster)");	
						System.out.println("inputValue: " + inputValue);	
						executeBuilder.addComplexDataReference(inputName, (String) inputValue, null, null, "image/tiff");
					
					} else if (inputValue instanceof GTRasterDataBinding) {
						//Currently doesn't correctly create a tif that can be read on server side by WPS4R  
						System.out.println("Handle as raster binding");						
						//executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "image/tiff"); //Won't work cos of the string cast					
                                 
						//IData tempIData = (GTRasterDataBinding) inputValue;
						GTRasterDataBinding tempF =  ((GTRasterDataBinding) inputValue);

						System.out.println("going to get raster as payload");
						//GridCoverage2D dataPayload = tempF.getPayload(); 
			            //IData data = new GTRasterDataBinding((GridCoverage2D) inputValue); // Hangs on this, not sure this creates a valid object	             						
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempF, null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 

					} else if (inputValue instanceof  GenericFileDataWithGTBinding)  { 
						System.out.println("Handle as a GenericFileDataWithGTBinding");
						//Currently doesn't correctly create a tif that can be read on server side by WPS4R  
						System.out.println(inputValue.toString());
						System.out.println(inputValue.getClass());
						IData tempIData = (IData) inputValue;
						GenericFileDataWithGTBinding tempGenericData= (GenericFileDataWithGTBinding) tempIData; 

						System.out.println("going to get as payload");
						GenericFileDataWithGT dataPayload = tempGenericData.getPayload(); 
						System.out.println("going to get file extension");
						System.out.println(dataPayload.getFileExtension());

						System.out.println("going to get file mimeType");
						System.out.println(dataPayload.getMimeType());

						System.out.println("going to get file inputStream");
						InputStream inputStream = dataPayload.getDataStream();
						//System.out.println(baseFile.);

						System.out.println("going to get base file");
						File baseFile = dataPayload.getBaseFile(true);

						System.out.println(baseFile.getAbsolutePath());

						File file = new File (baseFile.getAbsolutePath());

						InputStream image = null;
						try{
							image = new BufferedInputStream(new FileInputStream(file));                        	
							//image = new FileInputStream(file);                      
						}
						catch (Exception e){
							System.out.println("File read in exception " + e);
						}	                        
				
						//IData tempIDataPayload = (IData) tempIData.getPayload();
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempIData , null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 

					} else {											
						System.out.println("Should handle as raster Generic GT binding");						
						//executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "image/tiff"); //Won't work cos of the string cast						
						IData tempIData = (IData) inputValue;
						//IData tempIDataPayload = (IData) tempIData.getPayload();
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempIData, null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
					}
				}
				else if (inputValue instanceof String) {
					System.out.println("instance of string. inputName: " + inputName);	
					executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null,"application/json");					
				}

			}
			if (inputValue == null && input.getMinOccurs().intValue() > 0) {
				System.out.println("Null inputValue for a mandatory field.");	
				try {
					throw new IOException("Property not set, but mandatory: "
							+ inputName);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		System.out.println("Finished constructing execute request inputs");				
		
		//Loop over process outputs to determine what output types should be requested
		for (OutputDescriptionType output : processDescription.getProcessOutputs().getOutputArray()) {
			System.out.println("Looping over output types to hardcode schema");	
			String outputName = output.getIdentifier().getStringValue();						
			if (output.getComplexOutput() != null) {				
				System.out.println("Setting schema for output: " + outputName);				
				
				System.out.println("output type " + outputName);
				
				//Why not check output data type?
				if (outputName.equals("outputRasterModel")) {
					System.out.println("Setting schema for an outputRasterModel: " + outputName);
					executeBuilder.setSchemaForOutput("image/tiff",	outputName);
					
					executeBuilder.setAsReference(outputName, true); //set the return output value as a reference
				} else if (processID.contains("org.n52.wps.server.r")) { 
					System.out.println("Setting output mime type for R process: " + outputName);
					executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0", outputName); 
					executeBuilder.setSchemaForOutput("http://schemas.opengis.net/gml/3.1.0/base/feature.xsd", outputName);
					
					executeBuilder.setAsReference(outputName, true); //set the return output value as a reference
				} else {
					System.out.println("Setting schema for: " + outputName);
					executeBuilder.setSchemaForOutput("application/json",outputName);
					executeBuilder.setAsReference(outputName, true); //set the return output value as a reference		
					
					
					/*
					executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0", "result"); 
					executeBuilder.setSchemaForOutput("http://schemas.opengis.net/gml/3.1.0/base/feature.xsd", "result");					  							  
					executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0", "qual_result"); 
					executeBuilder.setSchemaForOutput("http://schemas.opengis.net/gml/3.1.0/base/feature.xsd", "qual_result");
					*/
					
					
					/*
		
					
					String mimeType = output.getComplexOutput().getSupported().getFormatArray (1).getMimeType();
					executeBuilder.setMimeTypeForOutput(mimeType, outputName);
					  
					String schema = output.getComplexOutput().getSupported().getFormatArray(1).getSchema(); 
					if(schema!=null){					  
						executeBuilder.setSchemaForOutput( schema, outputName); 
					}								
					//System.out.println("outputName " + outputName + " mimeType " + mimeType + " schema " + schema);			  		
					 */						 
							 
				}
			} else if (output.getLiteralOutput() != null) {
				System.out.println("Warning: got literal output but not handling it!");
			}
		}

		// executeBuilder.setMimeTypeForOutput("text/plain", "metadata");

		ExecuteDocument execute = executeBuilder.getExecute();
		
		dumpTextToFile(execute.toString(), processID);
		
		execute.getExecute().setService("WPS");
		WPSClientSession wpsClient = WPSClientSession.getInstance();

		Object responseObject;
		try {
			responseObject = wpsClient.execute(url, execute);
			//System.out.println("printing execute request...");
			//System.out.println(execute.toString());
			

			if (responseObject instanceof ExecuteResponseDocument) {
				System.out.println("Handling responseObject as instanceof ExecuteResponseDocument");
				ExecuteResponseDocument response = (ExecuteResponseDocument) responseObject;
				ExecuteResponseAnalyser analyser = new ExecuteResponseAnalyser( execute, response, processDescription);
				System.out.println("HERE 6");
				System.out.println("Extracting outputs from process description...");
				System.out.println("Process Outputs Array size: " + processDescription.getProcessOutputs().getOutputArray().length);				

				for (OutputDescriptionType output : processDescription.getProcessOutputs().getOutputArray()) {
					System.out.println("getting an ouputIdentifier..."); 
					String outputName = output.getIdentifier().getStringValue();
					System.out.println("ouputIdentifier: "+ outputName);
					System.out.println("ouputIdentifierToString: " + output.toString());									
					System.out.println("ouput Class: "+ output.getClass());			
					System.out.println("ouput Identifier Class (toString): "+ output.getIdentifier().getClass().toString());					 
					try {
						System.out.println("Attempting to resolve output format");

						//Check if output raster or vector data
						if (outputName.equals("outputRasterModel")) {
							System.out.println("Handling output as raster");
							//handling raster outputs
							
							dumpTextToFile(responseObject.toString(), processID + " outputRasterModel response");							
							//Object outputValue = analyser.getComplexData(outputName,GeotiffBinding.class); //Doesn't work, no suitable parser
														
							//Handling raster as a string link, as might be returned by WPS execute request return by reference 
							//Note, a "only whitespace content allowed" compilation error indicates problems with the definition of data types
							//between the input / output variables e.g. as defined in CustomWorkItem work item doc.
							Object outputValue2 = analyser.getComplexReferenceByIndex(0); 
							String outputValue =(String) outputValue2;								
							System.out.println("Raster Output, outputValue2: " + outputValue.toString());							
							if (outputValue != null && outputValue instanceof String) {
								System.out.println("Raster Output, string location resolved");
								System.out.println("Raster Output, reference: " + analyser.getComplexReferenceByIndex(0));
								result.put(outputName, outputValue);								
							} else{
								System.out.println("Getting raster file reference not successful");
							}							

						} else {
							//Handling vector output
							System.out.println("Handling output as vector or literal");													
							//Check if R extra data is 
							if (outputName.equals("sessionInfo") || outputName.equals("warnings")) {
								System.out.println("skipping R outputs");			
							} else {
								System.out.println("checking for literal or vector string link");
								if (output.getLiteralOutput() != null) {
									System.out.println("Assumming literalOutput");
									Object literalOutput = output.getLiteralOutput();
									result.put(outputName, literalOutput);
								} else {									
									System.out.println("Handling as a string link");
									
									dumpTextToFile(responseObject.toString(), processID + " vector response");							
									//Handling vector as a string link, as might be returned by WPS execute request return by reference 
									//Note, a "only whitespace content allowed" compilation error indicates problems with the definition of data types
									//between the input / output variables e.g. as defined in CustomWorkItem work item doc.
									Object outputValue2 = analyser.getComplexReferenceByIndex(0); 
									String outputValue = (String) outputValue2;								
									System.out.println("Vector Output, outputValue2: " + outputValue.toString());							
									if (outputValue != null && outputValue instanceof String) {
										System.out.println("Vector Output, string location resolved");
										System.out.println("Vector Output, reference: " + analyser.getComplexReferenceByIndex(0));
										result.put(outputName, outputValue);								
									} else {
										System.out.println("Getting vector file reference not successful");
									} 									
								}													
			
							}
						}					

					} catch (NullPointerException e) {
						System.out.println("Output " + outputName
								+ " contains no results");
					}
				}
				System.out.println("Completed loop of Outputs Array");		
			} else {
				System.out.println("responseObject not an instanceof ExecuteResponseDocument");
				System.out.println("Dumping responseObject to file...");
				dumpTextToFile(responseObject.toString(), processID + " response");
			}
		} catch (WPSClientException e1) {
			System.out.println("error generating response object " + e1);
			e1.printStackTrace();
		}

		System.out.println("result collection size " + result.size());


		System.out.println("Printing hashmap...");
		Iterator iterator = result.keySet().iterator();		  
		while (iterator.hasNext()) {
			String key = iterator.next().toString();
			String value = result.get(key).toString();

			System.out.println("HASHMAP: " + key + " " + value);
		}

		return result;
	}


	
	
	
	/**
	 * 
	 * @param url
	 *            - WPS url
	 * @param processID
	 *            - process description ID
	 * @param processDescription
	 *            - process description information
	 * @param inputs
	 *            - the inputs for this WPS task (i.e. data, data links, parameters)  
	 * @return outputs - hashmap of the results (containing url links, data or FeatureCollection of data)
	 */	
	public HashMap<String, Object> executeProcess(String url, String processID,	ProcessDescriptionType processDescription,	HashMap<String, Object> inputs) {
		org.n52.wps.client.ExecuteRequestBuilder executeBuilder = new org.n52.wps.client.ExecuteRequestBuilder(processDescription);

		System.out.println("Trying to execute process...");
		HashMap<String, Object> result = new HashMap<String, Object>();
		
		//Loop over the inputs (data and params) and create the ExectuteRequest   
		for (InputDescriptionType input : processDescription.getDataInputs().getInputArray()) {
			String inputName = input.getIdentifier().getStringValue();
			Object inputValue = inputs.get(inputName);
			
			//Handle literal data
			if (input.getLiteralData() != null) {
				System.out.println("WPS URL " + wpsURL);
				if (inputValue instanceof String) {
					executeBuilder.addLiteralData(inputName, (String) inputValue);
				}
			
			//Handle as ComplexData ie vectors, rasters
			} else if (input.getComplexData() != null) {		
				System.out.println("Generic WPS Client HERE 3 " + inputName	+ " " + inputValue + " ");
				// System.out.println("Here 4 " + inputValue.toString());
				// Complexdata by value
				if (inputValue instanceof FeatureCollection	|| inputValue instanceof GTVectorDataBinding) {
					System.out.println("instance of FeatureCollection || ObjectDataType " + inputName);
					// IData data = new GTVectorDataBinding(
					// (FeatureCollection) inputValue);
					IData data = (IData) inputValue;
					try {
						executeBuilder.addComplexData((String) inputName, data,	null, null, "application/json");
					} catch (WPSClientException e) {
						System.out.println("add complex data exception " + e);
						e.printStackTrace();
					}
				}
				if (inputName.equals("inputSurfaceModel")) {
					//Hard-coded handling for .asc type inputs
					System.out.println("got an inputSurfaceModel");					
					executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "text/plain");
				} 
				if (inputName.equals("inputRasterModel")) {
					//Hard-coded handling of raster data inputs e.g. GeoTiff
					System.out.println("got an inputRasterModel");									
					//IData tempGenericData = (IData) inputValue;
					//Object tempInputValue = ((GenericFileDataBinding) inputValue);
					if (inputValue instanceof String) {
						System.out.println("Handling inputRasterModel as a string (i.e. URL of a raster)");	
						System.out.println("inputValue: " + inputValue);	
						executeBuilder.addComplexDataReference(inputName, (String) inputValue, null, null, "image/tiff");
					
					} else if (inputValue instanceof GTRasterDataBinding) {
						//Currently doesn't correctly create a tif that can be read on server side by WPS4R  
						System.out.println("Handle as raster binding");						
						//executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "image/tiff"); //Won't work cos of the string cast					
                                 
						//IData tempIData = (GTRasterDataBinding) inputValue;
						GTRasterDataBinding tempF =  ((GTRasterDataBinding) inputValue);

						System.out.println("going to get raster as payload");
						//GridCoverage2D dataPayload = tempF.getPayload(); 
			            //IData data = new GTRasterDataBinding((GridCoverage2D) inputValue); // Hangs on this, not sure this creates a valid object	             						
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempF, null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 

					} else if (inputValue instanceof  GenericFileDataWithGTBinding)  { 
						System.out.println("Handle as a GenericFileDataWithGTBinding");
						//Currently doesn't correctly create a tif that can be read on server side by WPS4R  
						System.out.println(inputValue.toString());
						System.out.println(inputValue.getClass());
						IData tempIData = (IData) inputValue;
						GenericFileDataWithGTBinding tempGenericData= (GenericFileDataWithGTBinding) tempIData; 

						System.out.println("going to get as payload");
						GenericFileDataWithGT dataPayload = tempGenericData.getPayload(); 
						System.out.println("going to get file extension");
						System.out.println(dataPayload.getFileExtension());

						System.out.println("going to get file mimeType");
						System.out.println(dataPayload.getMimeType());

						System.out.println("going to get file inputStream");
						InputStream inputStream = dataPayload.getDataStream();
						//System.out.println(baseFile.);

						System.out.println("going to get base file");
						File baseFile = dataPayload.getBaseFile(true);

						System.out.println(baseFile.getAbsolutePath());

						File file = new File (baseFile.getAbsolutePath());

						InputStream image = null;
						try{

							image = new BufferedInputStream(new FileInputStream(file));                        	
							//image = new FileInputStream(file);                      

						}
						catch (Exception e){
							System.out.println("File read in exception " + e);
						}	                        
				
						//IData tempIDataPayload = (IData) tempIData.getPayload();
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempIData , null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 

					} else {											
						System.out.println("Should handle as raster Generic GT binding");						
						//executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null, "image/tiff"); //Won't work cos of the string cast						
						IData tempIData = (IData) inputValue;
						//IData tempIDataPayload = (IData) tempIData.getPayload();
						try {
							System.out.println("adding data into execute request");	
							executeBuilder.addComplexData(inputName,tempIData, null, null, "image/tiff");
						} catch (WPSClientException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
					}
				}
				else if (inputValue instanceof String) {
					System.out.println("instance of string " + inputName);	
					executeBuilder.addComplexDataReference(inputName,(String) inputValue, null, null,"application/json");
				}

			}
			if (inputValue == null && input.getMinOccurs().intValue() > 0) {
				System.out.println("Null inputValue for a mandatory field.");	
				try {
					throw new IOException("Property not set, but mandatory: "
							+ inputName);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		System.out.println("Finished constructing execute request inputs");		
		
		
		//Loop over process outputs to determine what output types should be requested
		for (OutputDescriptionType output : processDescription.getProcessOutputs().getOutputArray()) {
			System.out.println("Looping over output types to hardcode schema");	
			String outputName = output.getIdentifier().getStringValue();						
			if (output.getComplexOutput() != null) {				
				System.out.println("Setting schema for output: " + outputName);				
				
				//Why not check output data type?
				if (outputName.equals("outputRasterModel")) {		
					System.out.println("Setting schema for an outputRasterModel: " + outputName);
					executeBuilder.setSchemaForOutput("image/tiff",	outputName);
					executeBuilder.setAsReference(outputName, true); //set the return output value as a reference
				} else if (processID.contains("org.n52.wps.server.r")) { 
					System.out.println("Setting output mime type for R process: " + outputName);
					executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0", outputName); 
					executeBuilder.setSchemaForOutput("http://schemas.opengis.net/gml/3.1.0/base/feature.xsd", outputName);					
				}else {
					System.out.println("Setting schema for: " + outputName);
					executeBuilder.setSchemaForOutput("application/json",outputName);
				}


				/**
				 * String mimeType =
				 * output.getComplexOutput().getSupported().getFormatArray
				 * (1).getMimeType();
				 * executeBuilder.setMimeTypeForOutput(mimeType, outputName);
				 * 
				 * String schema =
				 * output.getComplexOutput().getSupported().getFormatArray
				 * (1).getSchema(); if(schema!=null){
				 * 
				 * executeBuilder.setSchemaForOutput( schema, outputName) }
				 * System.out.println("outputName " + outputName + " mimeType "
				 * + mimeType + " schema " + schema);
				 * 
				 * }
				 **/
			}

			else if (output.getLiteralOutput() != null) {

			}
		}

		/**
		 * executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0",
		 * "result"); executeBuilder.setSchemaForOutput(
		 * "http://schemas.opengis.net/gml/3.1.0/base/feature.xsd", "result");
		 * 
		 * executeBuilder.setMimeTypeForOutput("text/xml; subtype=gml/3.1.0",
		 * "qual_result"); executeBuilder.setSchemaForOutput(
		 * "http://schemas.opengis.net/gml/3.1.0/base/feature.xsd",
		 * "qual_result");
		 **/

		// executeBuilder.setMimeTypeForOutput("text/plain", "metadata");



		ExecuteDocument execute = executeBuilder.getExecute();
		execute.getExecute().setService("WPS");
		WPSClientSession wpsClient = WPSClientSession.getInstance();

		Object responseObject;
		try {
			dumpTextToFile(execute.toString(), processID);
			responseObject = wpsClient.execute(url, execute);
			//System.out.println("printing execute request...");
			//System.out.println(execute.toString());

			if (responseObject instanceof ExecuteResponseDocument) {
				ExecuteResponseDocument response = (ExecuteResponseDocument) responseObject;
				ExecuteResponseAnalyser analyser = new ExecuteResponseAnalyser( execute, response, processDescription);
				System.out.println("HERE 6");
				System.out.println("Extracting outputs from process description...");
				System.out.println("Process Outputs Array size: " + processDescription.getProcessOutputs().getOutputArray().length);				

				for (OutputDescriptionType output : processDescription.getProcessOutputs().getOutputArray()) {
					System.out.println("getting an ouputIdentifier..."); 
					String outputName = output.getIdentifier().getStringValue();
					System.out.println("ouputIdentifier: "+ outputName);
					System.out.println("ouputIdentifierToString: " + output.toString());									
					System.out.println("ouput Class: "+ output.getClass());			
					System.out.println("ouput Identifier Class (toString): "+ output.getIdentifier().getClass().toString());					 
					try {
						System.out.println("Attempting to resolve output format");

						//Check if output raster or vector data
						if (outputName.equals("outputRasterModel")) {
							System.out.println("Handling output as raster");
							//handling raster outputs
							
							dumpTextToFile(responseObject.toString(), processID + " outputRasterModel response");							
							//Object outputValue = analyser.getComplexData(outputName,GeotiffBinding.class); //Doesn't work, no suitable parser
														
							//Handling raster as a string link, as might be returned by WPS execute request return by reference 
							//Note, a "only whitespace content allowed" compilation error indicates problems with the definition of data types
							//between the input / output variables e.g. as defined in CustomWorkItem work item doc.
							Object outputValue2 = analyser.getComplexReferenceByIndex(0); 
							String outputValue =(String) outputValue2;								
							System.out.println("Raster Output, outputValue2: " + outputValue.toString());							
							if (outputValue != null && outputValue instanceof String) {
								System.out.println("Raster Output, string location resolved");
								System.out.println("Raster Output, reference: " + analyser.getComplexReferenceByIndex(0));
								result.put(outputName, outputValue);								
							} else{
								System.out.println("Getting raster file reference not successful");
							}
							
																					
							//Handle raster outputs as raw data. Currently raster data can't be 
							//correctly formed into a geotiff by the WPS client.
							//This may be a bug in 52N. 
							/*
							Object outputValue = analyser.get.getComplexData(outputName,GTRasterDataBinding.class); //Does parse with Tiffparser when returning reference
							System.out.println("Raster Output, outputName: " + outputName);
							GridCoverage2D tempF =  ((GTRasterDataBinding) outputValue).getPayload();
							System.out.println("Raster Output, created GridCoverage2d: ");
							if (outputValue != null) {
								System.out.println("Raster Output, parsed resolved");
								System.out.println("Raster Output, value: " + outputValue.toString());
								result.put(outputName, outputValue);			
							} else{
								System.out.println("GridCoverage creation not successful");
							}
							*/								
							//Handle raster as generic file with GT. Same client issue
							//as above - can't reconstruct the raw file data into a tiff that is recognised when passed back to server 		
							/*
							Object outputValue = analyser.getComplexData(outputName,GenericFileDataWithGTBinding.class); //Does parse with Tiffparser							
							System.out.println("Raster Generic Output, outputName: " + outputName);
							if (outputValue != null) {
								System.out.println("Raster Generic Output, parsed resolved");
								System.out.println("Raster Generic  Output, value: " + outputValue.toString());
								result.put(outputName, outputValue);								
							} else{
								System.out.println("Generic creation not successful");
							}
							 */													
							 
						} else {			
							System.out.println("Handling output as vector or literal");
							//Handling vector output							
							if (outputName.equals("sessionInfo") || outputName.equals("warnings")) {
								System.out.println("skipping R outputs");
							} else {
								System.out.println("Assumming GTVectorDataBinding");
								Object outputValue = analyser.getComplexData(outputName, GTVectorDataBinding.class);
								
								
								System.out.println("HERE 7 " + outputName + " "	+ outputValue);
								FeatureCollection tempF = ((GTVectorDataBinding) outputValue).getPayload();

								if (outputValue != null && outputValue instanceof GTVectorDataBinding) {
									System.out.println("HERE 8 output name "
											+ outputName + " outputValue size "
											+ tempF.size());
									result.put(outputName, outputValue);
								}
								else if (output.getLiteralOutput() != null) {
									System.out.println("Assumming literalOutput");
									Object literalOutput = output.getLiteralOutput();
									result.put(outputName, literalOutput);
								}
							}
						}					

					} catch (NullPointerException e) {
						System.out.println("Output " + outputName
								+ " contains no results");
					}
				}
				System.out.println("Completed loop of Outputs Array");		
			} else {
				System.out.println("responseObject not an instanceof ExecuteResponseDocument");
				System.out.println("Dumping responseObject to file...");
				dumpTextToFile(responseObject.toString(), processID + " response");
			}
		} catch (WPSClientException e1) {
			System.out.println("error generating response object " + e1);
			e1.printStackTrace();
		}

		System.out.println("result collection size " + result.size());


		System.out.println("Printing hashmap...");
		Iterator iterator = result.keySet().iterator();		  
		while (iterator.hasNext()) {
			String key = iterator.next().toString();
			String value = result.get(key).toString();

			System.out.println("HASHMAP: " + key + " " + value);
		}

		return result;
	}


	//Write data to a file for debugging of requests and responses
	private void dumpTextToFile(String textToDump, String outputName) {
		try {	        
			Date date = new Date() ;
			UUID uuid = UUID.randomUUID();
			String randomUUIDString = uuid.toString();

			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss") ;
			File file = new File(tempDir + dateFormat.format(date) +"_" + randomUUIDString +"_"+ outputName +".xml") ;
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			out.write(textToDump);
			out.close();
		} catch (IOException iox) {
			//do stuff with exception
			iox.printStackTrace();
		}
	}


	public HashMap<String, Object> getOutputs() {
		return outputs;
	}

	/**
	 * 
	 * @param xmlGenericData
	 * @return this needs to be implemented This method is partially implemented
	 *         to deal with metadata documents for GeoNetwork however, this was
	 *         somewhat abandoned when it was decided that metadata should be
	 *         tightly coupled to the features.
	 */

	private File parseXMLFromWPS(GenericFileData xmlGenericData) {
		File file = xmlGenericData.getBaseFile(true);
		InputStream fis;
		try {
			fis = new FileInputStream(file);
			BufferedReader br = new BufferedReader(new InputStreamReader(fis));

			File newFile = File.createTempFile("temp2", "xml");

			FileWriter fw = new FileWriter(newFile);

			for (String line = br.readLine(); line != null; line = br
					.readLine()) {
				String newLine = line.replaceAll("&gt;", ">")
						.replaceAll("&lt;", "<").replaceAll("&amp;", "&");
				System.out.println("NEWLINE " + newLine);
				fw.write(newLine);

			}
			fw.close();
			fis.close();
			return newFile;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
