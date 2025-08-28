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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apposed.appose.Appose;
import org.apposed.appose.Builder;
import org.scijava.command.Command;
import org.scijava.launcher.Splash;
import org.scijava.log.Logger;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

/**
 * SciJava command wrapper to build a Python environment.
 *
 * @author Curtis Rueden
 */
@Plugin(type = Command.class, label = "Rebuild Python environment")
public class RebuildEnvironment implements Command {

	private static final int _PROGRESS_LENGTH = 80;

	@Parameter
	private Logger log;

	@Parameter(label = "environment definition file")
	private File environmentYaml;

	@Parameter(label = "Target directory")
	private File targetDir;

	@Parameter(required = false)
	private UIService uiService;

	private int progressPrinted = 0;

	// -- OptionsPython methods --

	@Override
	public void run() {
		final File backupDir = new File(targetDir.getPath() + ".old");
		// Prevent rebuilding the environment currently in use by pyimagej
		if (targetDir.exists()) {
			String cfgPythonDir = System.getProperty("scijava.python.dir", "");
			if (cfgPythonDir != null && !cfgPythonDir.isEmpty()) {
				// Normalize both paths for comparison
				String targetPath = targetDir.getAbsolutePath().replace("\\", "/");
				String cfgPath = cfgPythonDir.replace("\\", "/");
				if (targetPath.endsWith(cfgPath)) {
					String msg = "You cannot rebuild the environment currently being used by pyimagej (" + cfgPythonDir + ").\n" +
						"Please select a different target directory, or restart in Java mode.";
					if (uiService != null) {
						uiService.showDialog(msg, "Cannot Rebuild Active Environment", DialogPrompt.MessageType.ERROR_MESSAGE);
					} else {
						log.error(msg);
					}
					return;
				}
			}
			boolean confirmed = true;
			if (uiService != null) {
				String msg =
					"The environment directory already exists. If you continue, it will be renamed to '" +
						backupDir.getName() +
						"' (and any previous backup will be deleted). Continue?";
				DialogPrompt.Result result = uiService.showDialog(msg,
					"Confirm Environment Rebuild",
					DialogPrompt.MessageType.QUESTION_MESSAGE,
					DialogPrompt.OptionType.YES_NO_OPTION);
				confirmed = result == DialogPrompt.Result.YES_OPTION;
			}
			if (!confirmed) return;
		}
		// Delete the previous backup environment recursively.
		if (backupDir.exists()) {
			try (Stream<Path> x = Files.walk(backupDir.toPath())) {
				x.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					}
					catch (IOException exc) {
						log.error(exc);
					}
				});
			}
			catch (IOException exc) {
				log.error(exc);
			}
		}
		// Rename the old environment to a backup directory.
		if (targetDir.exists()) targetDir.renameTo(backupDir);
		// Build the new environment.
		try {
			Builder builder = Appose.file(environmentYaml, "environment.yml")
				.subscribeOutput(this::reportMsg).subscribeError(this::reportErr)
				.subscribeProgress((msg, cur, max) -> Splash.update(msg, (double) cur /
					max));

			// HACK: stderr stream triggers console window show.
			System.err.println();
			log.info("Building Python environment");
			Splash.show();
			builder.build(targetDir);
			// Notify user of success
			if (uiService != null) {
				uiService.showDialog(
					"Python environment setup was successful and is ready to use!",
					"Environment Ready", DialogPrompt.MessageType.INFORMATION_MESSAGE);
			}
		}
		catch (IOException exc) {
			log.error("Failed to build Python environment", exc);
		}
	}

	private void reportErr(String s) {
		report(s, log::error);
	}

	private void reportMsg(String s) {
		report(s, log::info);
	}

    private void report(String s, Consumer<String> reporter) {
		if (s.isEmpty()) {
			System.err.print(".");
			progressPrinted++;
			if (progressPrinted >= _PROGRESS_LENGTH) {
				System.err.println();
				progressPrinted = 0;
			}
		}
		else {
			progressPrinted = 0;
			reporter.accept(s);
		}
    }
}
