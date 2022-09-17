package com.ilscipio.scipio.ce.webapp.ftl.context;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.template.FreeMarkerWorker;

import com.ilscipio.scipio.ce.webapp.ftl.lang.LangFtlUtil;

import freemarker.core.Environment;
import freemarker.ext.util.WrapperTemplateModel;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;

/**
 * SCIPIO: Utilities intended explicitly for implementation Freemarker transforms,
 * or that implement the transform interface as seen by templates.
 * <p>
 * The utils here provide the common behavior for transforms such that they all should behave
 * predictably to users.
 * <p>
 * Functions which are more generic in nature should go in the other util classes such
 * as {@link ContextFtlUtil}.
 */
public abstract class TransformUtil {

    //private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected TransformUtil() {
    }

    /**
     * Abstracted method to retrieve a "context/global" var (loosely-defined) from the Freemarker environment.
     * At minimum, always include Freemarker globals and data model, encompassing Ofbiz context and globalContext.
     * In addition - SUBJECT TO CHANGE - may read from current or main namespace.
     * <p>
     * NOTE: 2016-10-13: Currently this only reads from globals and data model, ignoring main
     * and current namespaces. So it will only respond to changes made using #global directive and not #assign.
     * TODO: REVIEW: We will start with this more restrictive/safer behavior first and see in future if main
     * or current namespace should be considered. This should be made to match the FTL macro implementations.
     * Some of the more common variable names (such as "locale") can cause problematic conflicts.
     *
     * @see freemarker.core.Environment#getGlobalVariable(String)
     * @see com.ilscipio.scipio.ce.webapp.ftl.lang.LangFtlUtil#getMainNsOrGlobalVar(String, Environment)
     * @see com.ilscipio.scipio.ce.webapp.ftl.lang.LangFtlUtil#getCurrentNsOrGlobalVar(String, Environment)
     */
    public static TemplateModel getFtlContextGlobalVar(String name, Environment env) throws TemplateModelException {
        //return LangFtlUtil.getMainNsOrGlobalVar(name, env);
        return env.getGlobalVariable(name);
    }

    // TemplateModel (any source)

    /**
     * Gets boolean arg.
     * <p>
     * Will automatically interpret string true/false as boolean.
     */
    public static Boolean getBooleanArg(TemplateModel obj, Boolean defaultValue) throws TemplateModelException {
        if (obj instanceof TemplateBooleanModel) {
            return ((TemplateBooleanModel) obj).getAsBoolean();
        }
        else if (obj instanceof TemplateScalarModel) {
            TemplateScalarModel s = (TemplateScalarModel) obj;
            String val = s.getAsString();
            // SCIPIO: empty check is desirable and makes it so caller can request default by specifying ""
            if (!val.isEmpty()) {
                return "true".equalsIgnoreCase(s.getAsString());
            }
        } else if (obj != null) {
            throw new TemplateModelException("Expected boolean model or string model representation of boolean, but got a " +
                    obj.getClass() + " instead");
        }
        return defaultValue;
    }

    public static Boolean getBooleanArg(TemplateModel obj) throws TemplateModelException {
        return getBooleanArg(obj, null);
    }

    // Map (macro arguments)

    public static Boolean getBooleanArg(Map<?, ?> args, String key, Boolean defaultValue) throws TemplateModelException {
        return getBooleanArg(getModel(args, key), defaultValue);
    }

    public static Boolean getBooleanArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getBooleanArg(getModel(args, key), null);
    }

    // List (function arguments)

    public static Boolean getBooleanArg(List<?> args, int position, Boolean defaultValue) throws TemplateModelException {
        return getBooleanArg(getModel(args, position), defaultValue);
    }

    public static Boolean getBooleanArg(List<?> args, int position) throws TemplateModelException {
        return getBooleanArg(getModel(args, position), null);
    }

    // List (function arguments) with input Hash support

    public static Boolean getBooleanArg(List<?> args, String key, int position, Boolean defaultValue) throws TemplateModelException {
        return getBooleanArg(getModel(args, key, position), defaultValue);
    }

    public static Boolean getBooleanArg(List<?> args, String key, int position) throws TemplateModelException {
        return getBooleanArg(getModel(args, key, position), null);
    }

    // TemplateModel (any source)

    /**
     * Gets string arg.
     * <p>
     * If number or date passed, will be coerced to string. Other types such as maps or lists
     * will throw TemplateModelException.
     */
    public static String getStringArg(TemplateModel obj, String defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        String result = null;
        if (obj instanceof TemplateScalarModel) {
            TemplateScalarModel s = (TemplateScalarModel) obj;
            result = LangFtlUtil.getAsString(s, nonEscaping);
        } else if (obj == null) {
            return defaultValue;
        } else if (obj instanceof TemplateNumberModel || obj instanceof TemplateDateModel) {
            // TODO: optimize this call
            result = LangFtlUtil.execStringBuiltIn(obj, FreeMarkerWorker.getCurrentEnvironment()).getAsString();
        } else {
            throw new TemplateModelException("Expected string model or something coercible to string, but got a " +
                    obj.getClass() + " instead");
        }
        if (useDefaultWhenEmpty && result.isEmpty()) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Gets string arg.
     * <p>
     * Only returns the default if the string is null, but not if empty.
     */
    public static String getStringArg(TemplateModel obj, String defaultValue) throws TemplateModelException {
        return getStringArg(obj, defaultValue, false, false);
    }

    public static String getStringArg(TemplateModel obj, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(obj, null, false, nonEscaping);
    }

    public static String getStringArg(TemplateModel obj) throws TemplateModelException {
        return getStringArg(obj, null, false, false);
    }

    /**
     * Gets string arg, bypassing screen auto-escaping.
     * <p>
     * Only returns the default if the string is null, but not if empty.
     */
    public static String getStringNonEscapingArg(TemplateModel obj, String defaultValue) throws TemplateModelException {
        return getStringArg(obj, defaultValue, false, true);
    }

    public static String getStringNonEscapingArg(TemplateModel obj) throws TemplateModelException {
        return getStringArg(obj, null, false, true);
    }

    // Map (macro arguments)

    public static String getStringArg(Map<?, ?> args, String key, String defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, key), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static String getStringArg(Map<?, ?> args, String key, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, key), defaultValue, false, false);
    }

    public static String getStringArg(Map<?, ?> args, String key, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, key), null, false, nonEscaping);
    }

    public static String getStringArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getStringArg(getModel(args, key), null, false, false);
    }

    public static String getStringNonEscapingArg(Map<?, ?> args, String key, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, key), defaultValue, false, true);
    }

    public static String getStringNonEscapingArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getStringArg(getModel(args, key), null, false, true);
    }

    // List (function arguments)

    public static String getStringArg(List<?> args, int position, String defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, position), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static String getStringArg(List<?> args, int position, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, position), defaultValue, false, false);
    }

    public static String getStringArg(List<?> args, int position, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, position), null, false, nonEscaping);
    }

    public static String getStringArg(List<?> args, int position) throws TemplateModelException {
        return getStringArg(getModel(args, position), null, false, false);
    }

    public static String getStringNonEscapingArg(List<?> args, int position, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, position), defaultValue, false, true);
    }

    public static String getStringNonEscapingArg(List<?> args, int position) throws TemplateModelException {
        return getStringArg(getModel(args, position), null, false, true);
    }

    // List (function arguments) with input Hash support

    public static String getStringArg(List<?> args, String key, int position, String defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static String getStringArg(List<?> args, String key, int position, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), defaultValue, false, false);
    }

    public static String getStringArg(List<?> args, String key, int position, boolean nonEscaping) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), null, false, nonEscaping);
    }

    public static String getStringArg(List<?> args, String key, int position) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), null, false, false);
    }

    public static String getStringNonEscapingArg(List<?> args, String key, int position, String defaultValue) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), defaultValue, false, true);
    }

    public static String getStringNonEscapingArg(List<?> args, String key, int position) throws TemplateModelException {
        return getStringArg(getModel(args, key, position), null, false, true);
    }

    // TemplateModel (any source)

    public static Object getBooleanOrStringArg(TemplateModel obj, Object defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        Object result = null;
        if (obj instanceof TemplateBooleanModel) {
            return ((TemplateBooleanModel) obj).getAsBoolean();
        } else if (obj instanceof TemplateScalarModel) {
            TemplateScalarModel s = (TemplateScalarModel) obj;
            result = LangFtlUtil.getAsString(s, nonEscaping);
        } else if (obj != null) {
            result = obj.toString();
        } else {
            return defaultValue;
        }
        if (useDefaultWhenEmpty && (result instanceof String) && ((String) result).isEmpty()) {
            return defaultValue;
        }
        return result;
    }

    public static Object getBooleanOrStringArg(TemplateModel obj) throws TemplateModelException {
        return getBooleanOrStringArg(obj, null, false, false);
    }

    public static Object getBooleanOrStringNonEscapingArg(TemplateModel obj) throws TemplateModelException {
        return getBooleanOrStringArg(obj, null, false, true);
    }

    // Map (macro arguments)

    public static Object getBooleanOrStringArg(Map<?, ?> args, String key, Object defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static Object getBooleanOrStringArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key), null, false, false);
    }

    public static Object getBooleanOrStringNonEscapingArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key), null, false, true);
    }

    // List (function arguments)

    public static Object getBooleanOrStringArg(List<?> args, int position, Object defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, position), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static Object getBooleanOrStringArg(List<?> args, int position) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, position), null, false, false);
    }

    public static Object getBooleanOrStringNonEscapingArg(List<?> args, int position) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, position), null, false, true);
    }

    // List (function arguments) with input Hash support

    public static Object getBooleanOrStringArg(List<?> args, String key, int position, Object defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key, position), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    public static Object getBooleanOrStringArg(List<?> args, String key, int position) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key, position), null, false, false);
    }

    public static Object getBooleanOrStringNonEscapingArg(List<?> args, String key, int position) throws TemplateModelException {
        return getBooleanOrStringArg(getModel(args, key, position), null, false, true);
    }


    // TemplateModel (any source)

    /**
     * Returns a Locale OR the Locale representation of the string using UtilMisc.parseLocale ofbiz utility.
     * Added 2017-11-06.
     */
    public static Locale getOfbizLocaleArg(TemplateModel obj) throws TemplateModelException {
        if (obj == null) return null;
        else if (obj instanceof WrapperTemplateModel) {
            Object localeObj = ((WrapperTemplateModel) obj).getWrappedObject();
            if (localeObj == null || localeObj instanceof Locale) return (Locale) localeObj;
            else if (localeObj instanceof String) return UtilMisc.parseLocale((String) localeObj);
        } else if (obj instanceof TemplateScalarModel) {
            String localeStr = LangFtlUtil.getAsStringNonEscaping((TemplateScalarModel) obj);
            return UtilMisc.parseLocale(localeStr);
        }
        throw new IllegalArgumentException("unexpected type for locale argument: " + obj.getClass().getName());
    }

    // Map (macro arguments)

    public static Locale getOfbizLocaleArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getOfbizLocaleArg(getModel(args, key));
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present (abstracted method, behavior could change).
     */
    public static Locale getOfbizLocaleArgOrCurrent(Map<?, ?> args, String key, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key));
        return (locale != null) ? locale : ContextFtlUtil.getCurrentLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale.
     * NOTE: this does NOT check the request locale!
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContext(Map<?, ?> args, String key, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key));
        return (locale != null) ? locale : ContextFtlUtil.getContextLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present.
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContextOrRequest(Map<?, ?> args, String key, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key));
        return (locale != null) ? locale : ContextFtlUtil.getContextOrRequestLocale(env);
    }

    // List (function arguments)

    public static Locale getOfbizLocaleArg(List<?> args, int position, Environment env) throws TemplateModelException {
        return getOfbizLocaleArg(getModel(args, position));
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present (abstracted method, behavior could change).
     */
    public static Locale getOfbizLocaleArgOrCurrent(List<?> args, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, position));
        return (locale != null) ? locale : ContextFtlUtil.getCurrentLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale.
     * NOTE: this does NOT check the request locale!
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContext(List<?> args, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, position));
        return (locale != null) ? locale : ContextFtlUtil.getContextLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present.
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContextOrRequest(List<?> args, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, position));
        return (locale != null) ? locale : ContextFtlUtil.getContextOrRequestLocale(env);
    }

    // List (function arguments) with input Hash support

    public static Locale getOfbizLocaleArg(List<?> args, String key, int position, Environment env) throws TemplateModelException {
        return getOfbizLocaleArg(getModel(args, key, position));
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present (abstracted method, behavior could change).
     */
    public static Locale getOfbizLocaleArgOrCurrent(List<?> args, String key, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key, position));
        return (locale != null) ? locale : ContextFtlUtil.getCurrentLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale.
     * NOTE: this does NOT check the request locale!
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContext(List<?> args, String key, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key, position));
        return (locale != null) ? locale : ContextFtlUtil.getContextLocale(env);
    }

    /**
     * Special handler that tries to read a locale arg and if not present gets it from context locale,
     * or falls back on request if present.
     * @deprecated 2019-02-05: It's best not to use this; use the abstract {@link getOfbizLocaleArgOrCurrent} instead.
     */
    @Deprecated
    public static Locale getOfbizLocaleArgOrContextOrRequest(List<?> args, String key, int position, Environment env) throws TemplateModelException {
        Locale locale = getOfbizLocaleArg(getModel(args, key, position));
        return (locale != null) ? locale : ContextFtlUtil.getContextOrRequestLocale(env);
    }

    // TemplateModel (any source)

    /**
     * Gets integer arg.
     * <p>
     * If string passed, will be parsed as integer. Other types such as maps or lists
     * will throw TemplateModelException.
     */
    public static Integer getIntegerArg(TemplateModel obj, Integer defaultValue) throws TemplateModelException, NumberFormatException {
        if (obj instanceof TemplateNumberModel) {
            return ((TemplateNumberModel) obj).getAsNumber().intValue();
        } else if (obj instanceof TemplateScalarModel) {
            String strResult = LangFtlUtil.getAsString((TemplateScalarModel) obj, true);
            return strResult.isEmpty() ? defaultValue : Integer.parseInt(strResult);
        } else if (obj == null) {
            return defaultValue;
        }
        throw new TemplateModelException("Expected integer model or string representing of integer, but got a " +
                obj.getClass() + " instead");
    }

    public static Integer getIntegerArg(TemplateModel obj) throws TemplateModelException {
        return getIntegerArg(obj, null);
    }

    // Map (macro arguments)

    public static Integer getIntegerArg(Map<?, ?> args, String key, Integer defaultValue) throws TemplateModelException {
        return getIntegerArg(getModel(args, key), defaultValue);
    }

    public static Integer getIntegerArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getIntegerArg(getModel(args, key), null);
    }

    // List (function arguments)

    public static Integer getIntegerArg(List<?> args, int position, Integer defaultValue) throws TemplateModelException {
        return getIntegerArg(getModel(args, position), defaultValue);
    }

    public static Integer getIntegerArg(List<?> args, int position) throws TemplateModelException {
        return getIntegerArg(getModel(args, position), null);
    }

    // List (function arguments) with input Hash support

    public static Integer getIntegerArg(List<?> args, String key, int position, Integer defaultValue) throws TemplateModelException {
        return getIntegerArg(getModel(args, key, position), defaultValue);
    }

    public static Integer getIntegerArg(List<?> args, String key, int position) throws TemplateModelException {
        return getIntegerArg(getModel(args, key, position), null);
    }

    // TemplateModel (any source)

    /**
     * Gets integer arg.
     * <p>
     * If string passed, will be parsed as integer. Other types such as maps or lists
     * will throw TemplateModelException.
     */
    public static Double getDoubleArg(TemplateModel obj, Double defaultValue) throws TemplateModelException, NumberFormatException {
        if (obj instanceof TemplateNumberModel) {
            return ((TemplateNumberModel) obj).getAsNumber().doubleValue();
        } else if (obj instanceof TemplateScalarModel) {
            String strResult = LangFtlUtil.getAsString((TemplateScalarModel) obj, true);
            return strResult.isEmpty() ? defaultValue :  Double.parseDouble(strResult);
        } else if (obj == null) {
            return defaultValue;
        }
        throw new TemplateModelException("Expected integer model or string representing of integer, but got a " +
                obj.getClass() + " instead");
    }

    public static Double getDoubleArg(TemplateModel obj) throws TemplateModelException {
        return getDoubleArg(obj, null);
    }

    // Map (macro arguments)

    public static Double getDoubleArg(Map<?, ?> args, String key, Double defaultValue) throws TemplateModelException {
        return getDoubleArg(getModel(args, key), defaultValue);
    }

    public static Double getDoubleArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getDoubleArg(getModel(args, key), null);
    }

    // List (function arguments)

    public static Double getDoubleArg(List<?> args, int position, Double defaultValue) throws TemplateModelException {
        return getDoubleArg(getModel(args, position), defaultValue);
    }

    public static Double getDoubleArg(List<?> args, int position) throws TemplateModelException {
        return getDoubleArg(getModel(args, position), null);
    }

    // List (function arguments) with input Hash support

    public static Double getDoubleArg(List<?> args, String key, int position, Double defaultValue) throws TemplateModelException {
        return getDoubleArg(getModel(args, key, position), defaultValue);
    }

    public static Double getDoubleArg(List<?> args, String key, int position) throws TemplateModelException {
        return getDoubleArg(getModel(args, key, position), null);
    }
    
    // TemplateModel (any source)

    /**
     * Gets BigDecimal arg.
     * <p>
     * If string or number passed, will be parsed as BigDecimal. Other types such as maps or lists
     * will throw TemplateModelException.
     */
    public static BigDecimal getBigDecimalArg(TemplateModel obj, BigDecimal defaultValue) throws TemplateModelException, NumberFormatException {
        if (obj instanceof TemplateNumberModel) {
            Number number = ((TemplateNumberModel) obj).getAsNumber();
            return UtilNumber.toBigDecimal(number);
        } else if (obj instanceof TemplateScalarModel) {
            String strResult = LangFtlUtil.getAsString((TemplateScalarModel) obj, true);
            return strResult.isEmpty() ? defaultValue : UtilNumber.toBigDecimal(strResult);
        } else if (obj == null) {
            return defaultValue;
        }
        throw new TemplateModelException("Expected BigDecimal model or string representing of BigDecimal, but got a " +
                obj.getClass() + " instead");
    }

    public static BigDecimal getBigDecimalArg(TemplateModel obj) throws TemplateModelException {
        return getBigDecimalArg(obj, null);
    }

    // Map (macro arguments)

    public static BigDecimal getBigDecimalArg(Map<?, ?> args, String key, BigDecimal defaultValue) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, key), defaultValue);
    }

    public static BigDecimal getBigDecimalArg(Map<?, ?> args, String key) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, key), null);
    }

    // List (function arguments)

    public static BigDecimal getBigDecimalArg(List<?> args, int position, BigDecimal defaultValue) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, position), defaultValue);
    }

    public static BigDecimal getBigDecimalArg(List<?> args, int position) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, position), null);
    }

    // List (function arguments) with input Hash support

    public static BigDecimal getBigDecimalArg(List<?> args, String key, int position, BigDecimal defaultValue) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, key, position), defaultValue);
    }

    public static BigDecimal getBigDecimalArg(List<?> args, String key, int position) throws TemplateModelException {
        return getBigDecimalArg(getModel(args, key, position), null);
    }

    // TemplateModel (any source)

    /**
     * Gets a deep-unwrapped map.
     * FIXME: nonEscaping bool is currently not handled... it may bypass escaping in some cases but not others...
     */
    public static <K,V> Map<K, V> getMapArg(TemplateModel obj, Map<K, V> defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        if (!nonEscaping) {
            throw new UnsupportedOperationException("getMapArg currently only supports escaping-bypassing (nonEscaping true)");
        }
        Map<K, V> result = null;
        if (obj instanceof TemplateHashModel) {
            result = UtilGenerics.checkMap(LangFtlUtil.unwrapAlways(obj));
        } else if (obj == null) {
            return defaultValue;
        } else {
            throw new TemplateModelException("Expected hash/map model or something coercible to map, but got a " +
                    obj.getClass() + " instead");
        }
        if (useDefaultWhenEmpty && result.isEmpty()) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Gets a deep-unwrapped map.
     * FIXME: nonEscaping bool is currently not handled... it may bypass escaping in some cases but not others...
     */
    public static <K,V> Map<K, V> getMapArg(Map<?, ?> args, String key, Map<K, V> defaultValue, boolean useDefaultWhenEmpty, boolean nonEscaping) throws TemplateModelException {
        return getMapArg(getModel(args, key), defaultValue, useDefaultWhenEmpty, nonEscaping);
    }

    // Map (macro arguments)

    public static TemplateModel getModel(Map<?, ?> args, String key) {
        return (TemplateModel) args.get(key);
    }

    // List (function arguments)

    public static TemplateModel getModel(List<?> args, int position) {
        return (args.size() > position) ? (TemplateModel) args.get(position) : null;
    }

    // List (function arguments) with input Hash support

    /**
     * If the first argument in the list is a hash, returns its value by key; otherwise returns the value
     * in the list at the given position. This is for functions that need to return
     * If position is (-1), only map access is supported.
     */
    public static TemplateModel getModel(List<?> args, String key, int position) throws TemplateModelException {
        if (args.size() > 0 && args.get(0) instanceof TemplateHashModelEx) {
            return ((TemplateHashModelEx) args.get(0)).get(key);
        } else if (position >= 0 && position < args.size()) {
            return (TemplateModel) args.get(position);
        }
        return null;
    }

    /**
     * @deprecated use {@link UrlTransformUtil#escapeGeneratedUrl(String, String, boolean, Environment)}
     */
    @Deprecated
    public static String escapeGeneratedUrl(String value, String lang, boolean strict, Environment env) throws TemplateModelException {
        return UrlTransformUtil.escapeGeneratedUrl(value, lang, strict, env);
    }
}
