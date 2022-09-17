/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.base.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.ofbiz.base.location.FlexibleLocation;
import org.ofbiz.base.util.GroovyUtil.GroovyLangVariant;
import org.ofbiz.base.util.cache.UtilCache;

/**
 * Scripting utility methods. This is a facade class that is used to connect OFBiz to JSR-223 scripting engines.
 * <p><b>Important:</b> To avoid a lot of <code>Map</code> copying, all methods that accept a context
 * <code>Map</code> argument will pass that <code>Map</code> directly to the scripting engine. Any variables that
 * are declared or modified in the script will affect the original <code>Map</code>. Client code that wishes to preserve
 * the state of the <code>Map</code> argument should pass a copy of the <code>Map</code>.</p>
 *
 */
public final class ScriptUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    /** The screen widget context map bindings key. */
    public static final String WIDGET_CONTEXT_KEY = "widget";
    /** The service/servlet/request parameters map bindings key. */
    public static final String PARAMETERS_KEY = "parameters";
    /** The result map bindings key. */
    public static final String RESULT_KEY = "result";
    /** The <code>ScriptHelper</code> key. */
    public static final String SCRIPT_HELPER_KEY = "ofbiz";
    private static final UtilCache<String, CompiledScript> parsedScripts = UtilCache.createUtilCache("script.ParsedScripts", 0, 0, false);
    private static final Object[] EMPTY_ARGS = {};
    private static ScriptHelperFactory helperFactory = null;
    /** A set of script names - derived from the JSR-223 scripting engines. */
    public static final Set<String> SCRIPT_NAMES;
    /** SCIPIO: Extra script names for backward-compatibility, mapping to which they're now implemented with (added 2018-09-19). */
    private static final Map<String, String> LEGACY_SCRIPT_NAMES_IMPLMAP = UtilMisc.toMap("bsh", "groovy");
    /**
     * SCIPIO: New (2017-01-30) static ScriptEnginerManager instance, instead of recreating at every invocation.
     * NOTE: For this to be safe, we MUST use the static ClassLoader, and NOT the thread context classloader,
     * because the latter may be a Tomcat webapp classloader for an arbitrary webapp.
     * NOTE: This singleton means it is not possible for a webapp to provide its own script engines, but generally
     * speaking, this was never supported or tested in ofbiz; to support webapp-specific languages with singleton instances,
     * there would probably have to be a ScriptEngineManager cached in every ServletContext as attribute (TODO?).
     */
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager(ScriptUtil.class.getClassLoader());

    static {
        // SCIPIO: sanity check
        Debug.logInfo("ScriptUtil engine manager class loader: " + ScriptUtil.class.getClassLoader().getClass().getName(), module);

        Set<String> writableScriptNames = new HashSet<>();
        ScriptEngineManager manager = getScriptEngineManager();
        List<ScriptEngineFactory> engines = manager.getEngineFactories();
        if (engines.isEmpty()) {
            Debug.logInfo("No scripting engines were found.", module);
        } else {
            Debug.logInfo("The following " + engines.size() + " scripting engines were found:", module);
            for (ScriptEngineFactory engine : engines) {
                Debug.logInfo("Engine name: " + engine.getEngineName(), module);
                Debug.logInfo("  Version: " + engine.getEngineVersion(), module);
                Debug.logInfo("  Language: " + engine.getLanguageName(), module);
                List<String> extensions = engine.getExtensions();
                if (extensions.size() > 0) {
                    Debug.logInfo("  Engine supports the following extensions:", module);
                    for (String e : extensions) {
                        Debug.logInfo("    " + e, module);
                    }
                }
                List<String> shortNames = engine.getNames();
                if (shortNames.size() > 0) {
                    Debug.logInfo("  Engine has the following short names:", module);
                    for (String name : engine.getNames()) {
                        writableScriptNames.add(name);
                        Debug.logInfo("    " + name, module);
                    }
                }
            }
        }
        // SCIPIO: 2018-09-19: These needed for transient backward-compat
        Debug.logInfo("The following deprecated/compatibility/legacy script language names are recognized by Scipio:", module);
        for(Map.Entry<String, String> entry : LEGACY_SCRIPT_NAMES_IMPLMAP.entrySet()) {
            if (!writableScriptNames.contains(entry.getKey())) {
                Debug.logInfo("    " + entry.getKey() 
                    + (entry.getValue() != null ? " (implemented with: " + entry.getValue() + ")" : ""), module);
                writableScriptNames.add(entry.getKey());
            }
        }
        // SCIPIO: Print out all script names in succinct list to help with script maintenance
        Debug.logInfo("All supported script names: " + writableScriptNames.toString(), module);
        SCRIPT_NAMES = Collections.unmodifiableSet(writableScriptNames);
        Iterator<ScriptHelperFactory> iter = ServiceLoader.load(ScriptHelperFactory.class).iterator();
        if (iter.hasNext()) {
            helperFactory = iter.next();
            if (Debug.verboseOn()) {
                Debug.logVerbose("ScriptHelper factory set to " + helperFactory.getClass().getName(), module);
            }
        } else {
            Debug.logWarning("ScriptHelper factory not found", module);
        }
    }

    /**
     * Returns a compiled script.
     *
     * @param filePath Script path and file name.
     * @return The compiled script, or <code>null</code> if the script engine does not support compilation.
     * @throws IllegalArgumentException
     * @throws ScriptException
     * @throws IOException
     */
    public static CompiledScript compileScriptFile(String filePath) throws ScriptException, IOException {
        Assert.notNull("filePath", filePath);
        CompiledScript script = parsedScripts.get(filePath);
        if (script == null) {
            ScriptEngineManager manager = getScriptEngineManager();
            String fileExtension = getFileExtension(filePath); // SCIPIO: slight refactor
            if ("bsh".equalsIgnoreCase(fileExtension)) { // SCIPIO: 2018-09-19: Warn here, there's nowhere else we can do it...
                Debug.logWarning("Deprecated Beanshell script file invoked (" + filePath + "); "
                        + "this is a compatibility mode only (runs as Groovy); please convert to Groovy script", module);
                fileExtension = "groovy";
            }
            ScriptEngine engine = manager.getEngineByExtension(fileExtension);
            if (engine == null) {
                throw new IllegalArgumentException("The script type is not supported for location: " + filePath);
            }
            engine = configureScriptEngineForInvoke(engine); // SCIPIO: 2017-01-27: Custom configurations for the engine
            try {
                Compilable compilableEngine = (Compilable) engine;
                URL scriptUrl = FlexibleLocation.resolveLocation(filePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(scriptUrl.openStream(), UtilIO
                    .getUtf8()));
                script = compilableEngine.compile(reader);
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Compiled script " + filePath + " using engine " + engine.getClass().getName(), module);
                }
            } catch (ClassCastException e) {
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Script engine " + engine.getClass().getName() + " does not implement Compilable", module);
                }
            }
            if (script != null) {
                parsedScripts.putIfAbsent(filePath, script);
            }
        }
        return script;
    }

    /**
     * Returns a compiled script.
     *
     * @param language
     * @param script
     * @return The compiled script, or <code>null</code> if the script engine does not support compilation.
     * @throws IllegalArgumentException
     * @throws ScriptException
     */
    public static CompiledScript compileScriptString(String language, String script) throws ScriptException {
        if ("bsh".equals(language)) { // SCIPIO: 2018-09-19: Beanshell backward-compatibility mode
            // FIXME?: currently unable to use a custom GroovyClassLoader in configureScriptEngineForInvoke so
            // we cannot fully honor GroovyLangVariant(.BSH) config yet; thankfully right now it only needs a custom Binding...
            language = "groovy";
        }
        Assert.notNull("language", language, "script", script);
        String cacheKey = language.concat("://").concat(script);
        CompiledScript compiledScript = parsedScripts.get(cacheKey);
        if (compiledScript == null) {
            ScriptEngineManager manager = getScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName(language);
            if (engine == null) {
                throw new IllegalArgumentException("The script type is not supported for language: " + language);
            }
            engine = configureScriptEngineForInvoke(engine); // SCIPIO: 2017-01-27: Custom configurations for the engine
            try {
                Compilable compilableEngine = (Compilable) engine;
                compiledScript = compilableEngine.compile(script);
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Compiled script [" + script + "] using engine " + engine.getClass().getName(), module);
                }
            } catch (ClassCastException e) {
                if (Debug.verboseOn()) {
                    Debug.logVerbose("Script engine " + engine.getClass().getName() + " does not implement Compilable", module);
                }
            }
            if (compiledScript != null) {
                parsedScripts.putIfAbsent(cacheKey, compiledScript);
            }
        }
        return compiledScript;
    }

    /**
     * Returns a <code>ScriptContext</code> that contains the members of <code>context</code>.
     * <p>If a <code>CompiledScript</code> instance is to be shared by multiple threads, then
     * each thread must create its own <code>ScriptContext</code> and pass it to the
     * <code>CompiledScript</code> eval method.</p>
     *
     * @param context
     * @return
     */
    public static ScriptContext createScriptContext(Map<String, Object> context) {
        Assert.notNull("context", context);
        Map<String, Object> localContext = new HashMap<>(context);
        localContext.put(WIDGET_CONTEXT_KEY, context);
        localContext.put("context", context);
        ScriptContext scriptContext = new SimpleScriptContext();
        ScriptHelper helper = createScriptHelper(scriptContext);
        if (helper != null) {
            localContext.put(SCRIPT_HELPER_KEY, helper);
        }
        Bindings bindings = new SimpleBindings(localContext);
        scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        return scriptContext;
    }

    /**
     * Returns a <code>ScriptContext</code> that contains the members of <code>context</code>.
     * <p>If a <code>CompiledScript</code> instance is to be shared by multiple threads, then
     * each thread must create its own <code>ScriptContext</code> and pass it to the
     * <code>CompiledScript</code> eval method.</p>
     *
     * @param context
     * @param protectedKeys
     * @return
     */
    public static ScriptContext createScriptContext(Map<String, Object> context, Set<String> protectedKeys) {
        Assert.notNull("context", context, "protectedKeys", protectedKeys);
        Map<String, Object> localContext = new HashMap<>(context);
        localContext.put(WIDGET_CONTEXT_KEY, context);
        localContext.put("context", context);
        ScriptContext scriptContext = new SimpleScriptContext();
        Bindings bindings = new ProtectedBindings(localContext, Collections.unmodifiableSet(protectedKeys));
        scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        ScriptHelper helper = createScriptHelper(scriptContext);
        if (helper != null) {
            localContext.put(SCRIPT_HELPER_KEY, helper);
        }
        return scriptContext;
    }

    public static ScriptHelper createScriptHelper(ScriptContext context) {
        if (helperFactory != null) {
            return helperFactory.getInstance(context);
        }
        return null;
    }

     /**
     * Executes a script <code>String</code> and returns the result.
     *
     * @param language
     * @param script
     * @param scriptClass
     * @param context
     * @return The script result.
     * @throws Exception
     */
    public static Object evaluate(String language, String script, Class<?> scriptClass, Map<String, Object> context) throws Exception {
        Assert.notNull("context", context);
        if (scriptClass != null) {
            if ("bsh".equals(language)) { // SCIPIO: 2018-09-19: Beanshell backward-compatibility mode (runs as Groovy)
                return InvokerHelper.createScript(scriptClass, GroovyUtil.getBinding(context, 
                        GroovyLangVariant.BSH)).run();
            }
            return InvokerHelper.createScript(scriptClass, GroovyUtil.getBinding(context)).run();
        }
        if ("bsh".equals(language)) { // SCIPIO: 2018-09-19: Beanshell backward-compatibility mode (runs as Groovy)
            // SPECIAL: we need our special Binding for Bsh compat, so reroute through GroovyUtil instead
            // NOTE: For this we'll use cache false to be safe; callers who want speed should switch to Groovy,
            // which gets cached by the GroovyScriptEngine through the code below this.
            return GroovyUtil.eval(script, context, GroovyLangVariant.BSH, false, false, false);
        }
        try {
            CompiledScript compiledScript = compileScriptString(language, script);
            if (compiledScript != null) {
                return executeScript(compiledScript, null, createScriptContext(context), null);
            }
            ScriptEngineManager manager = getScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName(language);
            if (engine == null) {
                throw new IllegalArgumentException("The script type is not supported for language: " + language);
            }
            engine = configureScriptEngineForInvoke(engine); // SCIPIO: 2017-01-27: Custom configurations for the engine
            if (Debug.verboseOn()) {
                Debug.logVerbose("Begin processing script [" + script + "] using engine " + engine.getClass().getName(), module);
            }
            ScriptContext scriptContext = createScriptContext(context);
            return engine.eval(script, scriptContext);
        } catch (Exception e) {
            String errMsg = "Error running " + language + " script [" + script + "]: " + e.toString();
            Debug.logWarning(e, errMsg, module);
            throw new IllegalArgumentException(errMsg);
        }
    }

    /**
     * Executes a compiled script and returns the result.
     *
     * @param script Compiled script.
     * @param functionName Optional function or method to invoke.
     * @param scriptContext Script execution context.
     * @return The script result.
     * @throws IllegalArgumentException
     */
    public static Object executeScript(CompiledScript script, String functionName, ScriptContext scriptContext, Object[] args) throws ScriptException, NoSuchMethodException {
        Assert.notNull("script", script, "scriptContext", scriptContext);
        Object result = script.eval(scriptContext);
        if (UtilValidate.isNotEmpty(functionName)) {
            if (Debug.verboseOn()) {
                Debug.logVerbose("Invoking function/method " + functionName, module);
            }
            ScriptEngine engine = script.getEngine();
            try {
                Invocable invocableEngine = (Invocable) engine;
                result = invocableEngine.invokeFunction(functionName, args == null ? EMPTY_ARGS : args);
            } catch (ClassCastException e) {
                throw new ScriptException("Script engine " + engine.getClass().getName() + " does not support function/method invocations");
            }
        }
        return result;
    }

    /**
     * Executes the script at the specified location and returns the result.
     *
     * @param filePath Script path and file name.
     * @param functionName Optional function or method to invoke.
     * @param context Script execution context.
     * @return The script result.
     * @throws IllegalArgumentException
     */
    public static Object executeScript(String filePath, String functionName, Map<String, Object> context) {
        return executeScript(filePath, functionName, context, new Object[] { context });
    }

    /**
     * Executes the script at the specified location and returns the result.
     *
     * @param filePath Script path and file name.
     * @param functionName Optional function or method to invoke.
     * @param context Script execution context.
     * @param args Function/method arguments.
     * @return The script result.
     * @throws IllegalArgumentException
     */
    public static Object executeScript(String filePath, String functionName, Map<String, Object> context, Object[] args) {
        try {
            // Enabled to run Groovy data preparation scripts using GroovyUtil rather than the generic JSR223 that doesn't support debug mode
            // and does not return a stack trace with line number on error
            if (filePath.endsWith(".groovy")) {
                return GroovyUtil.runScriptAtLocation(filePath, functionName, context);
            }
            return executeScript(filePath, functionName, createScriptContext(context), args);
        } catch (Exception e) {
            String errMsg = "Error running script at location [" + filePath + "]: " + e.toString();
            Debug.logWarning(e, errMsg, module);
            throw new IllegalArgumentException(errMsg, e);
        }
    }

    /**
     * Executes the script at the specified location and returns the result.
     *
     * @param filePath Script path and file name.
     * @param functionName Optional function or method to invoke.
     * @param scriptContext Script execution context.
     * @param args Function/method arguments.
     * @return The script result.
     * @throws ScriptException
     * @throws NoSuchMethodException
     * @throws IOException
     * @throws IllegalArgumentException
     */
    public static Object executeScript(String filePath, String functionName, ScriptContext scriptContext, Object[] args) throws ScriptException, NoSuchMethodException, IOException {
        Assert.notNull("filePath", filePath, "scriptContext", scriptContext);
        scriptContext.setAttribute(ScriptEngine.FILENAME, filePath, ScriptContext.ENGINE_SCOPE);
        if (functionName == null) {
            // The Rhino script engine will not work when invoking a function on a compiled script.
            // The test for null can be removed when the engine is fixed.
            CompiledScript script = compileScriptFile(filePath);
            if (script != null) {
                return executeScript(script, functionName, scriptContext, args);
            }
        }
        String fileExtension = getFileExtension(filePath);
        if ("bsh".equalsIgnoreCase(fileExtension)) { // SCIPIO: 2018-09-19: Warn here, there's nowhere else we can do it...
            Debug.logWarning("Deprecated Beanshell script file invoked (" + filePath + "); "
                    + "this is a compatibility mode only (runs as Groovy); please convert to Groovy script", module);
            // FIXME?: This does not properly use the GroovyLangVariant.BSH bindings for scripts in filesystem... unclear if should have
            fileExtension = "groovy";
        }
        ScriptEngineManager manager = getScriptEngineManager();
        ScriptEngine engine = manager.getEngineByExtension(fileExtension);
        if (engine == null) {
            throw new IllegalArgumentException("The script type is not supported for location: " + filePath);
        }
        engine = configureScriptEngineForInvoke(engine); // SCIPIO: 2017-01-27: Custom configurations for the engine
        if (Debug.verboseOn()) {
            Debug.logVerbose("Begin processing script [" + filePath + "] using engine " + engine.getClass().getName(), module);
        }
        engine.setContext(scriptContext);
        URL scriptUrl = FlexibleLocation.resolveLocation(filePath);
        try (
                InputStreamReader reader = new InputStreamReader(new FileInputStream(scriptUrl.getFile()), UtilIO
                        .getUtf8());) {
            Object result = engine.eval(reader);
            if (UtilValidate.isNotEmpty(functionName)) {
                try {
                    Invocable invocableEngine = (Invocable) engine;
                    result = invocableEngine.invokeFunction(functionName, args == null ? EMPTY_ARGS : args);
                } catch (ClassCastException e) {
                    throw new ScriptException("Script engine " + engine.getClass().getName()
                            + " does not support function/method invocations");
                }
            }
            return result;
        }
    }

    private static String getFileExtension(String filePath) {
        int pos = filePath.lastIndexOf(".");
        if (pos == -1) {
            throw new IllegalArgumentException("Extension missing in script file name: " + filePath);
        }
        return filePath.substring(pos + 1);
    }

    public static Class<?> parseScript(String language, String script) {
        Class<?> scriptClass = null;
        if ("groovy".equals(language)) {
            try {
                // SCIPIO: 2017-01-27: this now parses using the custom GroovyClassLoader,
                // so that groovy snippets from FlexibleStringExpander support extra additions
                // SCIPIO: TODO: in future this method should support the other GroovyLangVariants
                //scriptClass = GroovyUtil.parseClass(script);
                scriptClass = GroovyUtil.parseClass(script,
                        GroovyLangVariant.STANDARD.getCommonGroovyClassLoader());
            } catch (IOException e) {
                Debug.logError(e, module);
                return null;
            }
        } else if ("bsh".equals(language)) {
            // SCIPIO 2018-09-19: backward-compat mode
            try {
                scriptClass = GroovyUtil.parseClass(script,
                        GroovyLangVariant.BSH.getCommonGroovyClassLoader());
            } catch (IOException e) {
                Debug.logError(e, module);
                return null;
            }
        }
        return scriptClass;
    }

    private ScriptUtil() {}

    private static final class ProtectedBindings implements Bindings {
        private final Map<String, Object> bindings;
        private final Set<String> protectedKeys;
        private ProtectedBindings(Map<String, Object> bindings, Set<String> protectedKeys) {
            this.bindings = bindings;
            this.protectedKeys = protectedKeys;
        }
        public void clear() {
            for (String key : bindings.keySet()) {
                if (!protectedKeys.contains(key)) {
                    bindings.remove(key);
                }
            }
        }
        public boolean containsKey(Object key) {
            return bindings.containsKey(key);
        }
        public boolean containsValue(Object value) {
            return bindings.containsValue(value);
        }
        public Set<java.util.Map.Entry<String, Object>> entrySet() {
            return bindings.entrySet();
        }
        @Override
        public boolean equals(Object o) {
            return bindings.equals(o);
        }
        public Object get(Object key) {
            return bindings.get(key);
        }
        @Override
        public int hashCode() {
            return bindings.hashCode();
        }
        public boolean isEmpty() {
            return bindings.isEmpty();
        }
        public Set<String> keySet() {
            return bindings.keySet();
        }
        public Object put(String key, Object value) {
            Assert.notNull("key", key);
            if (protectedKeys.contains(key)) {
                UnsupportedOperationException e = new UnsupportedOperationException("Variable " + key + " is read-only");
                Debug.logWarning(e, module);
                throw e;
            }
            return bindings.put(key, value);
        }
        public void putAll(Map<? extends String, ? extends Object> map) {
            for (Map.Entry<? extends String, ? extends Object> entry : map.entrySet()) {
                Assert.notNull("key", entry.getKey());
                if (!protectedKeys.contains(entry.getKey())) {
                    bindings.put(entry.getKey(), entry.getValue());
                }
            }
        }
        public Object remove(Object key) {
            if (protectedKeys.contains(key)) {
                UnsupportedOperationException e = new UnsupportedOperationException("Variable " + key + " is read-only");
                Debug.logWarning(e, module);
                throw e;
            }
            return bindings.remove(key);
        }
        public int size() {
            return bindings.size();
        }
        public Collection<Object> values() {
            return bindings.values();
        }
    }

    /**
     * SCIPIO: Performs String.trim() on every line of script. Completely flattens the code.
     */
    public static String trimScriptLines(String body) {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new java.io.StringReader(body));
        try {
            String line = reader.readLine();
            while (line != null) {
                sb.append(line.trim());
                line = reader.readLine();
                if (line != null) {
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
            }
        }
        return sb.toString();
    }

    /**
     * SCIPIO: Applies global configurations to the given ScriptEngine, if any, for this invocation/thread.
     * Should be called by all ScriptUtil methods every time a new ScriptEngine is gotten
     * from the ScriptEngineManager.
     * <p>
     * NOTE: This is safe only with the assumption that the ScriptEngine instance
     * passed was newly created for this thread (and not a singleton instance).
     * At current time (2017-01-27), this was already being assumed (implied by the presence of
     * calls such as <code>ScriptEngine.setContext</code>, above),
     * and is known to be true for GroovyScriptEngineImpl (see Groovy source code),
     * further dictated by the fact that GroovyScriptEngineFactory cannot be configured
     * without subclassing.
     */
    private static ScriptEngine configureScriptEngineForInvoke(ScriptEngine scriptEngine) {
        return configureScriptEngine(scriptEngine);
    }

    /**
     * SCIPIO: Configures the given script engine with any non-default settings needed.
     * <p>
     * 2017-01-27: This now sets our custom GroovyClassLoader on the engine, so that
     * all Groovy scripts will derived from Ofbiz GroovyBaseScript and methods
     * such as <code>from(...)</code> (entity queries) are available everywhere,
     * notably from inline scripts.
     */
    private static ScriptEngine configureScriptEngine(ScriptEngine scriptEngine) {
        if (scriptEngine instanceof GroovyScriptEngineImpl) {
            GroovyScriptEngineImpl groovyScriptEngine = (GroovyScriptEngineImpl) scriptEngine;
            // SCIPIO: TODO: in future this method should support the other GroovyLangVariants
            groovyScriptEngine.setClassLoader(GroovyLangVariant.STANDARD.getCommonGroovyClassLoader());
        }
        return scriptEngine;
    }

    /**
     * SCIPIO: Returns an appropriate {@link javax.script.ScriptEngineManager} for current
     * thread, with ofbiz configuration (if any). Abstracts the creation and selection of the manager.
     * <p>
     * NOTE: 2017-01-30: This now currently always returns a manager that uses the basic component-level
     * classloader (rather than webapp classloader).
     */
    public static ScriptEngineManager getScriptEngineManager() {
        // SCIPIO: 2017-01-30: we will no longer create a new manager at every invocation;
        // needless re-initialization; does not appear to be any strong concrete reason to do this.
        // In addition, the classloader should probably not be there current thread context classloader (with current setup),
        // so in this new() call might call for ScriptUtil.class.getClassLoader() as parameter.
        //return new ScriptEngineManager();
        return scriptEngineManager;
    }
}
