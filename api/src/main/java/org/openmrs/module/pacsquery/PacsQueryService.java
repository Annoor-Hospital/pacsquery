/**
 * This Source Code Form is subject to the terms of the MIT License. If a copy
 * of the MPL was not distributed with this file, You can obtain one at 
 * https://opensource.org/licenses/MIT.
 */
package org.openmrs.module.pacsquery;

import java.io.Writer;
import java.util.List;

import javax.json.stream.JsonGenerator;
import javax.json.Json;

import org.dcm4che3.net.IncompatibleConnectionException;

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
import org.dcm4che3.data.ElementDictionary;

import org.openmrs.api.context.Context;
import org.openmrs.api.AdministrationService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.api.context.Context;

import org.openmrs.module.pacsquery.PacsQueryDimseRSPHandler;

import java.lang.InterruptedException;
import java.io.IOException;

public class PacsQueryService {
	
	String aetitle;
	
	String host;
	
	int port;
	
	int[] tags;
	
	Writer respWriter;
	
	public PacsQueryService(String aetitle, String host, int port, int[] tags) {
		this.aetitle = aetitle;
		this.host = host;
		this.port = port;
		this.tags = tags;
	}
	
	public PacsQueryService() {
		AdministrationService administrationService = Context.getAdministrationService();
		
		String pacsConfig = administrationService.getGlobalProperty("pacsquery.pacsConfig");
		// DCM4CHEE@localhost:11112
		String[] parts = pacsConfig.split("@");
		this.aetitle = parts[0];
		this.host = parts[1];
		parts = this.host.split(":");
		this.host = parts[0];
		this.port = Integer.parseInt(parts[1]);
		
		String retrieveTags = administrationService.getGlobalProperty("pacsquery.retrieveTags");
		//00000000,34323431
		String[] tagsString = retrieveTags.split(",");
		this.tags = new int[tagsString.length];
		for (int i = 0; i < tagsString.length; i++) {
			tags[i] = (int) Long.parseLong(tagsString[i], 16);
		}
	}
	
	public PacsQueryService(Writer respWriter) {
		this();
		this.setWriter(respWriter);
	}
	
	public void setWriter(Writer respWriter) {
		this.respWriter = respWriter;
	}
	
	// roughly stolen from FindSCU.java
	public void query(String patientid, String date) throws Exception {
		if (patientid.isEmpty() && date.isEmpty())
			throw new Exception("At least one of patientid, date required.");
		if (!patientid.isEmpty() && !patientid.matches("^[A-Za-z]{0,3}[0-9]+$")) {
			throw new Exception("patientid must be numeric");
		}
		if (!date.isEmpty() && !date.matches("^[0-9]{4}[0-1][0-9][0-3][0-9]$")) {
			throw new Exception("date must be of format YYYYMMDD");
		}
		//System.out.println("Initating Pacs Query");
		
		// Create a device for query
		Device device = new Device("findscu");
		
		// Create remote connection
		Connection remote = new Connection("pacs", this.host, this.port);
		
		// Create a new connection
		Connection conn = new Connection();
		
		// Set connection properties
		conn.setReceivePDULength(Connection.DEF_MAX_PDU_LENGTH);
		conn.setSendPDULength(Connection.DEF_MAX_PDU_LENGTH);
		conn.setMaxOpsInvoked(0);
		conn.setMaxOpsPerformed(0);
		conn.setPackPDV(true);
		conn.setConnectTimeout(10000); // 10 sec
		conn.setRequestTimeout(10000); // 10 sec
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
		
		// Create Application Entity
		ApplicationEntity ae = new ApplicationEntity("PACSQUERY");
		ae.addConnection(conn);
		device.addConnection(conn);
		device.addApplicationEntity(ae);
		
		// Create Association Request
		AAssociateRQ rq = new AAssociateRQ();
		rq.setCalledAET(this.aetitle);
		// pulled from CLIUtils.java
		String[] IVR_LE_FIRST = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian };
		rq.addPresentationContext(new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFind, IVR_LE_FIRST));
		
		// Create Attributes
		//System.out.println("Creating query attributes");
		Attributes attr = new Attributes();
		// Add study level
		attr.setString(Tag.QueryRetrieveLevel, VR.CS, "STUDY");
		// request params
		for (int tag : this.tags) {
			attr.setNull(tag, ElementDictionary.vrOf(tag, null));
		}
		// Match query params
		if (!patientid.isEmpty()) {
			attr.setString(0x00100020, VR.LO, patientid);
		}
		if (!date.isEmpty()) {
			attr.setString(0x00080020, VR.DA, date); // date-date
		}
		
		// Create Executor Service
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		device.setExecutor(executorService);
		device.setScheduledExecutor(scheduledExecutorService);
		
		// Run the query and write the result
		//System.out.println("Running query for patientid=" + patientid + " date=" + date);
		Association as = null;
		DimseRSP rsp = null;
		long t1 = 0;
		long t2 = 0;
		JsonGenerator g = null;
		try {
			// Get the association
			//t1 = System.currentTimeMillis();
			as = ae.connect(remote, rq);
			//t2 = System.currentTimeMillis();
			//System.out.println("Association opened in " + (t2 - t1) + "ms");
			
			// Build a JSON Generator and Writer
			g = Json.createGenerator(this.respWriter);
			g.writeStartArray();
			JSONWriter jw = new JSONWriter(g);
			//System.out.println("Created JSON Writer");
			
			//t1 = System.currentTimeMillis();
			DimseRSPHandler rspHandler = new PacsQueryDimseRSPHandler(as.nextMessageID(), jw);
			//System.out.println("Query with keys:\n" + attr.toString());
			as.cfind(UID.StudyRootQueryRetrieveInformationModelFind, Priority.NORMAL, attr, null, rspHandler);
		}
		catch (Exception e) {
			throw new Exception("Query failed: " + e.getMessage());
		}
		finally {
			// close
			if (as != null && as.isReadyForDataTransfer()) {
				as.waitForOutstandingRSP();
				as.release();
			}
			executorService.shutdown();
			scheduledExecutorService.shutdown();
			
			//t2 = System.currentTimeMillis();
			//System.out.println("C-FIND Closed in " + (t2 - t1) + "ms!");
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
}
