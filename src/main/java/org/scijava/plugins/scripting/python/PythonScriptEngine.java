/*
 * #%L
 * JSR-223-compliant Groovy scripting language plugin.
 * %%
 * Copyright (C) 2014 - 2021 SciJava developers.
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

import javax.script.ScriptException;
import org.scijava.script.AbstractScriptEngine;
import org.scijava.plugins.scripting.python.PythonScriptRunner;

/**
 * A script engine for conda-based python.
 *
 * @author Curtis Rueden
 * @author Karl Duderstadt
 * @see ScriptEngine
 */
public class PythonScriptEngine extends AbstractScriptEngine {

	@Parameter
	ObjectService objectService;

	public PythonScriptEngine(Context context) {
		context.inject(this);
	}

	@Override
	public Object eval(String script) throws ScriptException {
		Map<String, Object> vars;
		//parse script parameters and build input map Map<String, Object> vars

		return objectService.getObjects(PythonScriptRunner.class).stream().findAny().get().run(script, vars);
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

}
