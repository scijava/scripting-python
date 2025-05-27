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

import org.scijava.app.AppService;
import org.scijava.command.CommandService;
import org.scijava.launcher.Config;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.options.OptionsPlugin;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for configuring the Python environment.
 * 
 * @author Curtis Rueden
 */
@Plugin(type = OptionsPlugin.class, menu = {
	@Menu(label = MenuConstants.EDIT_LABEL,
		weight = MenuConstants.EDIT_WEIGHT,
		mnemonic = MenuConstants.EDIT_MNEMONIC),
	@Menu(label = "Options", mnemonic = 'o'),
	@Menu(label = "Python...", weight = 10),
})
public class OptionsPython extends OptionsPlugin {

	@Parameter
	private AppService appService;

	@Parameter
	private CommandService commandService;

	@Parameter
	private LogService log;

	@Parameter(label = "Python environment directory", persist = false)
	private File pythonDir;

	@Parameter(label = "Create Python environment", callback = "createEnv")
	private Button createEnvironment;

	@Parameter(label = "Launch in Python mode", callback = "updatePythonConfig", persist = false)
	private boolean pythonMode;

	// -- OptionsPython methods --

	public File getPythonDir() {
		return pythonDir;
	}

	public boolean isPythonMode() {
		return pythonMode;
	}

	public void setPythonDir(final File pythonDir) {
		this.pythonDir = pythonDir;
	}

	public void setPythonMode(final boolean pythonMode) {
		this.pythonMode = pythonMode;
	}

	// -- Callback methods --

	@Override
	public void load() {
		// Read python-dir and python-mode from app config file.
		String configFileProp = System.getProperty("scijava.app.config-file");
		File configFile = configFileProp == null ? null : new File(configFileProp);
		if (configFile != null && configFile.canRead()) {
			try {
				final Map<String, String> config = Config.load(configFile);

				final String cfgPythonDir = config.get("python-dir");
				if (cfgPythonDir != null) {
					final Path appPath = appService.getApp().getBaseDirectory().toPath();
					pythonDir = stringToFile(appPath, cfgPythonDir);
				}

				final String cfgPythonMode = config.get("python-mode");
				if (cfgPythonMode != null) pythonMode = cfgPythonMode.equals("true");
			}
			catch (IOException e) {
				// Proceed gracefully if config file is not accessible.
				log.debug(e);
			}
		}

		if (pythonDir == null) {
			// For the default Python directory, try to match the platform string used for Java installations.
			final String javaPlatform = System.getProperty("scijava.app.java-platform");
			final String platform = javaPlatform != null ? javaPlatform :
				System.getProperty("os.name") + "-" + System.getProperty("os.arch");
			final Path pythonPath = appService.getApp().getBaseDirectory().toPath().resolve("python").resolve(platform);
			pythonDir = pythonPath.toFile();
		}
	}

	public void createEnv() {
		// Use scijava.app.python-env-file system property if present.
		final Path appPath = appService.getApp().getBaseDirectory().toPath();
		File environmentYaml = appPath.resolve("config").resolve("environment.yml").toFile();
		final String pythonEnvFileProp = System.getProperty("scijava.app.python-env-file");
		if (pythonEnvFileProp != null) {
			environmentYaml = OptionsPython.stringToFile(appPath, pythonEnvFileProp);
		}

		commandService.run(CreateEnvironment.class, true,
			"environmentYaml", environmentYaml,
			"targetDir", pythonDir
		);
	}

	@Override
	public void save() {
		// Write python-dir and python-mode values to app config file.
		final String configFileProp = System.getProperty("scijava.app.config-file");
		if (configFileProp == null) return; // No config file to update.
		final File configFile = new File(configFileProp);
		Map<String, String> config = null;
		if (configFile.isFile()) {
			try {
				config = Config.load(configFile);
			}
			catch (IOException exc) {
				// Proceed gracefully if config file is not accessible.
				log.debug(exc);
			}
		}
		if (config == null) config = new LinkedHashMap<>();
		final Path appPath = appService.getApp().getBaseDirectory().toPath();
		config.put("python-dir", fileToString(appPath, pythonDir));
		config.put("python-mode", pythonMode ? "true" : "false");
		try {
			Config.save(configFile, config);
		}
		catch (IOException exc) {
			// Proceed gracefully if config file cannot be written.
			log.debug(exc);
		}
	}

	// -- Utility methods --

	/**
	 * Converts a path string to a file, treating relative path expressions as
	 * relative to the given base directory, not the current working directory.
	 */
	static File stringToFile(Path baseDir, String value) {
		final Path path = Paths.get(value);
		final Path absPath = path.isAbsolute() ? path : baseDir.resolve(path);
		return absPath.toFile();
	}

	/**
	 * Converts a file to a path string, which in the case of a file beneath the
	 * given base directory, will be a path expression relative to that base.
	 */
	static String fileToString(Path baseDir, File file) {
		Path filePath = file.toPath();
		Path relPath = filePath.startsWith(baseDir) ?
			baseDir.relativize(filePath) : filePath.toAbsolutePath();
		return relPath.toString();
	}
}
