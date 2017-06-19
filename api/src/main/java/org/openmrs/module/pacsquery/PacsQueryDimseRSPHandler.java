/**
 * This Source Code Form is subject to the terms of the MIT License. If a copy
 * of the MPL was not distributed with this file, You can obtain one at 
 * https://opensource.org/licenses/MIT.
 */

package org.openmrs.module.pacsquery;

import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.Association;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Status;

import java.io.IOException;

// roughly stolen from FindSCU.java
public class PacsQueryDimseRSPHandler extends DimseRSPHandler {
	
	int cancelAfter = 50;
	
	int numMatches;
	
	JSONWriter jw;
	
	public PacsQueryDimseRSPHandler(int msgId, JSONWriter jw) {
		super(msgId);
		this.jw = jw;
	}
	
	@Override
	public void onDimseRSP(Association as, Attributes cmd, Attributes data) {
		super.onDimseRSP(as, cmd, data);
		int status = cmd.getInt(Tag.Status, -1);
		if (Status.isPending(status)) {
			try {
				this.jw.write(data);
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
}
