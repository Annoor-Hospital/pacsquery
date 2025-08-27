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

public class PacsqueryDimseRSPHandler extends DimseRSPHandler {
	
	private final JSONWriter jw;
	
	public PacsqueryDimseRSPHandler(int msgId, JSONWriter jw) {
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
			}
			catch (Exception e) {
				System.out.println("Failed to write JSON : " + e.getMessage());
			}
		}
	}
}
