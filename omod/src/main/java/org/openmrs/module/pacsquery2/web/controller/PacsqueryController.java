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

import java.util.List;
import java.io.Writer;

import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import org.openmrs.api.context.Context;
import org.openmrs.api.AdministrationService;

import org.openmrs.module.pacsquery.PacsqueryService;

/**
 * This class configured as controller using annotation and mapped with the URL of
 * 'module/${rootArtifactid}/${rootArtifactid}Link.form'.
 */
// @Controller("${rootArtifactid}.PacsqueryController")
// @RequestMapping(value = "module/${rootArtifactid}")
@Controller("pacsquery.PacsqueryController")
@RequestMapping(value = "module/pacsquery")
public class PacsqueryController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	@Autowired
	UserService userService;
	
	@RequestMapping(value = "query.form", method = RequestMethod.GET, produces = "application/json")
	public void onGet(Writer responseWriter, @RequestParam(value = "patientid", defaultValue = "") String patientid,
	        @RequestParam(value = "date", defaultValue = "") String date) {
		
		AdministrationService administrationService = Context.getAdministrationService();
		String pacsConfig = administrationService.getGlobalProperty("pacsquery.pacsConfig");
		String aetitle;
		String host;
		int port;
		try {
			String[] parts = pacsConfig.split("@");
			aetitle = parts[0];
			parts = parts[1].split(":");
			host = parts[0];
			port = Integer.parseInt(parts[1]);
		}
		catch (Exception e) {
			String msg = "pacsquery.pacsConfig misconfigured. Expected: AETITLE@HOST:PORT, found " + pacsConfig;
			try {
				responseWriter.write("[{\"error\":\"Query failed: " + msg + "\"}]");
			}
			catch (Exception e2) {
				System.out.println(e2.getMessage() + "\n\n Message was: " + msg);
			}
			return;
		}
		
		String retrieveTags = administrationService.getGlobalProperty("pacsquery.retrieveTags");
		int[] tags;
		try {
			String[] tagsString = retrieveTags.split(",");
			tags = new int[tagsString.length];
			for (int i = 0; i < tagsString.length; i++) {
				tags[i] = (int) Long.parseLong(tagsString[i], 16);
			}
		}
		catch (Exception e) {
			String msg = "pacsquery.retrieveTags misconfigured. Expected: comma seperated tags, found " + retrieveTags;
			System.out.println(msg);
			try {
				responseWriter.write("[{\"error\":\"Query failed: " + msg + "\"}]");
			}
			catch (Exception e2) {
				System.out.println(e2.getMessage() + "\n\n Message was: " + msg);
			}
			return;
		}
		
		PacsqueryService ps = new PacsqueryService(aetitle, host, port, tags);
		try {
			ps.query(patientid, date, responseWriter);
		}
		catch (Exception e) {
			log.error(e.getMessage());
			try {
				responseWriter.write("[{\"error\":\"Query failed: " + e.getMessage() + "\"}]");
			}
			catch (Exception e2) {}
		}
	}
	
}
