/**
 * This Source Code Form is subject to the terms of the MIT License. If a copy
 * of the MPL was not distributed with this file, You can obtain one at 
 * https://opensource.org/licenses/MIT.
 */
package org.openmrs.module.pacsquery;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.io.Writer;
import java.io.Writer;

import jakarta.json.stream.JsonGenerator;
import jakarta.json.Json;

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

import org.openmrs.module.pacsquery.PacsqueryDimseRSPHandler;

class PacsquerySCU {
	
	ApplicationEntity ae;
	
	Device device;
	
	Association as;
	
	public PacsquerySCU() {
		Connection localConn = new Connection();
		this.ae = new ApplicationEntity("PACSQUERY");
		this.ae.addConnection(localConn);
		
		this.device = new Device("findscu");
		this.device.addConnection(localConn);
		this.device.addApplicationEntity(this.ae);
		
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		this.device.setExecutor(executorService);
		this.device.setScheduledExecutor(scheduledExecutorService);
	}
	
	public Association openConnection(String aetitle, String host, int port) throws Exception {
		Connection remote = new Connection(aetitle, host, port);
		AAssociateRQ rq = new AAssociateRQ();
		rq.setCalledAET(aetitle);
		String[] ts = { UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian };
		rq.addPresentationContext(new PresentationContext(1, UID.StudyRootQueryRetrieveInformationModelFind, ts));
		Association as = this.ae.connect(remote, rq);
		return as;
	}
	
	public ApplicationEntity getAE() {
		return ae;
	}
	
	public Device getDevice() {
		return device;
	}
	
	public Association getAssociation() {
		return as;
	}
	
	public void shutdown() {
		// this.device.getExecutor().shutdown();
		this.device.getScheduledExecutor().shutdown();
	}
}

public class PacsqueryService {
	
	String aetitle;
	
	String host;
	
	int port;
	
	int[] tags;
	
	private ApplicationEntity ae;
	
	private Device device;
	
	public PacsqueryService(String aetitle, String host, int port, int[] tags) {
		this.aetitle = aetitle;
		this.host = host;
		this.port = port;
		this.tags = tags;
	}
	
	private Attributes prepareAttributes(String patientid, String date) {
		// Create Attributes
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
		return attr;
	}
	
	public void query(String patientid, String date, Writer response) throws Exception {
		
		// set up local PACSQUERY device
		PacsquerySCU scu = new PacsquerySCU();
		try {
			Association as = scu.openConnection(this.aetitle, this.host, this.port);
			
			// prepare attributes
			Attributes attr = this.prepareAttributes(patientid, date);
			
			JsonGenerator jsonGen = Json.createGenerator(response);
			jsonGen.writeStartArray();
			JSONWriter jw = new JSONWriter(jsonGen);
			
			DimseRSPHandler rspHandler = new PacsqueryDimseRSPHandler(as.nextMessageID(), jw);
			as.cfind(UID.StudyRootQueryRetrieveInformationModelFind, Priority.NORMAL, attr, null, rspHandler);
			
			if (as != null && as.isReadyForDataTransfer()) {
				as.waitForOutstandingRSP();
				as.release();
			}
			jsonGen.writeEnd();
			jsonGen.close();
		}
		finally {
			scu.shutdown();
		}
	}
	
}
