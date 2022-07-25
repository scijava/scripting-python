/*
 * #%L
 * Python scripting language plugin to be used with PyImageJ.
 * %%
 * Copyright (C) 2021 - 2022 SciJava developers.
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

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.scijava.Context;
import org.scijava.log.LogService;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.script.AbstractScriptEngine;
import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

/**
 * A script engine for Python (PyImageJ).
 *
 * @author Curtis Rueden
 * @author Karl Duderstadt
 * @see ScriptEngine
 */
public class PythonScriptEngine extends AbstractScriptEngine {

	@Parameter
	ObjectService objectService;
	
	@Parameter
	LogService logService;
	
	@Parameter
	UIService uiService;

	public PythonScriptEngine(Context context) {
		context.inject(this);
		setLogService(logService);
		engineScopeBindings = new ScriptBindings();
	}

	@Override
	public Object eval(String script) throws ScriptException {
		if (objectService.getObjects(PythonScriptRunner.class).stream().count() > 0)
			return objectService.getObjects(PythonScriptRunner.class).get(0).run(script, engineScopeBindings, scriptContext);
		
		uiService.showDialog("The PythonScriptRunner could not be found in the ObjectService. To use the\n" +
			                   "Conda Python 3 script engine Fiji must be launched from python inside a conda\n " +
			                   "environment and a PythonScriptRunner must be added to the ObjectService.\n", DialogPrompt.MessageType.ERROR_MESSAGE);
		return null;
	}

	@Override
	public Object eval(Reader reader) throws ScriptException {
		StringBuilder buf = new StringBuilder();
		char [] cbuf = new char [65536];
		while (true) {
			try {
				int nChars = reader.read(cbuf);
				if (nChars <= 0) break;
				buf.append(cbuf, 0, nChars);
			} catch (IOException e) {
				throw new ScriptException(e);
			}
		}
		return eval(buf.toString());
	}

	@Override
	public Bindings createBindings() {
		return new ScriptBindings();
	}
	
	//Somehow just type casting did not work...
	class ScriptBindings implements Bindings {
		
		private Map<String, Object> bindingsMap; 
		
		ScriptBindings() {
			bindingsMap = new HashMap<String, Object>();
		}

		@Override
		public int size() {
			return bindingsMap.size();
		}

		@Override
		public boolean isEmpty() {
			return bindingsMap.isEmpty();
		}

		@Override
		public boolean containsValue(Object value) {
			return bindingsMap.containsValue(value);
		}

		@Override
		public void clear() {
			bindingsMap.clear();
		}

		@Override
		public Set<String> keySet() {
			return bindingsMap.keySet();
		}

		@Override
		public Collection<Object> values() {
			return bindingsMap.values();
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			return bindingsMap.entrySet();
		}

		@Override
		public Object put(String name, Object value) {
			return bindingsMap.put(name, value);
		}

		@Override
		public void putAll(Map<? extends String, ? extends Object> toMerge) {
			bindingsMap.putAll(toMerge);
		}

		@Override
		public boolean containsKey(Object key) {
			return bindingsMap.containsKey(key);
		}

		@Override
		public Object get(Object key) {
			return bindingsMap.get(key);
		}

		@Override
		public Object remove(Object key) {
			return bindingsMap.remove(key);
		}
	}
}
