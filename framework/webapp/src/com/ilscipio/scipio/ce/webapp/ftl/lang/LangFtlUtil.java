package com.ilscipio.scipio.ce.webapp.ftl.lang;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.rmi.server.UID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.template.FreeMarkerWorker;
import org.ofbiz.base.util.template.ScipioFtlWrappers.EscapingModel;
import org.ofbiz.base.util.template.ScipioFtlWrappers.ScipioBeansWrapper;
import org.ofbiz.base.util.template.ScipioFtlWrappers.ScipioExtendedObjectWrapper;

import freemarker.core.Environment;
import freemarker.ext.beans.BeanModel;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.SimpleMapModel;
import freemarker.ext.util.WrapperTemplateModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.DefaultArrayAdapter;
import freemarker.template.DefaultListAdapter;
import freemarker.template.DefaultMapAdapter;
import freemarker.template.ObjectWrapper;
import freemarker.template.ObjectWrapperAndUnwrapper;
import freemarker.template.SimpleHash;
import freemarker.template.SimpleScalar;
import freemarker.template.SimpleSequence;
import freemarker.template.Template;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateCollectionModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import freemarker.template.utility.DeepUnwrap;
import freemarker.template.utility.ObjectWrapperWithAPISupport;
import freemarker.template.utility.RichObjectWrapper;

/**
 * SCIPIO: Freemarker language utils.
 * <p>
 * These should generally not include Ofbiz-specific utils, except in the case
 * where the Ofbiz-specific code is merely a configuration of Freemarker (e.g.
 * selected usage of <code>BeansWrapper</code> or the special case of <code>EscapingModel</code>).
 * <p>
 * <strong>WARN:</strong> All utility methods here (except special wrap methods)
 * using ObjectWrapper should take an ObjectWrapper from caller - let caller decide which - and never
 * call Environment.getObjectWrapper anymore.
 *
 * @see com.ilscipio.scipio.ce.webapp.ftl.CommonFtlUtil
 */
