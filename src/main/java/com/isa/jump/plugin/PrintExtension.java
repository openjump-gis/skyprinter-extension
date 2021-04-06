package com.isa.jump.plugin;

import com.vividsolutions.jump.workbench.plugin.Extension;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;

/**
 *
 * @author Matthias Scholz<ms@jammerhund.de>
 */
public class PrintExtension extends Extension {
	@Override
	public void configure(PlugInContext context) throws Exception {
        new PrintPlugIn().initialize(context);
	}

	@Override
	public String getName() {
		return PrintPlugIn.PRINT_PLUGIN_NAME;
	}

	@Override
	public String getVersion() {
		return PrintPlugIn.PRINT_PLUGIN_VERSION;
	}

}
