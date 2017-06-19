/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.pacsquery.web.controller;

import java.io.Writer;
import java.util.List;

import java.io.IOException;

import javax.servlet.http.HttpSession;

import javax.json.stream.JsonGenerator;
import javax.json.Json;

import org.dcm4che3.json.JSONWriter;

import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSP;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Status;

import org.dcm4che3.data.UID;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.Attributes;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;

//import java.nio.charset.StandardCharsets;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/pacsquery/pacsquery.form'.
 */
@Controller("pacsquery.PacsQueryController")
public class PacsQueryController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	@Autowired
	UserService userService;
	
	/** Success form view name */
	private final String VIEW = "/module/pacsquery/pacsquery";
	
	/**
	 * Initially called after the getUsers method to get the landing form name
	 * 
	 * @return String form view name
	 */
	//@RequestMapping(method = RequestMethod.GET)
	public String onGet2() {
		return VIEW;
	}
	
	// pulled from CLIUtils.java
	private static String[] IVR_LE_FIRST = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian,
	        UID.ExplicitVRBigEndianRetired };
	
	// roughly stolen from FindSCU.java
	private void query(Association as, Attributes keys, final JSONWriter jw) throws IOException, InterruptedException {
		DimseRSPHandler rspHandler = new DimseRSPHandler(
		                                                 as.nextMessageID()) {
			
			int cancelAfter = 1; // for testing
			
			int numMatches;
			
			@Override
			public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
				//System.out.println("####### DimesRSP received after "+(System.currentTimeMillis()-t1)+"ms");
				super.onDimseRSP(as, cmd, data);
				int status = cmd.getInt(Tag.Status, -1);
				if (Status.isPending(status)) {
					try {
						jw.write(data);
						System.out.println("Wrote JSON");
					}
					catch (Exception e) {
						System.out.println("Failed to write JSON : " + e.getMessage());
					}
					++numMatches;
					if (cancelAfter != 0 && numMatches >= cancelAfter)
						try {
							cancel(as);
							cancelAfter = 0;
						}
						catch (IOException e) {
							e.printStackTrace();
						}
				}
			}
		};
		System.out.println("Query with keys: " + keys.toString());
		as.cfind(UID.StudyRootQueryRetrieveInformationModelFIND, Priority.NORMAL, keys, null, rspHandler);
	}
	
	/**
	 * Initially called after the getUsers method to get the landing form name
	 * 
	 * @return String form view name
	 */
	@RequestMapping(value = "module/pacsquery/pacsquery.form", method = RequestMethod.GET, produces = "application/json")
	public void onGet(Writer responseWriter, @RequestParam(value = "patientid") String patientid) {
		try {
			System.out.println("Initating Pacs Query");
			// get a JSON handle on output stream
			Device device = new Device("findscu");
			System.out.println("Created a device");
			//WriterOutputStream out = new WriterOutputStream(responseWriter, Charsets.UTF_8);
			// Use dcm4che to send a dicom request
			// remote connection
			Connection remote = new Connection("pacs", "10.10.10.133", 11112);
			System.out.println("Created Remote Connection");
			//Connection conn = new Connection("openmrs", "10.10.10.177", 11113);
			Connection conn = new Connection();
			System.out.println("Created Connection");
			// I'm guessing some of these would be defaults?
			conn.setReceivePDULength(Connection.DEF_MAX_PDU_LENGTH);
			conn.setSendPDULength(Connection.DEF_MAX_PDU_LENGTH);
			conn.setMaxOpsInvoked(0);
			conn.setMaxOpsPerformed(0);
			conn.setPackPDV(true);
			conn.setConnectTimeout(0);
			conn.setRequestTimeout(0);
			conn.setAcceptTimeout(0);
			conn.setReleaseTimeout(0);
			conn.setResponseTimeout(0);
			conn.setRetrieveTimeout(0);
			conn.setIdleTimeout(0);
			conn.setSocketCloseDelay(Connection.DEF_SOCKETDELAY);
			conn.setSendBufferSize(0);
			conn.setReceiveBufferSize(0);
			conn.setTcpNoDelay(true);
			remote.setTlsProtocols(conn.getTlsProtocols());
			remote.setTlsCipherSuites(conn.getTlsCipherSuites());
			System.out.println("Configured Connections");
			
			// Application Entity
			ApplicationEntity ae = new ApplicationEntity("PACSQUERY");
			ae.addConnection(conn);
			System.out.println("Created AE Entity");
			
			device.addConnection(conn);
			device.addApplicationEntity(ae);
			
			// Association Request
			AAssociateRQ rq = new AAssociateRQ();
			rq.setCalledAET("DCM4CHEE");
			rq.addPresentationContext(new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFIND,
			        IVR_LE_FIRST));
			System.out.println("Configured Association Requirements");
			
			Attributes attr = new Attributes();
			// Add study level
			attr.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
			// Match Patient ID
			attr.setString(0x00100020, VR.LO, patientid);
			// Request the following
			attr.setNull(0x00080020, VR.DA); // study date
			attr.setNull(0x00080030, VR.TM); // study time
			attr.setNull(0x00080060, VR.CS); // modality
			attr.setNull(0x0020000D, VR.UI); // study uid
			attr.setNull(0x00402016, VR.LO); // Order #
			attr.setNull(0x00080050, VR.SH); // Accession #
			attr.setNull(0x00321060, VR.LO); // Requested Procedure Description
			System.out.println("Configured Attributes");
			
			ExecutorService executorService = Executors.newSingleThreadExecutor();
			ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
			device.setExecutor(executorService);
			device.setScheduledExecutor(scheduledExecutorService);
			
			Association as = null;
			DimseRSP rsp = null;
			long t1 = 0;
			long t2 = 0;
			JsonGenerator g = null;
			try {
				// Get the association
				t1 = System.currentTimeMillis();
				as = ae.connect(remote, rq);
				t2 = System.currentTimeMillis();
				System.out.println("Association opened in " + (t2 - t1) + "ms");
				// run the query
				
				g = Json.createGenerator(responseWriter);
				g.writeStartArray();
				System.out.println("Created JSON Generator");
				
				JSONWriter jw = new JSONWriter(g);
				System.out.println("Created JSON Writer");
				
				t1 = System.currentTimeMillis();
				query(as, attr, jw);
			}
			finally {
				// close
				if (as != null && as.isReadyForDataTransfer()) {
					as.waitForOutstandingRSP();
					as.release();
				}
				executorService.shutdown();
				scheduledExecutorService.shutdown();
				
				t2 = System.currentTimeMillis();
				System.out.println("C-FIND Closed in " + (t2 - t1) + "ms!");
				if (g != null) {
					try {
						g.writeEnd();
					}
					catch (Exception e) {
						System.out.println(e.getMessage());
					}
					g.close();
				}
			}
		}
		catch (Exception e) {
			System.err.println("Caught Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * All the parameters are optional based on the necessity
	 * 
	 * @param httpSession
	 * @param anyRequestObject
	 * @param errors
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST)
	public String onPost(HttpSession httpSession, @ModelAttribute("anyRequestObject") Object anyRequestObject,
	        BindingResult errors) {
		
		if (errors.hasErrors()) {
			// return error view
		}
		
		return VIEW;
	}
	
	/**
	 * This class returns the form backing object. This can be a string, a boolean, or a normal java
	 * pojo. The bean name defined in the ModelAttribute annotation and the type can be just defined
	 * by the return type of this method
	 */
	@ModelAttribute("users")
	protected List<User> getUsers() throws Exception {
		List<User> users = userService.getAllUsers();
		
		// this object will be made available to the jsp page under the variable name
		// that is defined in the @ModuleAttribute tag
		return users;
	}
	
}
