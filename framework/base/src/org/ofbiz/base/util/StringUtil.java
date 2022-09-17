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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.ofbiz.base.lang.Appender;

/**
 * Misc String Utility Functions
 *
 */
public class StringUtil {

    public static final StringUtil INSTANCE = new StringUtil();
    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());
    private static final Map<String, Pattern> substitutionPatternMap = createSubstitutionPatternMap();

    private static Map<String, Pattern> createSubstitutionPatternMap() {
        Map<String, Pattern> substitutionPatternMap = new LinkedHashMap<>();  // Preserve insertion order
        substitutionPatternMap.put("&&", Pattern.compile("@and", Pattern.LITERAL));
        substitutionPatternMap.put("||", Pattern.compile("@or", Pattern.LITERAL));
        substitutionPatternMap.put("<=", Pattern.compile("@lteq", Pattern.LITERAL));
        substitutionPatternMap.put(">=", Pattern.compile("@gteq", Pattern.LITERAL));
        substitutionPatternMap.put("<", Pattern.compile("@lt", Pattern.LITERAL));
        substitutionPatternMap.put(">", Pattern.compile("@gt", Pattern.LITERAL));
        return Collections.unmodifiableMap(substitutionPatternMap);
    }

    private static final Pattern listElemDelim = Pattern.compile("\\,\\s"); // SCIPIO

    private StringUtil() {
    }

    public static String internString(String value) {
        return value != null ? value.intern() : null;
    }

    /**
     * Replaces all occurrences of oldString in mainString with newString
     * @param mainString The original string
     * @param oldString The string to replace
     * @param newString The string to insert in place of the old
     * @return mainString with all occurrences of oldString replaced by newString
     */
    public static String replaceString(String mainString, String oldString, String newString) {
        if (mainString == null) {
            return null;
        }
        if (UtilValidate.isEmpty(oldString)) {
            return mainString;
        }
        if (newString == null) {
            newString = "";
        }

        int i = mainString.lastIndexOf(oldString);

        if (i < 0) {
            return mainString;
        }

        StringBuilder mainSb = new StringBuilder(mainString);

        while (i >= 0) {
            mainSb.replace(i, i + oldString.length(), newString);
            i = mainString.lastIndexOf(oldString, i - 1);
        }
        return mainSb.toString();
    }

    /**
     * Creates a single string from a List of strings seperated by a delimiter.
     * @param list a list of strings to join
     * @param delim the delimiter character(s) to use. (null value will join with no delimiter)
     * @return a String of all values in the list seperated by the delimiter
     */
    public static String join(List<?> list, String delim) {
        return join ((Collection<?>) list, delim);
    }

    /**
     * Creates a single string from a Collection of strings seperated by a delimiter.
     * @param col a collection of strings to join
     * @param delim the delimiter character(s) to use. (null value will join with no delimiter)
     * @return a String of all values in the collection seperated by the delimiter
     */
    public static String join(Collection<?> col, String delim) {
        if (UtilValidate.isEmpty(col)) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        Iterator<?> i = col.iterator();

        while (i.hasNext()) {
            buf.append(i.next());
            if (i.hasNext()) {
                buf.append(delim);
            }
        }
        return buf.toString();
    }

    /**
     * Splits a String on a delimiter into a List of Strings.
     * @param str the String to split
     * @param delim the delimiter character(s) to join on (null will split on whitespace)
     * @return a list of Strings
     */
    public static List<String> split(String str, String delim) {
        List<String> splitList = null;
        StringTokenizer st;

        if (str == null) {
            return null;
        }

        st = (delim != null? new StringTokenizer(str, delim): new StringTokenizer(str));

        if (st.hasMoreTokens()) {
            splitList = new ArrayList<String>(); // SCIPIO: switched to ArrayList (default capacity is usually good here)

            while (st.hasMoreTokens()) {
                splitList.add(st.nextToken());
            }
        }
        return splitList;
    }

    /**
     * Splits a String on a delimiter into a List of Strings.
     * @param str the String to split
     * @param delim the delimiter character(s) to join on (null will split on whitespace)
     * @param limit see String.split() method
     * @return a list of Strings
     */
    public static List<String> split(String str, String delim, int limit) {
        List<String> splitList = null;
        String[] st = null;

        if (str == null) {
            return splitList;
        }

        // SCIPIO: 2018-10-18: switched to String.split because is better optimized for single-char case than Pattern.compile
        //if (delim != null) st = Pattern.compile(delim).split(str, limit);
        if (delim != null) {
            st = str.split(delim, limit);
        } else {
            st = str.split("\\s", limit); // SCIPIO: 2018-10-18: fixed missing limit in this case (stock bug)
        }

        if (st != null && st.length > 0) {
            // SCIPIO: NOTE: Can't safely do Arrays.asList from here, because throws exception if tries to add items,
            // and can't assume a caller won't try (old interface); but assume few callers do that and set capacity tight
            splitList = new ArrayList<String>(Arrays.asList(st)); // SCIPIO: switched to ArrayList and copy constructor
            //for (int i=0; i < st.length; i++) splitList.add(st[i]);
        }

        return splitList;
    }

    /**
     * Encloses each of a List of Strings in quotes.
     * @param list List of String(s) to quote.
     */
    public static List<String> quoteStrList(List<String> list) {
        List<String> tmpList = list;

        list = new ArrayList<String>(list.size()); // SCIPIO: switched to ArrayList
        for (String str: tmpList) {
            str = "'" + str + "'";
            list.add(str);
        }
        return list;
    }

    /**
     * Creates a Map from an encoded name/value pair string
     * @param str The string to decode and format
     * @param delim the delimiter character(s) to join on (null will split on whitespace)
     * @param trim Trim whitespace off fields
     * @return a Map of name/value pairs
     */
    public static Map<String, String> strToMap(String str, String delim, boolean trim) {
        return strToMap(str, delim, trim, null);

    }

    /**
     * Creates a Map from a name/value pair string
     * @param str The string to decode and format
     * @param delim the delimiter character(s) to join on (null will split on whitespace)
     * @param trim Trim whitespace off fields
     * @param pairsSeparator in case you use not encoded name/value pairs strings
     *        and want to replace "=" to avoid clashes with parameters values in a not encoded URL, default to "="
     * @return a Map of name/value pairs
     */
    public static Map<String, String> strToMap(String str, String delim, boolean trim, String pairsSeparator) {
        if (str == null) {
            return null;
        }
        Map<String, String> decodedMap = new HashMap<>();
        List<String> elements = split(str, delim);
        pairsSeparator = pairsSeparator == null ? "=" : pairsSeparator;

        for (String s: elements) {

            List<String> e = split(s, pairsSeparator);
            if (e.size() != 2) {
                continue;
            }
            String name = e.get(0);
            String value = e.get(1);
            if (trim) {
                if (name != null) {
                    name = name.trim();
                }
                if (value != null) {
                    value = value.trim();
                }
            }

            try {
                if (value != null && name != null) {
                    decodedMap.put(URLDecoder.decode(name, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
                }
            } catch (UnsupportedEncodingException e1) {
                Debug.logError(e1, module);
            }
        }
        return decodedMap;
    }

    /**
     * Creates a Map from an encoded name/value pair string
     * @param str The string to decode and format
     * @param trim Trim whitespace off fields
     * @return a Map of name/value pairs
     */
    public static Map<String, String> strToMap(String str, boolean trim) {
        return strToMap(str, "|", trim);
    }

    /**
     * Creates a Map from an encoded name/value pair string
     * @param str The string to decode and format
     * @param delim the delimiter character(s) to join on (null will split on whitespace)
     * @return a Map of name/value pairs
     */
    public static Map<String, String> strToMap(String str, String delim) {
        return strToMap(str, delim, false);
    }

    /**
     * Creates a Map from an encoded name/value pair string
     * @param str The string to decode and format
     * @return a Map of name/value pairs
     */
    public static Map<String, String> strToMap(String str) {
        return strToMap(str, "|", false);
    }


    /**
     * Creates an encoded String from a Map of name/value pairs (MUST BE STRINGS!)
     * @param map The Map of name/value pairs
     * @return String The encoded String
     */
    public static String mapToStr(Map<? extends Object, ? extends Object> map) {
        if (map == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        boolean first = true;

        for (Map.Entry<? extends Object, ? extends Object> entry: map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();

            if (!(key instanceof String) || !(value instanceof String)) {
                continue;
            }
            String encodedName = null;
            try {
                encodedName = URLEncoder.encode((String) key, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Debug.logError(e, module);
            }
            String encodedValue = null;
            try {
                encodedValue = URLEncoder.encode((String) value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Debug.logError(e, module);
            }

            if (first) {
                first = false;
            } else {
                buf.append("|");
            }

            buf.append(encodedName);
            buf.append("=");
            buf.append(encodedValue);
        }
        return buf.toString();
    }

    /**
     * Reads a String version of a Map (should contain only strings) and creates a new Map.
     * Partial Map elements are skipped: <code>{foo=fooValue, bar=}</code> will contain only
     * the foo element.
     *
     * @param s String value of a Map ({n1=v1, n2=v2})
     * @return new Map
     */
    public static Map<String, String> toMap(String s) {
        Map<String, String> newMap = new HashMap<>();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1);
            // SCIPIO: Pre-compiled delim Pattern, because more than 2 chars (not optimized by String.split)
            //String[] entries = s.split("\\,\\s");
            String[] entries = listElemDelim.split(s);
            for (String entry: entries) {
                String[] nv = entry.split("\\=");
                if (nv.length == 2) {
                    newMap.put(nv[0], nv[1]);
                }
            }
        } else {
            throw new IllegalArgumentException("String is not from Map.toString()");
        }

        return newMap;
    }

    /**
     * Reads a String version of a List (should contain only strings) and creates a new List
     *
     * @param s String value of a Map ({n1=v1, n2=v2})
     * @return new List
     */
    public static List<String> toList(String s) {
        //List<String> newList = new LinkedList<String>(); // SCIPIO: switched to ArrayList (below)
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
            // SCIPIO: Pre-compiled delim Pattern, because more than 2 chars (not optimized by String.split)
            //String[] entries = s.split("\\,\\s");
            String[] entries = listElemDelim.split(s);
            // SCIPIO: NOTE: Can't safely do Arrays.asList from here, because throws exception if tries to add items,
            // and can't assume a caller won't try (old interface); but assume few callers do that and set capacity tight
            List<String> newList = new ArrayList<String>(Arrays.asList(entries)); // SCIPIO: switched to ArrayList and copy constructor
            //for (String entry: entries) {
            //    newList.add(entry);
            //}
            return newList;
        } else {
            throw new IllegalArgumentException("String is not from List.toString()");
        }
    }

    /**
     * Reads a String version of a Set (should contain only strings) and creates a new Set
     *
     * @param s String value of a Map ({n1=v1, n2=v2})
     * @return new List
     */
    public static Set<String> toSet(String s) {
        Set<String> newSet = new LinkedHashSet<>();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
            String[] entries = s.split("\\,\\s");
            for (String entry: entries) {
                newSet.add(entry);
            }
        } else {
            throw new IllegalArgumentException("String is not from Set.toString()");
        }

        return newSet;
    }

    /**
     * Create a Map from a List of keys and a List of values
     * @param keys List of keys
     * @param values List of values
     * @return Map of combined lists
     * @throws IllegalArgumentException When either List is null or the sizes do not equal
     */
    public static <K, V> Map<K, V> createMap(List<K> keys, List<V> values) {
        if (keys == null || values == null || keys.size() != values.size()) {
            throw new IllegalArgumentException("Keys and Values cannot be null and must be the same size");
        }
        Map<K, V> newMap = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            newMap.put(keys.get(i), values.get(i));
        }
        return newMap;
    }

    /** Make sure the string starts with a forward slash but does not end with one; converts back-slashes to forward-slashes; if in String is null or empty, returns zero length string. */
    public static String cleanUpPathPrefix(String prefix) {
        if (UtilValidate.isEmpty(prefix)) {
            return "";
        }

        StringBuilder cppBuff = new StringBuilder(prefix.replace('\\', '/'));

        if (cppBuff.charAt(0) != '/') {
            cppBuff.insert(0, '/');
        }
        if (cppBuff.charAt(cppBuff.length() - 1) == '/') {
            cppBuff.deleteCharAt(cppBuff.length() - 1);
        }
        return cppBuff.toString();
    }

    /** Removes all spaces from a string */
    public static String removeSpaces(String str) {
        return removeRegex(str,"[\\ ]");
    }

    public static String toHexString(byte[] bytes) {
        return new String(Hex.encodeHex(bytes));
    }

    public static String cleanHexString(String str) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) != 32 && str.charAt(i) != ':') {
                buf.append(str.charAt(i));
            }
        }
        return buf.toString();
    }

    public static byte[] fromHexString(String str) {
        str = cleanHexString(str);
        try {
            return Hex.decodeHex(str.toCharArray());
        } catch (DecoderException e) {
            throw new GeneralRuntimeException(e);
        }
    }

    private static char[] hexChar = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    public static int convertChar(char c) {
        if ('0' <= c && c <= '9') {
            return c - '0' ;
        } else if ('a' <= c && c <= 'f') {
            return c - 'a' + 0xa ;
        } else if ('A' <= c && c <= 'F') {
            return c - 'A' + 0xa ;
        } else {
            throw new IllegalArgumentException("Invalid hex character: [" + c + "]");
        }
    }

    public static char[] encodeInt(int i, int j, char digestChars[]) {
        if (i < 16) {
            digestChars[j] = '0';
        }
        j++;
        do {
            digestChars[j--] = hexChar[i & 0xf];
            i >>>= 4;
        } while (i != 0);
        return digestChars;
    }

    /** Removes all non-numbers from str */
    public static String removeNonNumeric(String str) {
        return removeRegex(str,"[\\D]");
    }

    /** Removes all numbers from str */
    public static String removeNumeric(String str) {
        return removeRegex(str,"[\\d]");
    }

    /**
     * @param str
     * @param regex
     * Removes all matches of regex from a str
     */
    public static String removeRegex(String str, String regex) {
        return str.replaceAll(regex, "");
    }

    /**
     * Add the number to the string, keeping (padding to min of original length)
     *
     * @return the new value
     */
    public static String addToNumberString(String numberString, long addAmount) {
        if (numberString == null) {
            return null;
        }
        int origLength = numberString.length();
        long number = Long.parseLong(numberString);
        return padNumberString(Long.toString(number + addAmount), origLength);
    }

    public static String padNumberString(String numberString, int targetMinLength) {
        StringBuilder outStrBfr = new StringBuilder(numberString);
        while (targetMinLength > outStrBfr.length()) {
            outStrBfr.insert(0, '0');
        }
        return outStrBfr.toString();
    }

    /** Converts operator substitutions (@and, @or, etc) back to their original form.
     * <p>OFBiz script syntax provides special forms of common operators to make
     * it easier to embed logical expressions in XML</p>
     * <table border="1" cellpadding="2">
     *   <caption>OFBiz XML operators</caption>
     *   <tr><th>OFBiz operator</th><th>Substitution</th></tr>
     *   <tr><td><strong>@and</strong></td><td>&amp;&amp;</td></tr>
     *   <tr><td><strong>@or</strong></td><td>||</td></tr>
     *   <tr><td><strong>@gt</strong></td><td>&gt;</td></tr>
     *   <tr><td><strong>@gteq</strong></td><td>&gt;=</td></tr>
     *   <tr><td><strong>@lt</strong></td><td>&lt;</td></tr>
     *   <tr><td><strong>@lteq</strong></td><td>&lt;=</td></tr>
     * </table>
     * @param expression The <code>String</code> to convert
     * @return The converted <code>String</code>
     */
    public static String convertOperatorSubstitutions(String expression) {
        String result = expression;
        if (result != null && (result.contains("@"))) {
            for (Map.Entry<String, Pattern> entry: substitutionPatternMap.entrySet()) {
                Pattern pattern = entry.getValue();
                result = pattern.matcher(result).replaceAll(entry.getKey());
            }
            if (Debug.verboseOn()) {
                Debug.logVerbose("Converted " + expression + " to " + result, module);
            }
        }
        return result;
    }

    /**
     * Remove/collapse multiple newline characters
     *
     * @param str string to collapse newlines in
     * @return the converted string
     */
    public static String collapseNewlines(String str) {
        return collapseCharacter(str, '\n');
    }

    /**
     * Remove/collapse multiple spaces
     *
     * @param str string to collapse spaces in
     * @return the converted string
     */
    public static String collapseSpaces(String str) {
        return collapseCharacter(str, ' ');
    }

    /**
     * Remove/collapse multiple characters
     *
     * @param str string to collapse characters in
     * @param c character to collapse
     * @return the converted string
     */
    public static String collapseCharacter(String str, char c) {
        StringBuilder sb = new StringBuilder();
        char last = str.charAt(0);

        for (int i = 0; i < str.length(); i++) {
            char current = str.charAt(i);
            if (i == 0 || current != c || last != c) {
                sb.append(current);
                last = current;
            }
        }

        return sb.toString();
    }

    public static StringWrapper wrapString(String theString) {
        return makeStringWrapper(theString);
    }
    public static StringWrapper makeStringWrapper(String theString) {
        if (theString == null) {
            return null;
        }
        if (theString.length() == 0) {
            return StringWrapper.EMPTY_STRING_WRAPPER;
        }
        return new StringWrapper(theString);
    }

    public static StringBuilder appendTo(StringBuilder sb, Iterable<? extends Appender<StringBuilder>> iterable, String prefix, String suffix, String sep) {
        return appendTo(sb, iterable, prefix, suffix, null, sep, null);
    }

    public static StringBuilder appendTo(StringBuilder sb, Iterable<? extends Appender<StringBuilder>> iterable, String prefix, String suffix, String sepPrefix, String sep, String sepSuffix) {
        Iterator<? extends Appender<StringBuilder>> it = iterable.iterator();
        while (it.hasNext()) {
            if (prefix != null) {
                sb.append(prefix);
            }
            it.next().appendTo(sb);
            if (suffix != null) {
                sb.append(suffix);
            }
            if (it.hasNext() && sep != null) {
                if (sepPrefix != null) {
                    sb.append(sepPrefix);
                }
                sb.append(sep);
                if (sepSuffix != null) {
                    sb.append(sepSuffix);
                }
            }
        }
        return sb;
    }

    public static StringBuilder append(StringBuilder sb, Iterable<? extends Object> iterable, String prefix, String suffix, String sep) {
        return append(sb, iterable, prefix, suffix, null, sep, null);
    }

    public static StringBuilder append(StringBuilder sb, Iterable<? extends Object> iterable, String prefix, String suffix, String sepPrefix, String sep, String sepSuffix) {
        Iterator<? extends Object> it = iterable.iterator();
        while (it.hasNext()) {
            if (prefix != null) {
                sb.append(prefix);
            }
            sb.append(it.next());
            if (suffix != null) {
                sb.append(suffix);
            }
            if (it.hasNext() && sep != null) {
                if (sepPrefix != null) {
                    sb.append(sepPrefix);
                }
                sb.append(sep);
                if (sepSuffix != null) {
                    sb.append(sepSuffix);
                }
            }
        }
        return sb;
    }

    /**
     * A super-lightweight object to wrap a String object. Mainly used with FTL templates
     * to avoid the general HTML auto-encoding that is now done through the Screen Widget.
     */
    public static class StringWrapper {
        public static final StringWrapper EMPTY_STRING_WRAPPER = new StringWrapper("");

        protected String theString;
        protected StringWrapper() { }
        public StringWrapper(String theString) {
            this.theString = theString;
        }

        /**
         * Fairly simple method used for the plus (+) base concatenation in Groovy.
         *
         * @param value
         * @return the wrapped string, plus the value
         */
        public String plus(Object value) {
            return this.theString + value;
        }

        /**
         * @return The String this object wraps.
         */
        @Override
        public String toString() {
            return this.theString;
        }
    }

    /**
     * SCIPIO: Returns the given object as a CharSequence as-if if it is one, otherwise returns
     * the object's {@link Object#toString()} value.
     * <p>
     * NOTE: May be used on {@link Appendable} instances that are expected to hold strings.
     */
    public static CharSequence asCharSequence(Object str) {
        if (str instanceof CharSequence) {
            return (CharSequence) str;
        }
        return str.toString();
    }

    /**
     * SCIPIO: Ensures the string does not pass the given length and returns
     * the first lengthLimit characters if longer, with extra suffix to
     * add if passes the length (e.g., "...").
     * <p>
     * Added 2018-09-17.
     */
    public static String limitLength(String str, int lengthLimit, String suffixIfOver) {
        if (str == null || str.length() <= lengthLimit) {
            return str;
        }
        return str.substring(0, lengthLimit) + suffixIfOver;
    }

    /**
     * SCIPIO: Ensures the string does not pass the given length and returns the
     * first lengthLimit characters if longer.
     * <p>
     * Added 2018-09-17.
     */
    public static String limitLength(String str, int lengthLimit) {
        if (str == null || str.length() <= lengthLimit) {
            return str;
        }
        return str.substring(0, lengthLimit);
    }

    /**
     * SCIPIO: Checks if the given string/StringBuilder starts with the given character.
     */
    public static boolean startsWith(CharSequence str, char suffix) {
        return (str.length() > 0) && (str.charAt(0) == suffix);
    }

    /**
     * SCIPIO: Checks if the given string/StringBuilder starts with the given prefix.
     */
    public static boolean startsWith(CharSequence str, CharSequence prefix) {
        return StringUtils.startsWith(str, prefix);
    }

    /**
     * SCIPIO: Checks if the given String/StringBuilder ends with the given character.
     */
    public static boolean endsWith(CharSequence str, char suffix) {
        return (str.length() > 0) && (str.charAt(str.length() - 1) == suffix);
    }

    /**
     * SCIPIO: Checks if the given String/StringBuilder ends with the given suffix.
     */
    public static boolean endsWith(CharSequence str, CharSequence suffix) {
        return StringUtils.endsWith(str, suffix);
    }

    /**
     * SCIPIO: Checks if the given object's {@link Object#toString()) value ends with the given character.
     * CharSequence-optimized.
     */
    public static boolean endsWith(Object str, char suffix) {
        return endsWith(asCharSequence(str), suffix);
    }

    /**
     * SCIPIO: Checks if the given object's {@link Object#toString()) value ends with the given suffix.
     * CharSequence-optimized.
     */
    public static boolean endsWith(Object str, CharSequence suffix) {
        return endsWith(asCharSequence(str), suffix);
    }

    /**
     * SCIPIO: Append the string to the buffer after removing the given suffixToRemove from the end of the string if present.
     * NOTE: Only removes one occurrence of the character.
     */
    public static void appendWithoutSuffix(Appendable buffer, CharSequence str, char suffixToRemove) throws IOException {
        if (endsWith(str, suffixToRemove)) {
            buffer.append(str, 0, str.length() - 1);
        } else {
            buffer.append(str);
        }
    }

    /**
     * SCIPIO: Append the string to the buffer after removing the given suffixToRemove from the end of the string if present.
     */
    public static void appendWithoutSuffix(Appendable buffer, CharSequence str, CharSequence suffixToRemove) throws IOException {
        if (endsWith(str, suffixToRemove)) {
            buffer.append(str, 0, str.length() - suffixToRemove.length());
        } else {
            buffer.append(str);
        }
    }

    /**
     * SCIPIO: Replaces the first characters of the string by the given character.
     * <p>
     * If maskLength is greater than zero, it determines the number of characters to mask from the left;
     * if negative, the number of characters to leave unmasked from the right;
     * if zero, returns the original string.
     * <p>
     * If maskLength is greater than zero and the string is less than maskLength,
     * returns a string of maskLength masked characters; if maskLength is negative and
     * the string is less than the absolute value of maskLength, returns the original string.
     */
    public static String maskLeft(CharSequence str, int maskLength, char maskChar) {
        if (str == null) {
            return null;
        } else if (str.length() == 0 || maskLength == 0) {
            return str.toString();
        }
        StringBuilder sb = new StringBuilder(str.length());
        if (maskLength < 0) {
            maskLength = (str.length() - (-maskLength));
            if (maskLength <= 0) {
                return str.toString();
            }
        }
        for(int i = 0; i < maskLength; i++) {
            sb.append(maskChar);
        }
        if (str.length() > maskLength) {
            sb.append(str, maskLength, str.length());
        }
        return sb.toString();
    }

    /**
     * SCIPIO: Replaces the last characters of the string by the given character.
     * <p>
     * If maskLength is greater than zero, it determines the number of characters to mask from the right;
     * if negative, the number of characters to leave unmasked from the left;
     * if zero, returns the original string.
     * <p>
     * If maskLength is greater than zero and the string is less than maskLength,
     * returns a string of maskLength masked characters; if maskLength is negative and
     * the string is less than the absolute value of maskLength, returns the original string.
     */
    public static String maskRight(CharSequence str, int maskLength, char maskChar) {
        if (str == null) {
            return null;
        } else if (str.length() == 0 || maskLength == 0) {
            return str.toString();
        }
        StringBuilder sb = new StringBuilder(str.length());
        int unmaskLength;
        if (maskLength < 0) {
            unmaskLength = -maskLength;
            maskLength = (str.length() - unmaskLength);
            if (maskLength <= 0) {
                return str.toString();
            }
        } else {
            unmaskLength = (str.length() - maskLength);
        }
        if (unmaskLength > 0) {
            sb.append(str, 0, unmaskLength);
        }
        for(int i = 0; i < maskLength; i++) {
            sb.append(maskChar);
        }
        return sb.toString();
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any number of mask characters to the left.
     */
    public static boolean matchesMaskedLeft(CharSequence plainStr, CharSequence maskedStr, char[] maskChars) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        int i = 0;
        while(i < maskedStr.length() && indexOf(maskChars, maskedStr.charAt(i)) >= 0) {
            i++;
        }
        return substringsEqual(plainStr, maskedStr, i, maskedStr.length());
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any number of mask characters to the left.
     */
    public static boolean matchesMaskedLeft(CharSequence plainStr, CharSequence maskedStr, char maskChar) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        int i = 0;
        while(i < maskedStr.length() && maskedStr.charAt(i) == maskChar) {
            i++;
        }
        return substringsEqual(plainStr, maskedStr, i, maskedStr.length());
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any number of mask characters  to the right.
     */
    public static boolean matchesMaskedRight(CharSequence plainStr, CharSequence maskedStr, char[] maskChars) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        int i = maskedStr.length();
        while(i > 0 && indexOf(maskChars, maskedStr.charAt(i - 1)) >= 0) {
            i--;
        }
        return substringsEqual(plainStr, maskedStr, 0, i);
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any number of mask characters  to the right.
     */
    public static boolean matchesMaskedRight(CharSequence plainStr, CharSequence maskedStr, char maskChar) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        int i = maskedStr.length();
        while(i > 0 && maskedStr.charAt(i - 1) == maskChar) {
            i--;
        }
        return substringsEqual(plainStr, maskedStr, 0, i);
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any mask characters at any place in the masked string.
     */
    public static boolean matchesMaskedAny(CharSequence plainStr, CharSequence maskedStr, char[] maskChars) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        for(int i = 0; i < maskedStr.length(); i++) {
            if (indexOf(maskChars, maskedStr.charAt(i)) < 0) {
                if (maskedStr.charAt(i) != plainStr.charAt(i)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * SCIPIO: Checks if the two strings match, ignoring any mask characters at any place in the masked string.
     */
    public static boolean matchesMaskedAny(CharSequence plainStr, CharSequence maskedStr, char maskChar) {
        if (maskedStr == null || plainStr == null || maskedStr.length() != plainStr.length()) {
            return false;
        }
        for(int i = 0; i < maskedStr.length(); i++) {
            if (maskedStr.charAt(i) != maskChar) {
                if (maskedStr.charAt(i) != plainStr.charAt(i)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * SCIPIO: Unsafe version of Apache commons ArrayUtils.indexOf (unsafe).
     */
    @SuppressWarnings("unused")
    private static int indexOf(final char[] array, final char valueToFind, int startIndex) {
        for (int i = startIndex; i < array.length; i++) {
            if (valueToFind == array[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * SCIPIO: Unsafe version of Apache commons ArrayUtils.indexOf (unsafe).
     */
    private static int indexOf(final char[] array, final char valueToFind) {
        for (int i = 0; i < array.length; i++) {
            if (valueToFind == array[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * SCIPIO: Checks if two strings of same size have same characters between the given indices (unsafe).
     */
    private static boolean substringsEqual(CharSequence str1, CharSequence str2, int startIndex, int endIndex) {
        for(int i = startIndex; i < endIndex; i++) {
            if (str1.charAt(i) != str2.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
