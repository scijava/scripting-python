/*-
 * #%L
 * Python scripting language plugin to be used via scyjava.
 * %%
 * Copyright (C) 2021 - 2025 SciJava developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package org.scijava.plugins.scripting.python;

import org.apposed.appose.Appose;
import org.apposed.appose.Builder;
import org.scijava.app.AppService;
import org.scijava.command.Command;
import org.scijava.launcher.Splash;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * SciJava command wrapper to build a Python environment.
 * 
 * @author Curtis Rueden
 */
@Plugin(type = Command.class, label = "Create Python environment")
public class CreateEnvironment implements Command {

	@Parameter
	private AppService appService;

	@Parameter
	private Logger log;

	@Parameter(label = "environment definition file")
	private File environmentYaml;

	@Parameter(label = "Target directory")
	private File targetDir;

	// -- OptionsPython methods --

	@Override
	public void run() {
		FileUtils.deleteRecursively(targetDir);
		try {
			Builder builder = Appose
				.file(environmentYaml, "environment.yml")
				.subscribeOutput(this::report)
				.subscribeError(this::report)
				.subscribeProgress((msg, cur, max) -> Splash.update(msg, (double) cur / max));
			System.err.println("Creating Python environment"); // HACK: stderr stream triggers console window show.
			Splash.show();
			builder.build(targetDir);
		}
		catch (IOException exc) {
			log.error("Failed to create Python environment", exc);
		}
	}

	private void report(String s) {
		if (s.isEmpty()) System.err.print(".");
		else System.err.print(s);
	}
}