public abstract class LangFtlUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    // NOTE: there's no _real_ need to synchronize on these. if two templates are built for one builtin its not big deal.
    private static final Map<String, Template> builtInCalls = new ConcurrentHashMap<>();
    private static Template stringBuiltInCall = null;
    private static final Map<String, Template> functionCalls = new ConcurrentHashMap<>();
    private static final Map<String, Template> macroCalls = new ConcurrentHashMap<>();

    protected LangFtlUtil() {
    }

    /**
     * Used for TemplateModel <-> unwrapped/raw value conversions.
     */
    public enum TemplateValueTargetType {
        PRESERVE,
        RAW,
        MODEL,
        SIMPLEMODEL,
        COMPLEXMODEL
    }

    public enum SetOperations {
        UNION,
        INTERSECT,
        DIFFERENCE
    }

    public interface FtlVarHandler {
        void setVariable(String name, TemplateModel model) throws TemplateModelException;
        TemplateModel getVariable(String name) throws TemplateModelException;
    }

    public static class GlobalFtlVarHandler implements FtlVarHandler {
        private final Environment env;

        public GlobalFtlVarHandler(Environment env) {
            this.env = env;
        }

        @Override
        public void setVariable(String name, TemplateModel model) throws TemplateModelException {
            env.setGlobalVariable(name, model);
        }

        @Override
        public TemplateModel getVariable(String name) throws TemplateModelException {
            return env.getGlobalVariable(name);
        }
    }

    public static class CurrentFtlVarHandler implements FtlVarHandler {
        private final Environment env;

        public CurrentFtlVarHandler(Environment env) {
            this.env = env;
        }

        @Override
        public void setVariable(String name, TemplateModel model) throws TemplateModelException {
            env.setVariable(name, model);
        }

        @Override
        public TemplateModel getVariable(String name) throws TemplateModelException {
            return env.getVariable(name);
        }
    }

    public static class LocalFtlVarHandler implements FtlVarHandler {
        private final Environment env;

        public LocalFtlVarHandler(Environment env) {
            this.env = env;
        }

        @Override
        public void setVariable(String name, TemplateModel model) throws TemplateModelException {
            env.setLocalVariable(name, model);
        }

        @Override
        public TemplateModel getVariable(String name) throws TemplateModelException {
            return env.getLocalVariable(name);
        }
    }

    /**
     * Returns a TemplateModel representing a null value. Will always return
     * a model, even if the bean wrapper does not have a null model set.
     */
    public static TemplateModel getNullModelAlways() {
        return TemplateNullModel.getNullModel();
    }

    /**
     * Gets current object wrapper, whatever it may be.
     */
    public static ObjectWrapper getCurrentObjectWrapper(Environment env) {
        return env.getObjectWrapper();
    }

    /**
     * Gets current object wrapper, whatever it may be.
     */
    public static ObjectWrapper getCurrentObjectWrapper() {
        return FreeMarkerWorker.getCurrentEnvironment().getObjectWrapper();
    }

    /**
     * Checks if the wrapper is a special escaping wrapper, and if so,
     * returns a non-escaping one.
     */
    public static ObjectWrapper getNonEscapingObjectWrapper(ObjectWrapper objectWrapper) {
        if (objectWrapper instanceof ScipioExtendedObjectWrapper) {
            return FreeMarkerWorker.getDefaultOfbizWrapper();
        } else {
            return objectWrapper;
        }
    }

    /**
     * Checks if the current env wrapper is a special escaping wrapper, and if so,
     * returns a non-escaping one.
     */
    public static ObjectWrapper getNonEscapingObjectWrapper(Environment env) {
        ObjectWrapper objectWrapper = env.getObjectWrapper();
        if (objectWrapper instanceof ScipioExtendedObjectWrapper) {
            return FreeMarkerWorker.getDefaultOfbizWrapper();
        } else {
            return objectWrapper;
        }
    }

    /**
     * Checks if the current env wrapper is a special escaping wrapper, and if so,
     * returns a non-escaping one.
     */
    public static ObjectWrapper getNonEscapingObjectWrapper() {
        Environment env = FreeMarkerWorker.getCurrentEnvironment();
        ObjectWrapper objectWrapper = env.getObjectWrapper();
        if (objectWrapper instanceof ScipioExtendedObjectWrapper) {
            return FreeMarkerWorker.getDefaultOfbizWrapper();
        } else {
            return objectWrapper;
        }
    }

    /**
     * Returns a non-escaping, simple-types-only object wrapper.
     */
    public static ObjectWrapper getSimpleTypeNonEscapingObjectWrapper(ObjectWrapper objectWrapper) {
        return FreeMarkerWorker.getDefaultSimpleTypeWrapper();
    }

    /**
     * Returns a non-escaping, simple-types-only object wrapper.
     */
    public static ObjectWrapper getSimpleTypeNonEscapingObjectWrapper(Environment env) {
        return FreeMarkerWorker.getDefaultSimpleTypeWrapper();
    }

    /**
     * Returns a non-escaping, simple-types-only object wrapper.
     */
    public static ObjectWrapper getSimpleTypeNonEscapingObjectWrapper() {
        return FreeMarkerWorker.getDefaultSimpleTypeWrapper();
    }

    /**
     * Returns a non-escaping, simple-types-only copying object wrapper.
     */
    public static ObjectWrapper getSimpleTypeCopyingNonEscapingObjectWrapper(ObjectWrapper objectWrapper) {
        return FreeMarkerWorker.getDefaultSimpleTypeCopyingWrapper();
    }

    /**
     * Returns a non-escaping, simple-types-only copying object wrapper.
     */
    public static ObjectWrapper getSimpleTypeCopyingNonEscapingObjectWrapper(Environment env) {
        return FreeMarkerWorker.getDefaultSimpleTypeCopyingWrapper();
    }

    /**
     * Returns a non-escaping, simple-types-only copying object wrapper.
     */
    public static ObjectWrapper getSimpleTypeCopyingNonEscapingObjectWrapper() {
        return FreeMarkerWorker.getDefaultSimpleTypeCopyingWrapper();
    }


    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrap(Object object, ObjectWrapper objectWrapper) throws TemplateModelException {
        return objectWrapper.wrap(object);
    }

    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrap(Object object, Environment env) throws TemplateModelException {
        return getCurrentObjectWrapper(env).wrap(object);
    }

    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrap(Object object) throws TemplateModelException {
        return getCurrentObjectWrapper().wrap(object);
    }

    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrapNonEscaping(Object object, ObjectWrapper objectWrapper) throws TemplateModelException {
        return getNonEscapingObjectWrapper(objectWrapper).wrap(object);
    }

    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrapNonEscaping(Object object, Environment env) throws TemplateModelException {
        return getNonEscapingObjectWrapper(env).wrap(object);
    }

    /**
     * Scipio wrapper around ObjectWrapper.wrap in case extra logic is needed.
     * <p>
     * Currently leaving all to object wrapper, but may change...
     */
    public static TemplateModel wrapNonEscaping(Object object) throws TemplateModelException {
        return getNonEscapingObjectWrapper().wrap(object);
    }

    /**
     * Unwraps template model; if cannot, returns null.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapOrNull(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel != null) {
            Object res = DeepUnwrap.permissiveUnwrap(templateModel);
            if (res != templateModel) {
                return res;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * If TemplateModel, unwraps value, or if cannot, returns null;
     * if not TemplateModel, returns as-is.
     * <p>
     * Ensures no TemplateModels remain.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapOrNull(Object value) throws TemplateModelException {
        if (value instanceof TemplateModel) {
            Object res = DeepUnwrap.permissiveUnwrap((TemplateModel) value);
            if (res != value) {
                return res;
            } else {
                return null;
            }
        } else {
            return value;
        }
    }

    /**
     * Unwraps template model; if cannot, returns as-is.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapPermissive(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel != null) {
            return DeepUnwrap.permissiveUnwrap(templateModel);
        } else {
            return null;
        }
    }

    /**
     * Unwraps value; if cannot, returns value, even if still TemplateModel.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapPermissive(Object value) throws TemplateModelException {
        if (value instanceof TemplateModel) {
            return DeepUnwrap.permissiveUnwrap((TemplateModel) value);
        } else {
            return value;
        }
    }

    /**
     * Unwraps template model; if cannot, throws exception. If null, returns null.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrap(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel != null) {
            return DeepUnwrap.unwrap(templateModel); // will throw exception if improper type
        } else {
            return null;
        }
    }

    /**
     * If template model, unwraps, or if cannot, throws exception;
     * if not template model or null, returns value.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrap(Object value) throws TemplateModelException {
        if (value instanceof TemplateModel) {
            return DeepUnwrap.unwrap((TemplateModel) value);
        } else {
            return value;
        }
    }

    /**
     * Unwraps template model; if cannot, throws exception.
     * <p>
     * Interpretation of null depends on the ObjectWrapper.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapAlways(TemplateModel templateModel) throws TemplateModelException {
        return DeepUnwrap.unwrap(templateModel); // will throw exception if improper type
    }

    /**
     * Unwraps value if template model and unwrappable; else exception.
     * <p>
     * Interpretation of null depends on the ObjectWrapper.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapAlways(Object value) throws TemplateModelException {
        if (value instanceof TemplateModel || value == null) {
            return DeepUnwrap.unwrap((TemplateModel) value);
        } else {
            throw new TemplateModelException("Cannot unwrap non-TemplateModel value (type " + value.getClass().getName() + ")");
        }
    }

    /**
     * Unwraps template model; if cannot, throws exception. Special case where null accepted.
     * <p>
     * Interpretation of null depends on the ObjectWrapper.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapAlwaysUnlessNull(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel == null) {
            return null;
        } else {
            return DeepUnwrap.unwrap(templateModel); // will throw exception if improper type
        }
    }

    /**
     * Unwraps value if template model and unwrappable; else exception. Special case where null accepted.
     * <p>
     * Interpretation of null depends on the ObjectWrapper.
     * <p>
     * NOTE: This automatically bypasses the auto-escaping done by wrappers implementing the <code>EscapingModel</code> interface
     * (such as Ofbiz special widget wrappers).
     */
    public static Object unwrapAlwaysUnlessNull(Object value) throws TemplateModelException {
        if (value instanceof TemplateModel) {
            return DeepUnwrap.unwrap((TemplateModel) value);
        } else if (value == null) {
            return null;
        } else {
            throw new TemplateModelException("Cannot unwrap non-TemplateModel value (type " + value.getClass().getName() + ")");
        }
    }

    /**
     * SCIPIO: Special unwrap that unwraps only objects wrapped with special escaping (Ofbiz) wrappers.
     * If doesn't apply to the value, returns the value as-is.
     * <p>
     * NOTE: The other unwrap methods automatically perform this operation as well.
     */
    public static Object unwrapIfEscaping(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel instanceof EscapingModel) {
            return ((EscapingModel) templateModel).getWrappedObject();
        }
        return templateModel;
    }

    /**
     * SCIPIO: Special unwrap that unwraps only objects wrapped with special escaping (Ofbiz) wrappers.
     * If doesn't apply to the value, returns the value as-is.
     * <p>
     * NOTE: The other unwrap methods automatically perform this operation as well.
     */
    public static Object unwrapIfEscaping(Object value) throws TemplateModelException {
        if (value instanceof EscapingModel) {
            return ((EscapingModel) value).getWrappedObject();
        }
        return value;
    }

    /**
     * SCIPIO: Special unwrap that unwraps only objects wrapped with special escaping (Ofbiz) wrappers.
     * If doesn't apply to the value, returns null.
     * <p>
     * NOTE: The other unwrap methods automatically perform this operation as well.
     */
    public static Object unwrapIfEscapingOrNull(TemplateModel templateModel) throws TemplateModelException {
        if (templateModel instanceof EscapingModel) {
            return ((EscapingModel) templateModel).getWrappedObject();
        }
        return null;
    }

    /**
     * SCIPIO: Special unwrap that unwraps only objects wrapped with special escaping (Ofbiz) wrappers.
     * If doesn't apply to the value, returns null.
     * <p>
     * NOTE: The other unwrap methods automatically perform this operation as well.
     */
    public static Object unwrapIfEscapingOrNull(Object value) throws TemplateModelException {
        if (value instanceof EscapingModel) {
            return ((EscapingModel) value).getWrappedObject();
        }
        return null;
    }

    public static String camelCaseToDashLowerName(String name) {
        // TODO: optimize
        return name.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    public static <K, V> Map<K, V> concatMaps(Map<? extends K, ? extends V> first, Map<? extends K, ? extends V> second) {
        Map<K, V> res = new LinkedHashMap<K, V>();
        if (first != null) {
            res.putAll(first);
        }
        if (second != null) {
            res.putAll(second);
        }
        return res;
    }

    /**
     * Checks if the given model matches the logical FTL object type.
     *
     * @see com.ilscipio.scipio.ce.webapp.ftl.lang.OfbizFtlObjectType
     */
    public static boolean isObjectType(String ftlTypeName, TemplateModel object) {
        return OfbizFtlObjectType.isObjectTypeSafe(ftlTypeName, object);
    }

    /**
     * Gets map keys, either as collection or Set.
     * <p>
     * WARN: auto-escaping is bypassed on all keys, caller handles.
     * (e.g. the object wrapper used to rewrap the result).
     * DEV NOTE: we MUST manually bypass auto-escaping for all on this one.
     */
    public static Object getMapKeys(TemplateModel object) throws TemplateModelException {
        if (OfbizFtlObjectType.COMPLEXMAP.isObjectType(object)) {
            // WARN: bypasses auto-escaping
            Map<Object, Object> wrappedObject = UtilGenerics.cast(((WrapperTemplateModel) object).getWrappedObject());
            return wrappedObject.keySet();
        } else if (object instanceof TemplateHashModelEx) {
            // 2016-04-20: cannot do this because we MUST trigger bypass of auto-escaping,
            // so just do a deep unwrap, which automatically bypasses the escaping,
            // and then caller handles the result, which is probably an arraylist
            //return ((TemplateHashModelEx) object).keys();
            return unwrapAlways(((TemplateHashModelEx) object).keys());
        } else {
            throw new TemplateModelException("object is not a map or does not support key iteration");
        }
    }

    /**
     * Shallow-copies map or list. Note: won't preserve order for maps.
     *
     * @param object
     * @param toSimpleType if true, converts to simple FTL type instead of beans, where possible
     * @return
     * @throws TemplateModelException
     */
    public static Object copyObject(TemplateModel object, TemplateValueTargetType targetType, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (targetType == null) {
            targetType = TemplateValueTargetType.PRESERVE;
        }
        if (OfbizFtlObjectType.COMPLEXMAP.isObjectType(object) || (object instanceof TemplateHashModelEx && OfbizFtlObjectType.MAP.isObjectType(object))) {
            return LangFtlUtil.copyMap(object, null, null, targetType, objectWrapper);
        } else if (object instanceof TemplateCollectionModel || object instanceof TemplateSequenceModel) {
            return LangFtlUtil.copyList(object, targetType, objectWrapper);
        } else {
            throw new TemplateModelException("object is not cloneable");
        }
    }

    /**
     * Copies map.
     * <p>
     * WARN: For complex maps, auto-escaping is bypassed; caller must decide how to handle.
     * (e.g. the object wrapper used to rewrap the result).
     * <p>
     * FIXME: The rewrapping objectWrapper behavior is inconsistent! may lead to auto-escape issues
     */
    public static Object copyMap(TemplateModel object, Set<String> inExKeys, Boolean include,
            TemplateValueTargetType targetType, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (targetType == null) {
            targetType = TemplateValueTargetType.PRESERVE;
        }
        if (OfbizFtlObjectType.COMPLEXMAP.isObjectType(object)) {
            // WARN: bypasses auto-escaping
            Map<String, Object> wrappedObject = UtilGenerics.cast(((WrapperTemplateModel) object).getWrappedObject());
            // TODO: this only handles most urgent targetType case
            if (targetType == TemplateValueTargetType.SIMPLEMODEL) {
                return LangFtlUtil.copyMapToSimple(wrappedObject, inExKeys, include, objectWrapper);
            } else {
                return LangFtlUtil.copyMapToRawMap(wrappedObject, inExKeys, include);
            }
        } else if (object instanceof TemplateHashModel && OfbizFtlObjectType.MAP.isObjectType(object)) {
            // TODO: this ignores targetType
            return LangFtlUtil.copyMapToSimple((TemplateHashModel) object, inExKeys, include, objectWrapper);
        }
        throw new TemplateModelException("Cannot copy map of type " + object.getClass().toString() +
                " to target type: " + targetType.toString());
    }

    public static SimpleHash copyMapToSimple(TemplateHashModel hashModel, Set<String> inExKeys, Boolean include, ObjectWrapper objectWrapper) throws TemplateModelException {
        SimpleHash res = new SimpleHash(objectWrapper);
        putAll(res, hashModel, inExKeys, include, objectWrapper);
        return res;
    }

    public static void putAll(SimpleHash res, TemplateHashModel hashModel, Set<String> inExKeys, Boolean include, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (include == Boolean.TRUE) {
            if (inExKeys == null) {
                inExKeys = new HashSet<String>();
            }
            for(String key : inExKeys) {
                TemplateModel valueModel = hashModel.get(key);
                if (inExKeys.contains(key)) {
                    res.put(key, valueModel);
                }
            }
        } else if (include == null || inExKeys == null || inExKeys.isEmpty()) {
            if (!(hashModel instanceof TemplateHashModelEx)) {
                throw new TemplateModelException("Hash to copy does not support ?keys");
            }

            TemplateCollectionModel keys = ((TemplateHashModelEx) hashModel).keys();
            TemplateModelIterator keysIt = keys.iterator();

            while(keysIt.hasNext()) {
                String key = getAsStringNonEscaping((TemplateScalarModel) keysIt.next());
                res.put(key, hashModel.get(key));
            }
        } else {
            if (!(hashModel instanceof TemplateHashModelEx)) {
                throw new TemplateModelException("Hash to copy does not support ?keys");
            }

            TemplateCollectionModel keys = ((TemplateHashModelEx) hashModel).keys();
            TemplateModelIterator keysIt = keys.iterator();

            while(keysIt.hasNext()) {
                String key = getAsStringNonEscaping((TemplateScalarModel) keysIt.next());
                TemplateModel valueModel = hashModel.get(key);
                if (!inExKeys.contains(key)) {
                    res.put(key, valueModel);
                }
            }
        }
    }

    public static SimpleHash copyMapToSimple(Map<String, Object> map, Set<String> inExKeys, Boolean include, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (include == Boolean.TRUE) {
            SimpleHash res = new SimpleHash(objectWrapper);
            if (inExKeys == null) {
                inExKeys = new HashSet<String>();
            }
            for(String key : inExKeys) {
                Object valueModel = map.get(key);
                if (inExKeys.contains(key)) {
                    res.put(key, valueModel);
                }
            }
            return res;
        }
        else if (include == null || inExKeys == null || inExKeys.isEmpty()) {
            return new SimpleHash(map, objectWrapper);
        }
        else {
            SimpleHash res = new SimpleHash(objectWrapper);
            for(Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if (!inExKeys.contains(key)) {
                    res.put(key, entry.getValue());
                }
            }
            return res;
        }
    }

    public static Map<String, Object> copyMapToRawMap(Map<String, Object> map, Set<String> inExKeys, Boolean include) throws TemplateModelException {
        if (include == Boolean.TRUE) {
            Map<String, Object> res = new HashMap<>(map.size());
            if (inExKeys == null) {
                inExKeys = new HashSet<String>();
            }
            for(String key : inExKeys) {
                Object valueModel = map.get(key);
                if (inExKeys.contains(key)) {
                    res.put(key, valueModel);
                }
            }
            return res;
        }
        else if (include == null || inExKeys == null || inExKeys.isEmpty()) {
            return new HashMap<>(map);
        }
        else {
            Map<String, Object> res = new HashMap<>(map.size());
            for(Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                if (!inExKeys.contains(key)) {
                    res.put(key, entry.getValue());
                }
            }
            return res;
        }
    }

    /**
     * Copies a list to a target model/raw list type. In general does not wrap/unwrap individual values.
     */
    public static Object copyList(Object object, TemplateValueTargetType targetType, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (targetType == null) {
            targetType = TemplateValueTargetType.PRESERVE;
        }
        if (object instanceof Iterable) {
            return LangFtlUtil.copyList(UtilGenerics.<Iterable<Object>>cast(object), targetType, objectWrapper);
        }
        else if (object instanceof TemplateModel) {
            return LangFtlUtil.copyList((TemplateModel) object, targetType, objectWrapper);
        }
        throw new TemplateModelException("Cannot copy list of type " + object.getClass().toString() +
            " to target type: " + targetType.toString());
    }

    public static Object copyList(Iterable<Object> object, TemplateValueTargetType targetType, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (targetType == null) {
            targetType = TemplateValueTargetType.PRESERVE;
        }
        if (object instanceof Collection) {
            Collection<Object> collection = UtilGenerics.<Collection<Object>>cast(object);
            if (targetType == TemplateValueTargetType.PRESERVE || targetType == TemplateValueTargetType.RAW) {
                return new ArrayList<>(collection);
            }
            else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL) {
                return new SimpleSequence(collection, objectWrapper);
            }
            else if (targetType == TemplateValueTargetType.COMPLEXMODEL) {
                // no choice but to use user-supplied object wrapper
                return wrap(new ArrayList<>(collection), objectWrapper);
            }
        }
        else {
            Iterable<Object> iterable = UtilGenerics.<Iterable<Object>>cast(object);
            if (targetType == TemplateValueTargetType.PRESERVE || targetType == TemplateValueTargetType.RAW) {
                List<Object> res = new ArrayList<>();
                for(Object val : iterable) {
                    res.add(val);
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL) {
                SimpleSequence res = new SimpleSequence(objectWrapper);
                for(Object val : iterable) {
                    res.add(val);
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.COMPLEXMODEL) {
                List<Object> res = new ArrayList<>();
                for(Object val : iterable) {
                    res.add(val);
                }
                return wrap(res, objectWrapper);
            }
        }
        throw new TemplateModelException("Cannot copy list of type " + object.getClass().toString() +
                " to target type: " + targetType.toString());
    }

    /**
     * Copies list.
     * <p>
     * WARN: For complex lists, auto-escaping is bypassed. Caller must decide how to handle.
     * (e.g. the object wrapper used to rewrap the result).
     * <p>
     * FIXME: The rewrapping objectWrapper behavior is inconsistent! may lead to auto-escape issues
     */
    public static Object copyList(TemplateModel object, TemplateValueTargetType targetType, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (targetType == null) {
            targetType = TemplateValueTargetType.PRESERVE;
        }
        if (object instanceof TemplateCollectionModel) { // TODO: isObjectType
            TemplateCollectionModel collectionModel = (TemplateCollectionModel) object;
            if (targetType == TemplateValueTargetType.RAW) {
                List<Object> res = new ArrayList<>();
                TemplateModelIterator it = collectionModel.iterator();
                while(it.hasNext()) {
                    res.add(it.next());
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL || targetType == TemplateValueTargetType.PRESERVE) {
                // We CANNOT do this, we must make a new one with the specified object wrapper (unfortunately),
                // even if nothing else about it changes
                //return new SimpleSequence(collectionModel);
                SimpleSequence res = new SimpleSequence(objectWrapper);
                TemplateModelIterator it = collectionModel.iterator();
                while(it.hasNext()) {
                    res.add(it.next());
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.COMPLEXMODEL) {
                List<Object> res = new ArrayList<>();
                TemplateModelIterator it = collectionModel.iterator();
                while(it.hasNext()) {
                    res.add(it.next());
                }
                return wrap(res, objectWrapper);
            }
        }
        else if (object instanceof TemplateSequenceModel) { // TODO: isObjectType
            TemplateSequenceModel seqModel = (TemplateSequenceModel) object;
            if (targetType == TemplateValueTargetType.RAW) {
                List<Object> res = new ArrayList<>();
                for(int i=0; i < seqModel.size(); i++) {
                    res.add(seqModel.get(i));
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL || targetType == TemplateValueTargetType.PRESERVE) {
                SimpleSequence res = new SimpleSequence(seqModel.size(), objectWrapper);
                for(int i=0; i < seqModel.size(); i++) {
                    res.add(seqModel.get(i));
                }
                return res;
            }
            else if (targetType == TemplateValueTargetType.COMPLEXMODEL) {
                List<Object> res = new ArrayList<>();
                for(int i=0; i < seqModel.size(); i++) {
                    res.add(seqModel.get(i));
                }
                return wrap(res, objectWrapper);
            }
        }
        else if (object instanceof WrapperTemplateModel) {
            // WARN: bypasses auto-escaping
            Object wrappedObj = ((WrapperTemplateModel) object).getWrappedObject();
            if (wrappedObj instanceof Collection) {
                Collection<Object> collection = UtilGenerics.<Collection<Object>>cast(object);
                if (targetType == TemplateValueTargetType.RAW) {
                    return new ArrayList<>(collection);
                }
                else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL) {
                    return new SimpleSequence(collection, objectWrapper);
                }
                else if (targetType == TemplateValueTargetType.PRESERVE || targetType == TemplateValueTargetType.COMPLEXMODEL) {
                    return wrap(new ArrayList<>(collection), objectWrapper);
                }
            }
            else if (wrappedObj instanceof Iterable) {
                Iterable<Object> iterable = UtilGenerics.<Iterable<Object>>cast(object);
                if (targetType == TemplateValueTargetType.RAW) {
                    List<Object> res = new ArrayList<>();
                    for(Object val : iterable) {
                        res.add(val);
                    }
                    return res;
                }
                else if (targetType == TemplateValueTargetType.MODEL || targetType == TemplateValueTargetType.SIMPLEMODEL) {
                    SimpleSequence res = new SimpleSequence(objectWrapper);
                    for(Object val : iterable) {
                        res.add(val);
                    }
                    return res;
                }
                else if (targetType == TemplateValueTargetType.PRESERVE || targetType == TemplateValueTargetType.COMPLEXMODEL) {
                    List<Object> res = new ArrayList<>();
                    for(Object val : iterable) {
                        res.add(val);
                    }
                    return wrap(res, objectWrapper);
                }
            }
        }
        throw new TemplateModelException("Cannot copy list of type " + object.getClass().toString() +
                " to target type: " + targetType.toString());
    }

    /**
     * Converts map to a simple wrapper, if applicable. Currently only applies to complex maps.
     * <p>
     * 2017-01-26: This has been changed so that it will by default try to wrap everything
     * with DefaultMapAdapter (or SimpleMapModel for BeansWrapper compatibility), which will always work as long as
     * the ObjectWrapper implements ObjectWrapperWithAPISupport.
     * <p>
     * WARN: Bypasses auto-escaping for complex maps; caller must decide how to handle
     * (e.g. the object wrapper used to rewrap the result).
     * Other types of maps are not altered.
     */
    public static TemplateHashModel toSimpleMap(TemplateModel object, Boolean copy, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (OfbizFtlObjectType.COMPLEXMAP.isObjectType(object)) {
            // WARN: bypasses auto-escaping
            Map<?, ?> wrappedObject = UtilGenerics.cast(((WrapperTemplateModel) object).getWrappedObject());
            if (Boolean.TRUE.equals(copy)) {
                return makeSimpleMapCopy(wrappedObject, objectWrapper);
            } else {
                return makeSimpleMapAdapter(wrappedObject, objectWrapper, true);
            }
        } else if (object instanceof TemplateHashModel) {
            return (TemplateHashModel) object;
        } else {
            throw new TemplateModelException("object is not a recognized map type");
        }
    }

    public static TemplateHashModelEx makeSimpleMapCopy(Map<?, ?> map, ObjectWrapper objectWrapper) throws TemplateModelException {
        return new SimpleHash(map, objectWrapper);
    }

    /**
     * Adapts a map to a TemplateHashModelEx using an appropriate simple adapter, normally
     * DefaultMapAdapter (or SimpleMapModel for BeansWrapper compatibility).
     * <p>
     * The ObjectWrapper is expected to implement at least ObjectWrapperWithAPISupport.
     * <p>
     * WARN: If impossible, it will duplicate the map using SimpleHash; but because this may result
     * in loss of ordering, a log warning will be printed.
     */
    public static TemplateHashModelEx makeSimpleMapAdapter(Map<?, ?> map, ObjectWrapper objectWrapper, boolean permissive) throws TemplateModelException {
        // COMPATIBILITY MODE: check if exactly BeansWrapper, or a class that we know extends it WITHOUT extending DefaultObjectWrapper
        if (objectWrapper instanceof ScipioBeansWrapper || BeansWrapper.class.equals(objectWrapper.getClass())) {
            return new SimpleMapModel(map, (BeansWrapper) objectWrapper);
        } else if (objectWrapper instanceof ObjectWrapperWithAPISupport) {
            return DefaultMapAdapter.adapt(map, (ObjectWrapperWithAPISupport) objectWrapper);
        } else {
            if (permissive) {
                Debug.logWarning("Scipio: adaptSimpleMap: Unsupported Freemarker object wrapper (expected to implement ObjectWrapperWithAPISupport or BeansWrapper); forced to adapt map"
                        + " using SimpleHash; this could cause loss of map insertion ordering; please switch renderer setup to a different ObjectWrapper", module);
                return new SimpleHash(map, objectWrapper);
            } else {
                throw new TemplateModelException("Tried to wrap a Map using an adapter class,"
                        + " but our ObjectWrapper does not implement ObjectWrapperWithAPISupport or BeansWrapper"
                        + "; please switch renderer setup to a different ObjectWrapper");
            }
        }
    }


    /**
     * Converts map to a simple wrapper, if applicable, by rewrapping
     * known complex map wrappers that implement <code>WrapperTemplateModel</code>.
     * <p>
     * If the specified ObjectWrapper is a BeansWrapper, this forces rewrapping as a SimpleMapModel.
     * If it isn't we assume caller specified an objectWrapper that will rewrap the map with
     * a simple model (we have no way of knowing).
     * <p>
     * WARN: Bypasses auto-escaping for complex maps; caller must decide how to handle
     * (e.g. the object wrapper used to rewrap the result).
     * Other types of maps are not altered.
     *
     * @deprecated don't use
     */
    @SuppressWarnings("unused")
    @Deprecated
    private static TemplateHashModel toSimpleMapRewrapAdapters(TemplateModel object, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (object instanceof SimpleMapModel || object instanceof BeanModel || object instanceof DefaultMapAdapter) {
            // Permissive
            Map<?, ?> wrappedObject = (Map<?, ?>) ((WrapperTemplateModel) object).getWrappedObject();
            if (objectWrapper instanceof BeansWrapper) {
                // Bypass the beanswrapper wrap method and always make simple wrapper
                return new SimpleMapModel(wrappedObject, (BeansWrapper) objectWrapper);
            } else {
                // If anything other than BeansWrapper, assume caller is aware and his wrapper will create a simple map
                return (TemplateHashModel) objectWrapper.wrap(wrappedObject);
            }
        }
        else if (object instanceof TemplateHashModel) {
            return (TemplateHashModel) object;
        }
        else {
            throw new TemplateModelException("object is not a recognized map type");
        }
    }

    /**
     * Converts map to a simple wrapper, if applicable, by rewrapping
     * any map wrappers that implement <code>WrapperTemplateModel</code>.
     * <p>
     * This method is very permissive: anything that wraps a Map is accepted.
     * Other types of hashes are returned as-is.
     * <p>
     * If the specified ObjectWrapper is a BeansWrapper, this forces rewrapping as a SimpleMapModel.
     * If it isn't we assume caller specified an objectWrapper that will rewrap the map with
     * a simple model (we have no way of knowing).
     * <p>
     * WARN: Bypasses auto-escaping for complex maps; caller must decide how to handle
     * (e.g. the object wrapper used to rewrap the result).
     * Other types of maps are not altered.
     *
     * @deprecated don't use
     */
    @SuppressWarnings("unused")
    @Deprecated
    private static TemplateHashModel toSimpleMapRewrapAny(TemplateModel object, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (object instanceof WrapperTemplateModel) {
            // Permissive
            Map<?, ?> wrappedObject = (Map<?, ?>) ((WrapperTemplateModel) object).getWrappedObject();
            if (objectWrapper instanceof BeansWrapper) {
                // Bypass the beanswrapper wrap method and always make simple wrapper
                return new SimpleMapModel(wrappedObject, (BeansWrapper) objectWrapper);
            } else {
                // If anything other than BeansWrapper, assume caller is aware and his wrapper will create a simple map
                return (TemplateHashModel) objectWrapper.wrap(wrappedObject);
            }
        }
        else if (object instanceof TemplateHashModel) {
            return (TemplateHashModel) object;
        }
        else {
            throw new TemplateModelException("object is not a recognized map type");
        }
    }


    /**
     * Supposed to convert to simple sequence.
     * <p>
     * WARN: Bypasses auto-escaping for complex maps, caller must decide how to handle.
     * (e.g. the object wrapper used to rewrap the result).
     * <p>
     * DEV NOTE: I stopped writing/testing this when found out most of the problems w.r.t. collections are not
     * the FTL types this time but the way they're used in Ofbiz templates.
     * FTL's CollectionModel (subclass of TemplateCollectionModel) is supposed to cover everything and
     * won't suffer from the same problems maps have.
     */
    @SuppressWarnings({ "unchecked", "unused" })
    @Deprecated
    private static TemplateSequenceModel toSimpleSequence(TemplateModel object, ObjectWrapper objectWrapper) throws TemplateModelException {
        if (object instanceof TemplateSequenceModel) {
            return (TemplateSequenceModel) object;
        }
        else if (object instanceof WrapperTemplateModel) {
            WrapperTemplateModel wrapperModel = (WrapperTemplateModel) object;
            // WARN: bypasses auto-escaping
            Object wrappedObject = wrapperModel.getWrappedObject();
            if (wrappedObject instanceof List) {
                return DefaultListAdapter.adapt((List<Object>) wrappedObject, (RichObjectWrapper) objectWrapper);
            }
            else if (wrappedObject instanceof Object[]) {
                return DefaultArrayAdapter.adapt((Object[]) wrappedObject, (ObjectWrapperAndUnwrapper) objectWrapper);
            }
            else if (wrappedObject instanceof Set) {
                throw new UnsupportedOperationException("Not yet implemented");
            }
            else if (wrappedObject instanceof Collection) {
                throw new UnsupportedOperationException("Not yet implemented");
            }
            else if (wrappedObject instanceof Iterable) {
                throw new UnsupportedOperationException("Not yet implemented");
            }
            else {
                throw new TemplateModelException("Cannot convert bean-wrapped object of type " + (object != null ? object.getClass() : "null") + " to simple sequence");
            }
        } else if (object instanceof TemplateCollectionModel) {
            TemplateCollectionModel collModel = (TemplateCollectionModel) object;
            SimpleSequence res = new SimpleSequence(objectWrapper);
            TemplateModelIterator it = collModel.iterator();
            while(it.hasNext()) {
                res.add(it.next());
            }
            return res;
        } else {
            throw new TemplateModelException("Cannot convert object of type " + (object != null ? object.getClass() : "null") + " to simple sequence");
        }
    }

    /**
     * Adds all the elements in the given collection to a new set.
     * @deprecated 2019-01-28: objectWrapper is not necessary.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    @Deprecated
    public static <T> Set<T> toSet(TemplateModel object, ObjectWrapper objectWrapper) throws TemplateModelException {
        return toSet(object);
    }

    /**
     * Adds all the elements in the given collection to a new set.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    @SuppressWarnings("unchecked")
    public static <T> Set<T> toSet(TemplateModel object) throws TemplateModelException {
        if (object instanceof WrapperTemplateModel && ((WrapperTemplateModel) object).getWrappedObject() instanceof Set) {
            return (Set<T>) ((WrapperTemplateModel) object).getWrappedObject();
        } else if (object instanceof TemplateCollectionModel) {
            return toSet((TemplateCollectionModel) object);
        } else if (object instanceof TemplateSequenceModel) {
            return toSet((TemplateSequenceModel) object);
        } else {
            throw new TemplateModelException("Cannot convert object of type " + (object != null ? object.getClass() : "null") + " to set");
        }
    }

    /**
     * Adds all the elements in the given collection to a new set.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T> Set<T> toSet(TemplateCollectionModel object) throws TemplateModelException {
        // would be safer to let the wrapper do it, but we know it's just a BeanModel in Ofbiz so we can optimize.
        TemplateCollectionModel collModel = (TemplateCollectionModel) object;
        Set<Object> res = new HashSet<>();
        TemplateModelIterator it = collModel.iterator();
        while(it.hasNext()) {
            res.add(LangFtlUtil.unwrapAlways(it.next()));
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new set.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T> Set<T> toSet(TemplateSequenceModel object) throws TemplateModelException {
        TemplateSequenceModel seqModel = (TemplateSequenceModel) object;
        Set<Object> res = new HashSet<>();
        for(int i=0; i < seqModel.size(); i++) {
            res.add(LangFtlUtil.unwrapAlways(seqModel.get(i)));
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new set, as TemplateModels.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T extends TemplateModel> Set<T> toSetNoUnwrap(TemplateCollectionModel object) throws TemplateModelException {
        // would be safer to let the wrapper do it, but we know it's just a BeanModel in Ofbiz so we can optimize.
        TemplateCollectionModel collModel = (TemplateCollectionModel) object;
        Set<Object> res = new HashSet<>();
        TemplateModelIterator it = collModel.iterator();
        while(it.hasNext()) {
            res.add(it.next());
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new set, as TemplateModels.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T extends TemplateModel> Set<T> toSetNoUnwrap(TemplateSequenceModel object) throws TemplateModelException {
        TemplateSequenceModel seqModel = (TemplateSequenceModel) object;
        Set<Object> res = new HashSet<>();
        for(int i=0; i < seqModel.size(); i++) {
            res.add(seqModel.get(i));
        }
        return UtilGenerics.cast(res);
    }


    /**
     * Adds all the elements in the given collection to a new list.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T> List<T> toList(TemplateCollectionModel collModel) throws TemplateModelException {
        // would be safer to let the wrapper do it, but we know it's just a BeanModel in Ofbiz so we can optimize.
        List<Object> res = new ArrayList<>();
        TemplateModelIterator it = collModel.iterator();
        while(it.hasNext()) {
            res.add(LangFtlUtil.unwrapAlways(it.next()));
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new list.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T> List<T> toList(TemplateSequenceModel seqModel) throws TemplateModelException {
        List<Object> res = new ArrayList<>();
        for(int i=0; i < seqModel.size(); i++) {
            res.add(LangFtlUtil.unwrapAlways(seqModel.get(i)));
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new list, as TemplateModels.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T extends TemplateModel> List<T> toListNoUnwrap(TemplateCollectionModel collModel) throws TemplateModelException {
        // would be safer to let the wrapper do it, but we know it's just a BeanModel in Ofbiz so we can optimize.
        List<Object> res = new ArrayList<>();
        TemplateModelIterator it = collModel.iterator();
        while(it.hasNext()) {
            res.add(it.next());
        }
        return UtilGenerics.cast(res);
    }

    /**
     * Adds all the elements in the given collection to a new list, as TemplateModels.
     * <p>
     * NOTE: This method does NOT handle string escaping explicitly; you may want {@link #toStringSet} instead.
     */
    public static <T extends TemplateModel> List<T> toListNoUnwrap(TemplateSequenceModel seqModel) throws TemplateModelException {
        List<Object> res = new ArrayList<>();
        for(int i=0; i < seqModel.size(); i++) {
            res.add(seqModel.get(i));
        }
        return UtilGenerics.cast(res);
    }

    /*
     * DEV NOTE: This has been removed along with all code that relied on it. it adds too much liability.
     * for now there are no more places we need it.
     *
     * Same as Freemarker's ?is_directive.
     * <p>
     * <em>NOTE:</em> This <em>must</em> have the exact same behavior as Freemarker's ?is_directive.
     * Please refer to Freemarker source code.
     * Unfortunately there is no evident way of reusing their code from here...
     * <p>
     * <strong>WARNING:</strong> FIXME: This currently refers to the FTL freemarker.core.Macro class, which is set
     * to change at any time. this needs a better solution!!!
     */
    /*
    public static boolean isDirective(Object object) {
        return (object instanceof TemplateTransformModel || object instanceof freemarker.core.Macro || object instanceof TemplateDirectiveModel);
    }
    */

    /**
     * Adds to simple hash from source map.
     * <p>
     * <em>WARN</em>: This is not BeanModel-aware (complex map).
     */
    public static void addToSimpleMap(SimpleHash dest, TemplateHashModelEx source) throws TemplateModelException {
        TemplateCollectionModel keysModel = source.keys();
        TemplateModelIterator modelIt = keysModel.iterator();
        while(modelIt.hasNext()) {
            String key = getAsStringNonEscaping((TemplateScalarModel) modelIt.next());
            dest.put(key, source.get(key));
        }
    }

    public static void addToSimpleMap(SimpleHash dest, TemplateHashModel source, Set<String> keys) throws TemplateModelException {
        for(String key : keys) {
            dest.put(key, source.get(key));
        }
    }

    /**
     * Adds the still-wrapped TemplateModels in hash to a java Map.
     * <p>
     * <em>WARN</em>: This is not BeanModel-aware (complex map).
     */
    public static void addModelsToMap(Map<String, ? super TemplateModel> dest, TemplateHashModelEx source) throws TemplateModelException {
        TemplateCollectionModel keysModel = source.keys();
        TemplateModelIterator modelIt = keysModel.iterator();
        while(modelIt.hasNext()) {
            String key = getAsStringNonEscaping((TemplateScalarModel) modelIt.next());
            dest.put(key, source.get(key));
        }
    }

    public static void addModelsToMap(Map<String, ? super TemplateModel> dest, TemplateHashModel source, Set<String> keys) throws TemplateModelException {
        for(String key : keys) {
            dest.put(key, source.get(key));
        }
    }

    /**
     * Makes a simple hash from source map; only specified keys.
     * <p>
     * <em>WARN</em>: This is not BeanModel-aware (complex map).
     */
    public static SimpleHash makeSimpleMap(TemplateHashModel map, Set<String> keys, ObjectWrapper objectWrapper) throws TemplateModelException {
        SimpleHash res = new SimpleHash(objectWrapper);
        addToSimpleMap(res, map, keys);
        return res;
    }

    public static SimpleHash makeSimpleMap(TemplateHashModel map, TemplateCollectionModel keys, ObjectWrapper objectWrapper) throws TemplateModelException {
        SimpleHash res = new SimpleHash(objectWrapper);
        addToSimpleMap(res, map, LangFtlUtil.toStringSet(keys));
        return res;
    }

    public static SimpleHash makeSimpleMap(TemplateHashModel map, TemplateSequenceModel keys, ObjectWrapper objectWrapper) throws TemplateModelException {
        SimpleHash res = new SimpleHash(objectWrapper);
        addToSimpleMap(res, map, LangFtlUtil.toStringSet(keys));
        return res;
    }

    public static Map<String, TemplateModel> makeModelMap(TemplateHashModelEx source) throws TemplateModelException {
        Map<String, TemplateModel> map = new HashMap<>();
        addModelsToMap(map, source);
        return map;
    }

    public static Map<String, Object> makeModelObjectMap(TemplateHashModelEx source) throws TemplateModelException {
        Map<String, Object> map = new HashMap<>();
        addModelsToMap(map, source);
        return map;
    }

    /**
     * To string set.
     * <p>
     * WARN: Bypasses auto-escaping, caller handles.
     * (e.g. the object wrapper used to rewrap the result).
     */
    public static Set<String> toStringSet(TemplateCollectionModel collModel) throws TemplateModelException {
        Set<String> set = new HashSet<String>();
        TemplateModelIterator modelIt = collModel.iterator();
        while(modelIt.hasNext()) {
            set.add(getAsStringNonEscaping((TemplateScalarModel) modelIt.next()));
        }
        return set;
    }

    /**
     * Add to string set.
     * <p>
     * WARN: bypasses auto-escaping, caller handles.
     * (e.g. the object wrapper used to rewrap the result).
     */
    public static void addToStringSet(Set<String> dest, TemplateCollectionModel collModel) throws TemplateModelException {
        TemplateModelIterator modelIt = collModel.iterator();
        while(modelIt.hasNext()) {
            dest.add(getAsStringNonEscaping((TemplateScalarModel) modelIt.next()));
        }
    }

    /**
     * To string set.
     * <p>
     * WARN: bypasses auto-escaping, caller handles.
     * (e.g. the object wrapper used to rewrap the result).
     */
    public static Set<String> toStringSet(TemplateSequenceModel seqModel) throws TemplateModelException {
        Set<String> set = new HashSet<String>();
        for(int i=0; i < seqModel.size(); i++) {
            set.add(getAsStringNonEscaping((TemplateScalarModel) seqModel.get(i)));
        }
        return set;
    }

    /**
     * Add to string set.
     * <p>
     * WARN: bypasses auto-escaping, caller handles.
     * (e.g. the object wrapper used to rewrap the result).
     */
    public static void addToStringSet(Set<String> dest, TemplateSequenceModel seqModel) throws TemplateModelException {
        for(int i=0; i < seqModel.size(); i++) {
            dest.add(getAsStringNonEscaping(((TemplateScalarModel) seqModel.get(i))));
        }
    }

    /**
     * Combines two maps with the given operator into a new hash.
     */
    public static TemplateHashModelEx combineMaps(TemplateHashModelEx first, TemplateHashModelEx second, SetOperations ops,
            ObjectWrapper objectWrapper) throws TemplateModelException {
        SimpleHash res = new SimpleHash(objectWrapper);
        if (ops == null || ops == SetOperations.UNION) {
            // this is less efficient than freemarker + operator, but provides the "alternative" implementation, so have choice
            addToSimpleMap(res, first);
            addToSimpleMap(res, second);
        }
        else if (ops == SetOperations.INTERSECT) {
            Set<String> intersectKeys = toStringSet(second.keys());
            intersectKeys.retainAll(toStringSet(first.keys()));
            addToSimpleMap(res, second, intersectKeys);
        }
        else if (ops == SetOperations.DIFFERENCE) {
            Set<String> diffKeys = toStringSet(first.keys());
            diffKeys.removeAll(toStringSet(second.keys()));
            addToSimpleMap(res, first, diffKeys);
        }
        else {
            throw new TemplateModelException("Unsupported combineMaps operation");
        }
        return res;
    }

    /**
     * Gets collection as a keys.
     * <p>
     * WARN: This bypasses auto-escaping in all cases. Caller must decide how to handle.
     * (e.g. the object wrapper used to rewrap the result).
     */
    public static Set<String> getAsStringSet(TemplateModel model) throws TemplateModelException {
        Set<String> exKeys = null;
        if (model != null) {
            if (model instanceof BeanModel && ((BeanModel) model).getWrappedObject() instanceof Set) {
                // WARN: bypasses auto-escaping
                exKeys = UtilGenerics.cast(((BeanModel) model).getWrappedObject());
            }
            else if (model instanceof TemplateCollectionModel) {
                exKeys = new HashSet<String>();
                TemplateModelIterator keysIt = ((TemplateCollectionModel) model).iterator();
                while(keysIt.hasNext()) {
                    exKeys.add(getAsStringNonEscaping((TemplateScalarModel) keysIt.next()));
                }
            }
            else if (model instanceof TemplateSequenceModel) {
                TemplateSequenceModel seqModel = (TemplateSequenceModel) model;
                exKeys = new HashSet<String>(seqModel.size());
                for(int i=0; i < seqModel.size(); i++) {
                    exKeys.add(getAsStringNonEscaping((TemplateScalarModel) seqModel.get(i)));
                }
            }
            else {
                throw new TemplateModelException("Include/exclude keys argument not a collection or set of strings");
            }
        }
        return exKeys;
    }

    public static void addToSimpleList(SimpleSequence dest, TemplateCollectionModel source) throws TemplateModelException {
        TemplateModelIterator it = source.iterator();
        while(it.hasNext()) {
            dest.add(it.next());
        }
    }

    public static void addToSimpleList(SimpleSequence dest, TemplateSequenceModel source) throws TemplateModelException {
        for(int i=0; i < source.size(); i++) {
            dest.add(source.get(0));
        }
    }

    public static void addToSimpleList(SimpleSequence dest, TemplateModel source) throws TemplateModelException {
        if (source instanceof TemplateCollectionModel) {
            addToSimpleList(dest, (TemplateCollectionModel) source);
        }
        else if (source instanceof TemplateSequenceModel) {
            addToSimpleList(dest, (TemplateSequenceModel) source);
        }
        else {
            throw new TemplateModelException("Can't add to simple list from source type (non-list type): " + source.getClass());
        }
    }

    /**
     * Puts all values in hash into FTL variables, decided by a varHandler.
     * <p>
     * TODO: replace tests with a filter class similar to FtlVarHandler.
     * <p>
     * @see copyMapToSimple
     */
    public static void varsPutAll(TemplateHashModel hashModel, Set<String> inExKeys, Boolean include,
            FtlVarHandler varHandler, Environment env) throws TemplateModelException {
        if (include == Boolean.TRUE) {
            if (inExKeys == null) {
                inExKeys = new HashSet<String>();
            }
            for(String key : inExKeys) {
                TemplateModel valueModel = hashModel.get(key);
                if (inExKeys.contains(key)) {
                    varHandler.setVariable(key, valueModel);
                }
            }
        }
        else if (include == null || inExKeys == null || inExKeys.isEmpty()) {
            if (!(hashModel instanceof TemplateHashModelEx)) {
                throw new TemplateModelException("Hash to copy does not support ?keys");
            }

            TemplateCollectionModel keys = ((TemplateHashModelEx) hashModel).keys();
            TemplateModelIterator keysIt = keys.iterator();
            while(keysIt.hasNext()) {
                String key = getAsStringNonEscaping((TemplateScalarModel) keysIt.next());
                varHandler.setVariable(key, hashModel.get(key));
            }
        }
        else {
            if (!(hashModel instanceof TemplateHashModelEx)) {
                throw new TemplateModelException("Hash to copy does not support ?keys");
            }

            TemplateCollectionModel keys = ((TemplateHashModelEx) hashModel).keys();
            TemplateModelIterator keysIt = keys.iterator();
            while(keysIt.hasNext()) {
                String key = getAsStringNonEscaping((TemplateScalarModel) keysIt.next());
                TemplateModel valueModel = hashModel.get(key);
                if (!inExKeys.contains(key)) {
                    varHandler.setVariable(key, valueModel);
                }
            }
        }
    }

    /**
     * Puts all values in hash into FTL globals (#global).
     * <p>
     * @see copyMapToSimple
     */
    public static void globalsPutAll(TemplateHashModel hashModel, Set<String> inExKeys, Boolean include, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, inExKeys, include, new GlobalFtlVarHandler(env), env);
    }

    /**
     * Puts all values in hash into FTL globals (#global).
     * <p>
     * @see copyMapToSimple
     */
    public static void globalsPutAll(TemplateHashModelEx hashModel, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, null, null, new GlobalFtlVarHandler(env), env);
    }

    /**
     * Puts all values in hash into FTL current namespace vars (#assign).
     * <p>
     * @see copyMapToSimple
     */
    public static void varsPutAll(TemplateHashModel hashModel, Set<String> inExKeys, Boolean include, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, inExKeys, include, new CurrentFtlVarHandler(env), env);
    }

    public static void varsPutAll(TemplateHashModelEx hashModel, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, null, null, new CurrentFtlVarHandler(env), env);
    }

    /**
     * Puts all values in hash into FTL locals (#local).
     * <p>
     * @see copyMapToSimple
     */
    public static void localsPutAll(TemplateHashModel hashModel, Set<String> inExKeys, Boolean include, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, inExKeys, include, new LocalFtlVarHandler(env), env);
    }

    public static void localsPutAll(TemplateHashModelEx hashModel, Environment env) throws TemplateModelException {
        varsPutAll(hashModel, null, null, new LocalFtlVarHandler(env), env);
    }


    /**
     * Returns the given model as string, bypassing auto-escaping done by EscapingModels.
     * <p>
     * WARN (TODO?: REVIEW?): this can crash when model is CollectionModel or MapModel, childs of TemplateScalarModel.
     * we let it crash because non-strict typing may be dangerous and hide errors...
     *
     * @see org.ofbiz.webapp.ftl.EscapingModel
     */
    public static String getAsStringNonEscaping(TemplateScalarModel model) throws TemplateModelException {
        if (model instanceof EscapingModel) {
            return (String) ((EscapingModel) model).getWrappedObject();
        } else {
            return model.getAsString();
        }
    }

    /**
     * Returns the given model as string, optionally bypassing auto-escaping done by EscapingModels.
     *
     * @see org.ofbiz.webapp.ftl.EscapingModel
     */
    public static String getAsString(TemplateScalarModel model, boolean nonEscaping) throws TemplateModelException {
        if (nonEscaping && (model instanceof EscapingModel)) {
            return (String) ((EscapingModel) model).getWrappedObject();
        } else {
            return model.getAsString();
        }
    }

    /**
     * Returns the given model as string, optionally bypassing auto-escaping done by EscapingModels;
     * if not a string, calles getAsString on it.
     * <p>
     * NOTE: this behaves similar to {@link #toRawString}, but returns a String.
     * They are almost the same.
     *
     * @see org.ofbiz.webapp.ftl.EscapingModel
     */
    public static String getAsOrToString(TemplateScalarModel model, boolean nonEscaping) throws TemplateModelException {
        if (nonEscaping && (model instanceof EscapingModel)) {
            Object value = ((EscapingModel) model).getWrappedObject();
            if (value instanceof String || value == null) {
                return (String) value;
            } else {
                return model.getAsString();
            }
        } else {
            return model.getAsString();
        }
    }

    /**
     * Standard/"dumb" (re-)wrapping method. will deep-unwrap the value if necessary (if TemplateModel).
     * <p>
     * Failure to unwrap TemplateModel first will throw exception.
     */
    public static TemplateModel wrapObjectStd(Object value, String wrapper, Environment env) throws TemplateModelException {
        return ObjectWrapperUtil.getObjectWrapperByName(wrapper, env).wrap(unwrap(value));
    }

    public static TemplateModel wrapObjectStd(Object value, String wrapper) throws TemplateModelException {
        return wrapObjectStd(value, wrapper, FreeMarkerWorker.getCurrentEnvironment());
    }

    /**
     * Full-featured rewrapObject implementation. Rewraps objects with different wrappers and options.
     * <p>
     * TODO: 2016-10-20: this is currently very limited to the cases which we currently have in scipio,
     * but other cases may pop up anytime.
     * The non-deep is currently mainly handled by toSimpleMap in an imperfect way.
     */
    public static Object rewrapObject(TemplateModel model, WrappingOptions opts, Environment env) throws TemplateModelException {
        if (opts.rewrapMode.deep) {
            if (opts.rewrapMode.always) {
                // simplest case (default), just fully unwrap and rewrap
                Object unwrapped = LangFtlUtil.unwrapAlways(model);
                return opts.targetWrapper.wrap(unwrapped);
            } else {
                // TODO
                throw new UnsupportedOperationException();
            }
        } else {
            if (opts.rewrapMode.always) {
                // TODO
                throw new UnsupportedOperationException();
            } else {
                // TODO
                throw new UnsupportedOperationException();
            }
        }
    }

    /* obsoleted, remove later when rewrapObject is fully implemented
    public static Object rewrapMap(TemplateHashModel model, WrappingOptions opts, Environment env) throws TemplateModelException {
        RewrapMode mode = opts.rewrapMode;
        if (mode.always) {
            return alwaysRewrapMap(model, opts, env);
        } else {

            if (mode.deep) {
                // WARN: Here we make one VERY delicate optimization:
                // if the map is a simple hash, we do nothing to it.
                // In theory this is WRONG but it works in most practical cases.
                // Caller can force if it doesn't work right in his case.
                if (model instanceof SimpleHash) {
                    return model;
                }

                // FIXME: Here we are forced to rewrap in most cases because Freemarker interface
                // does not allow inspecting which object wrapper an object is using!
                return alwaysRewrapMap(model, mode, env, curObjectWrapper);
            } else {
                // Shallow re-wrap to simple, non-(necessarily-)raw map.
                // This is the rare case we can currently optimize...
                return toSimpleMap(model, mode.copy, curObjectWrapper);
            }
        }
    }

    public static Object alwaysRewrapMap(TemplateHashModel model, WrappingOptions opts, Environment env) throws TemplateModelException {
        Map<?, ?> unwrapped = (Map<?, ?>) LangFtlUtil.unwrapAlways(model);
        return

        if (opts.rewrapMode.raw) {
            if (opts.rewrapMode.deep) {
                ObjectWrapper modelWrapper = mode.copy ?
                        LangFtlUtil.getSimpleTypeCopyingNonEscapingObjectWrapper(curObjectWrapper) : LangFtlUtil.getSimpleTypeNonEscapingObjectWrapper(curObjectWrapper);
                return modelWrapper.wrap(unwrapped);
            } else {
                // Can't do raw without doing deep
                throw new TemplateModelException("Scipio: rewrapMap mode unsupported");
            }
        } else {
            if (opts.rewrapMode.deep) {
                // TODO: This mode is desirable but it requires implementing a new DefaultObjectWrapper
                // that would preserve the wrapping mode curObjectWrapper is doing.
                // as-is, to do deep, you must also do raw.
                throw new TemplateModelException("Scipio: rewrapMap mode not yet implemented");
            } else {
                return opts.targetWrapper.wrap(unwrapped);
            }
        }
    }
    */

    public static boolean isNullOrEmptyString(TemplateModel model) throws TemplateModelException {
        // this doesn't work out: TemplateScalarModel.EMPTY_STRING.equals(model)
        return (model == null || (model instanceof TemplateScalarModel && ((TemplateScalarModel) model).getAsString().isEmpty()));
    }

    public static Locale getLocale(TemplateModel model) throws TemplateModelException {
        if (isNullOrEmptyString(model)) {
            return null;
        }
        if (!(model instanceof WrapperTemplateModel)) {
            throw new TemplateModelException("Invalid locale object (not WrapperTemplateModel)");
        }
        return (Locale) ((WrapperTemplateModel) model).getWrappedObject();
    }

    public static TimeZone getTimeZone(TemplateModel model) throws TemplateModelException {
        if (isNullOrEmptyString(model)) {
            return null;
        }
        if (!(model instanceof WrapperTemplateModel)) {
            throw new TemplateModelException("Invalid locale object (not WrapperTemplateModel)");
        }
        return (TimeZone) ((WrapperTemplateModel) model).getWrappedObject();
    }


    public static Template makeFtlCodeTemplate(String ftlCode) throws TemplateModelException {
        Reader templateReader = new StringReader(ftlCode);
        try {
            return new Template(new UID().toString(), templateReader, FreeMarkerWorker.getDefaultOfbizConfig());
        } catch (IOException e) {
            throw new TemplateModelException(e);
        } finally {
            try {
                templateReader.close();
            } catch (IOException e) {
                Debug.logError(e, module); // don't propagate
            }
        }
    }

    public static void execFtlCode(Template ftlCode, Environment env) throws TemplateModelException {
        try {
            FreeMarkerWorker.includeTemplate(ftlCode, env);
        } catch (TemplateException e) {
            throw new TemplateModelException(e);
        } catch (IOException e) {
            throw new TemplateModelException(e);
        }
    }

    /**
     * WARN: extremely slow, should be avoided! decompose into makeTemplate + executeFtlCode and cache the template instead.
     */
    public static void execFtlCode(String ftlCode, Environment env) throws TemplateModelException {
        execFtlCode(makeFtlCodeTemplate(ftlCode), env);
    }

    /**
     * Executes an arbitrary FTL built-in.
     */
    public static TemplateModel execBuiltIn(String builtInName, TemplateModel value, TemplateModel[] builtInArgs, Environment env) throws TemplateModelException {
        final int argCount = (builtInArgs != null) ? builtInArgs.length : 0;
        return execBuiltIn(getBuiltInCall(builtInName, argCount, env), value, builtInArgs, env);
    }

    /**
     * Executes an arbitrary FTL built-in.
     */
    public static TemplateModel execBuiltIn(String builtInName, TemplateModel value, Environment env) throws TemplateModelException {
        return execBuiltIn(builtInName, value, null, env);
    }

    /**
     * Gets an arbitrary FTL built-in call - non-abstracted version (for optimization only!).
     */
    public static Template getBuiltInCall(String builtInName, int argCount, Environment env) throws TemplateModelException {
        final String cacheKey = builtInName + ":" + argCount;
        Template builtInCall = builtInCalls.get(cacheKey);
        if (builtInCall == null) {
            // NOTE: there's no _real_ need to synchronize on this. if two templates are built for one builtin its ok, temporary only.
            if (argCount > 0) {
                String argVarsStr = "";
                for(int i=0; i < argCount; i++) {
                    argVarsStr += ",_scpEbiArg"+i;
                }
                builtInCall = makeFtlCodeTemplate("<#assign _scpEbiRes = _scpEbiVal?" + builtInName + "(" + argVarsStr.substring(1) + ")>");
            } else {
                builtInCall = makeFtlCodeTemplate("<#assign _scpEbiRes = _scpEbiVal?" + builtInName + ">");
            }
            builtInCalls.put(cacheKey, builtInCall);
        }
        return builtInCall;
    }

    /**
     * Executes an arbitrary FTL built-in - non-abstracted version (for optimization only!).
     */
    public static TemplateModel execBuiltIn(Template builtInCall, TemplateModel value, TemplateModel[] builtInArgs, Environment env) throws TemplateModelException {
        final int argCount = (builtInArgs != null) ? builtInArgs.length : 0;
        env.setVariable("_scpEbiVal", value);
        for(int i=0; i < argCount; i++) {
            env.setVariable("_scpEbiArg"+i, builtInArgs[i]);
        }
        execFtlCode(builtInCall, env);
        return env.getVariable("_scpEbiRes");
    }

    public static TemplateScalarModel execStringBuiltIn(TemplateModel value, Environment env) throws TemplateModelException {
        if (stringBuiltInCall == null) {
            // NOTE: no real need for synchronize here
            stringBuiltInCall = getBuiltInCall("string", 0, env);
        }
        return (TemplateScalarModel) execBuiltIn(stringBuiltInCall, value, null, env);
    }

    /**
     * Executes an arbitrary FTL function.
     */
    public static TemplateModel execFunction(String functionName, TemplateModel[] args, Environment env) throws TemplateModelException {
        final int argCount = (args != null) ? args.length : 0;
        return execFunction(getFunctionCall(functionName, argCount, env), args, env);
    }

    /**
     * Executes an arbitrary FTL function.
     */
    public static TemplateModel execFunction(String functionName, Environment env) throws TemplateModelException {
        return execFunction(getFunctionCall(functionName, 0, env), null, env);
    }

    /**
     * Gets an arbitrary FTL function call - non-abstracted version (for optimization only!).
     */
    public static Template getFunctionCall(String functionName, int argCount, Environment env) throws TemplateModelException {
        final String cacheKey = functionName + ":" + argCount;
        Template functionCall = functionCalls.get(cacheKey);
        if (functionCall == null) {
            String argVarsStr = "";
            for(int i=0; i < argCount; i++) {
                argVarsStr += ",_scpEfnArg"+i;
            }
            if (argCount > 0) {
                argVarsStr = argVarsStr.substring(1);
            }
            functionCall = makeFtlCodeTemplate("<#assign _scpEfnRes = " + functionName + "(" + argVarsStr + ")>");
            functionCalls.put(cacheKey, functionCall);
        }
        return functionCall;
    }

    /**
     * Executes an arbitrary FTL function - non-abstracted version (for optimization only!).
     */
    public static TemplateModel execFunction(Template functionCall, TemplateModel[] args, Environment env) throws TemplateModelException {
        final int argCount = (args != null) ? args.length : 0;
        for(int i=0; i < argCount; i++) {
            env.setVariable("_scpEfnArg"+i, args[i]);
        }
        execFtlCode(functionCall, env);
        return env.getVariable("_scpEfnRes");
    }

    /**
     * Executes an arbitrary FTL function - non-abstracted version (for optimization only!).
     */
    public static TemplateModel execFunction(Template functionCall, Environment env) throws TemplateModelException {
        return execFunction(functionCall, null, env);
    }


    /**
     * Executes an arbitrary FTL macro.
     */
    public static void execMacro(String macroName, Map<String, TemplateModel> args, Environment env) throws TemplateModelException {
        execMacro(getMacroCall(macroName, (args != null ? args.keySet() : null), env), args, env);
    }

    /**
     * Executes an arbitrary FTL macro.
     */
    public static void execMacro(String macroName, Environment env) throws TemplateModelException {
        execMacro(getMacroCall(macroName, null, env), null, env);
    }

    /**
     * Gets an arbitrary FTL macro call - non-abstracted version (for optimization only!).
     */
    public static Template getMacroCall(String macroName, Collection<String> argNames, Environment env) throws TemplateModelException {
        final String cacheKey = macroName + (argNames != null ? ":" + StringUtils.join(argNames, ":") : "");
        Template macroCall = macroCalls.get(cacheKey);
        if (macroCall == null) {
            String argVarsStr = "";
            if (argNames != null) {
                for(String argName : argNames) {
                    argVarsStr += " " + argName + "=_scpEfnArg_"+argName;
                }
            }
            macroCall = makeFtlCodeTemplate("<@" + macroName + argVarsStr + "/>");
            macroCalls.put(cacheKey, macroCall);
        }
        return macroCall;
    }

    /**
     * Executes an arbitrary FTL macro - non-abstracted version (for optimization only!).
     */
    public static void execMacro(Template macroCall, Map<String, TemplateModel> args, Environment env) throws TemplateModelException {
        if (args != null) {
            for(Map.Entry<String, TemplateModel> entry : args.entrySet()) {
                env.setVariable("_scpEfnArg_"+entry.getKey(), entry.getValue());
            }
        }
        execFtlCode(macroCall, env);
    }

    /**
     * Executes an arbitrary FTL macro - non-abstracted version (for optimization only!).
     */
    public static void execMacro(Template macroCall, Environment env) throws TemplateModelException {
        execMacro(macroCall, null, env);
    }


    /**
     * Gets a var from main namespace with fallback on globals/data-model, or null if doesn't exit or null.
     * <p>
     * Avoids local variables and emulates a simple Freemarker var read in the main namespace.
     * <p>
     * Similar to {@link freemarker.core.Environment.getVariable(String)} but skips local
     * variables and always main namespace instead of current namespace.
     * <p>
     * NOTE: This probably makes the most sense to call from transforms as a means to read
     * "context/global" variables (using term loosely, while providing possibility for
     * templates to override using both #assign and #global directives.
     */
    public static TemplateModel getMainNsOrGlobalVar(String name, Environment env) throws TemplateModelException {
        TemplateModel result = env.getMainNamespace().get(name);
        if (result == null) {
            result = env.getGlobalVariable(name);
        }
        return result;
    }

    /**
     * Gets a var from current namespace with fallback on globals/data-model, or null if doesn't exit or null.
     * <p>
     * Avoids local variables and emulates a simple Freemarker var read in the current namespace.
     * <p>
     * Similar to {@link freemarker.core.Environment.getVariable(String)} but skips local
     * variables.
     */
    public static TemplateModel getCurrentNsOrGlobalVar(String name, Environment env) throws TemplateModelException {
        TemplateModel result = env.getCurrentNamespace().get(name);
        if (result == null) {
            result = env.getGlobalVariable(name);
        }
        return result;
    }

    public static TemplateBooleanModel toBooleanModel(boolean value, Environment env) throws TemplateModelException {
        return value ? TemplateBooleanModel.TRUE : TemplateBooleanModel.FALSE;
    }

    /**
     * Performs the logical raw string operation on a single value.
     */
    public static TemplateScalarModel toRawString(TemplateModel value, Environment env) throws TemplateModelException {
        if (value instanceof TemplateScalarModel) {
            TemplateScalarModel strModel = (TemplateScalarModel) value;
            String str = getAsStringNonEscaping(strModel);
            return new SimpleScalar(str); // Emulates Freemarker ?string built-in
        } else {
            return execStringBuiltIn(value, env);
        }
    }

    /**
     * Performs the logical {@link #toRawString(TemplateModel, Environment)} operation on a single value, but returns as String type instead
     * of template model.
     */
    public static String toRawJavaString(TemplateModel value, Environment env) throws TemplateModelException {
        if (!(value instanceof TemplateScalarModel)) {
            value = execStringBuiltIn(value, env);
        }
        return getAsStringNonEscaping((TemplateScalarModel) value);
    }

    /**
     * Performs the logical raw string operation on multiple values, concatenating the result.
     */
    public static TemplateScalarModel toRawString(Collection<TemplateModel> values, Environment env) throws TemplateModelException {
        StringBuilder sb = new StringBuilder();
        for(TemplateModel value: values) {
            if (value instanceof TemplateScalarModel) {
                sb.append(getAsStringNonEscaping((TemplateScalarModel) value));
            } else {
                sb.append(execStringBuiltIn(value, env).getAsString());
            }
        }
        return new SimpleScalar(sb.toString());
    }

    /**
     * Adapts a hash model to a Map adapter.
     * <p>
     * NOTE: At current time (2019-01-28), this is best-effort and does not guarantee
     * copies won't be made - see implementation (incomplete). In other words,
     * avoid using anything but the Map.get method.
     */
    public static <V extends TemplateModel> Map<String, V> adaptAsMap(TemplateHashModelEx hash) {
        return new TemplateModelContainer.TemplateModelMap<>(hash);
    }

    /**
     * Gets the wrapped hash's map OR adapts a hash model to a Map adapter if not a wrapper (SimpleHash).
     * <p>
     * NOTE: At current time (2019-01-28), this is best-effort and does not guarantee
     * copies won't be made - see implementation (incomplete). In other words,
     * avoid using anything but the Map.get method.
     */
    @SuppressWarnings("unchecked")
    public static <V extends TemplateModel> Map<String, V> getWrappedOrAdaptAsMap(TemplateHashModelEx hash) {
        if (hash instanceof AdapterTemplateModel) {
            return (Map<String, V>) ((AdapterTemplateModel) hash).getAdaptedObject(Map.class);
        }
        if (hash instanceof WrapperTemplateModel) {
            return (Map<String, V>) ((WrapperTemplateModel) hash).getWrappedObject();
        }
        return adaptAsMap(hash);
    }
}
