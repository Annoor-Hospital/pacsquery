/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.pacsquery.api;

import jakarta.json.*;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.pacsquery.PacsqueryService;
import static org.junit.Assert.*;

public class PacsqueryServiceTest {
	
	@Test
	public void testQueryByPatientId() throws Exception {
		// PACS connection details
		String aetitle = "DCM4CHEE";
		String host = "10.10.10.248";
		int port = 11112;
		
		// Tags to request
		int[] tags = { 0x00080020, // Study Date
		        0x00080030, // Study Time
		        0x00080060, // Modality
		        0x0020000D, // Study Instance UID
		        0x00402016, // Scheduled Procedure Step ID
		        0x00080050, // Accession Number
		        0x00321060 // Requested Procedure Description
		};
		
		// Query parameters
		String patientId = "MAF266776";
		String date = ""; // no date
		
		// Create the query service
		PacsqueryService service = new PacsqueryService(aetitle, host, port, tags);
		
		// Capture JSON response in memory
		Writer responseWriter = new StringWriter();
		
		// Run the query
		service.query(patientId, date, responseWriter);
		
		// Output for manual inspection
		String jsonResponse = responseWriter.toString();
		
		// System.out.println("DICOM Query Result: " + jsonResponse);
		
		JsonReader reader = Json.createReader(new StringReader(jsonResponse));
		JsonArray studies = reader.readArray();
		
		System.out.println("\nRead " + studies.size() + " studies.\n");
		
		assertTrue("No studies returned", studies.size() > 0);
	}
	
	@Test
	public void testQueryByDate() throws Exception {
		// PACS connection details
		String aetitle = "DCM4CHEE";
		String host = "10.10.10.248";
		int port = 11112;
		
		// Tags to request
		int[] tags = { 0x00080020, // Study Date
		        0x00080030, // Study Time
		        0x00080060, // Modality
		        0x0020000D, // Study Instance UID
		        0x00402016, // Scheduled Procedure Step ID
		        0x00080050, // Accession Number
		        0x00321060 // Requested Procedure Description
		};
		
		// Query parameters
		String patientId = ""; // no patint
		String date = "20250825";
		
		// Create the query service
		PacsqueryService service = new PacsqueryService(aetitle, host, port, tags);
		
		// Capture JSON response in memory
		Writer responseWriter = new StringWriter();
		
		// Run the query
		service.query(patientId, date, responseWriter);
		
		// Output for manual inspection
		String jsonResponse = responseWriter.toString();
		
		// System.out.println("DICOM Query Result: " + jsonResponse);
		
		JsonReader reader = Json.createReader(new StringReader(jsonResponse));
		JsonArray studies = reader.readArray();
		
		System.out.println("\nRead " + studies.size() + " studies.\n");
		
		assertTrue("No studies returned", studies.size() > 0);
	}
}
