package com.ilscipio.scipio.solr;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;

/**
 * SCIPIO: Util dedicated for low-level solr query expression and string/term/phrase manipulation.
 * For higher-level query ops, see {@link SolrQueryUtil}.
 * <p>
 * NOTE: Some methods are Scipio-schema specific.
 * <p>
 * TODO/FIXME (2017-09-01):
 * <ul>
 * <li>{@link #escapeTermPlain} known issue - solr 5 query parser does not respect whitespace escape!
 *     and due to parser wierdness, quotes can't be used instead!
 * <li>{@link #extractTopTerms} is makeshift and best-effort only, there should be an ext lib for this.
 * </ul>
 */
public abstract class SolrExprUtil {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    // TODO: REVIEW: the "!" standalone character appears not recognized in solr 5 query parser;
    // it only works if space after is removed. but it shouldn't do any harm here so leaving in...
    static final Set<String> noPrefixTerms = UtilMisc.unmodifiableHashSet("AND", "OR", "NOT", "&&", "||", "!", "/*");
    static final Set<Character> noPrefixTermCharPrefixes = UtilMisc.unmodifiableHashSet('+', '-');
    static final Map<Character, Character> termEnclosingCharMap;
    static {
        Map<Character, Character> map = new HashMap<>();
        map.put('"', '"');
        map.put('{', '}');
        map.put('[', ']');
        map.put('(', ')');
        termEnclosingCharMap = Collections.unmodifiableMap(map);
    }

    static final Pattern solrFieldNameAllowedChars = Pattern.compile("[a-zA-Z0-9_-]");
    static final Pattern solrFieldNamePrefixExpr = Pattern.compile("^([a-zA-Z0-9_-]+:)(.*)$");

    protected SolrExprUtil() {
    }

    /**
     * Escapes all special solr/query characters in the given query term
     * <em>not</em> enclosed in quotes (single term).
     * At current time, this includes at least:
     * <code>+ - && || ! ( ) { } [ ] ^ " ~ * ? : \ /</code> and whitespace.
     * NOTE: The result should NOT be enclosed in quotes; use {@link SolrUtil#escapeTermForQuote} for that.
     * FIXME?: whitespace escaping appears to not always be honored by solr parser?...
     * @see SolrUtil#escapeTermForQuote
     */
    public static String escapeTermPlain(String term) {
        return ClientUtils.escapeQueryChars(term);
        // Reference implementation:
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < s.length(); i++) {
//          char c = s.charAt(i);
//          // These characters are part of the query syntax and must be escaped
//          if (c == '\\' || c == '+' || c == '-' || c == '!'  || c == '(' || c == ')' || c == ':'
//            || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
//            || c == '*' || c == '?' || c == '|' || c == '&'  || c == ';' || c == '/'
//            || Character.isWhitespace(c)) {
//            sb.append('\\');
//          }
//          sb.append(c);
//        }
//        return sb.toString();
    }

    static boolean isSyntaxDelimChar(char c) {
        return c == '+' || c == '-' || c == '!'  || c == '(' || c == ')' || c == ':'
                || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}' || c == '~'
                || c == '*' || c == '?' || c == '|' || c == '&'  || c == ';' || c == '/';
    }

    /**
     * Escapes all special solr/query characters in the given query term intended to be
     * enclosed in double-quotes (phrase).
     * At current time, this escapes the backslash and double-quote characters only.
     * @see escapeTermPlain
     */
    public static String escapeTermForQuote(String term) {
        final String s = term;
        // Reference implementation: http://api.drupalhelp.net/api/apachesolr/SolrPhpClient--Apache--Solr--Service.php/function/Apache_Solr_Service%3A%3AescapePhrase/5
        // TODO: REVIEW: make sure this actually corresponds to the solr/lucene parser implementation,
        // w.r.t. the backslash handling; the php reference might be unofficial...
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // there is no provided implementation for this...
            if (c == '\\' || c == '\"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes the term using {@link escapeTermForQuote} and returns it within double-quotes.
     * Convenience method.
     */
    public static String escapeTermAndQuote(String term) {
        return "\"" + escapeTermForQuote(term) + "\"";
    }

    /**
     * ABSTRACTED escaping method that will fully escape the given term using either {@link escapeTermAndQuote}
     * or {@link escapeTermPlain} or another, at its own discretion or based on configuration.
     * The result should NOT and NEVER be placed in quotes; it should be treated as containing its own quotes, even
     * if the escaping method is changed.
     * <p>
     * DEV NOTE: this is to factor out the escaping code to simplify things later, because solr is not
     * honoring <code>escapeTermPlain</code> as expected.
     * <p>
     * 2017-07-21: At current time, uses {@link escapeTermPlain} - SEE KNOWN ISSUES.
     */
    public static String escapeTermFull(String term) {
        return escapeTermPlain(term);
    }

    /**
     * BEST-EFFORT function to extract top-level terms in the solr query, quoted and unquoted.
     * WARN: RELIES ON WHITESPACE to split terms; this can't fully emulate the solr parser, which has a lot of quirks; we rely
     * on spaces, but solr parser inserts its own logical spaces, so this will never be exact.
     * can't reliably split on quote or parenthesis because of modifiers, which we're better off not
     * trying to deal with for now...
     * FIXME: only supports quotes and parenthesis
     * TODO: REVIEW: only partial solr syntax support
     * FIXME?: the quote escaping is probably not handled properly, solr parser may be different
     */
    public static List<String> extractTopTerms(String queryExpr) {
        List<String> terms = new ArrayList<>();

        queryExpr = queryExpr.trim().replaceAll("\\s+", " "); // normalize

        int backslashCount = 0;
        StringBuilder term = new StringBuilder();

        int i = 0;
        while (i < queryExpr.length()) {
            char c = queryExpr.charAt(i);
            int nextBackslashCount = 0;
            if (c == '\\') {
             // BEST-EFFORT emulate escaping (solr parser quirky)
                nextBackslashCount = backslashCount + 1;
                term.append(c);
            } else if (SolrExprUtil.isQueryCharEscaped(c, backslashCount)) {
                term.append(c);
            } else {
                if (termEnclosingCharMap.containsKey(c)) {
                    int endIndex = SolrExprUtil.findTermClosingCharIndex(queryExpr, i, c);
                    if (endIndex > i) {
                        term.append(queryExpr.substring(i, endIndex+1));
                        i = endIndex; // NOTE: gets ++ after
                    } else {
                        // BAD SYNTAX (probably): append the rest, even though it will probably fail...
                        term.append(queryExpr.substring(i));
                        i = queryExpr.length(); // abort
                    }
                } else if (c == ' ') {
                    if (term.length() > 0) terms.add(term.toString());
                    term = new StringBuilder();
                } else {
                    term.append(c);
                }
            }
            backslashCount = nextBackslashCount;
            i++;
        }
        if (term.length() > 0) terms.add(term.toString());

        return terms;
    }

    static boolean isQueryCharEscaped(char c, int backslashCount) {
        // TODO: REVIEW: simplified escaping logic, works in some languages,
        // but solr parser might not...
        // WARN: 2017-08-25: solr parser 5 doesn't seem to respect whitespace escaping, but
        // because not sure if bug, will honor it here for now (not making special case)...
        return ((backslashCount % 2) != 0);
    }

    static boolean isQueryCharEscaped(String queryExpr, int charIndex, char theChar) {
        int backslashCount = 0;
        for(int i = (charIndex-1); i >= 0; i--) {
            char c = queryExpr.charAt(i);
            if (c == '\\') backslashCount++;
            else break;
        }
        return isQueryCharEscaped(theChar, backslashCount);
    }

    static boolean isQueryCharEscaped(String queryExpr, int charIndex) {
        return isQueryCharEscaped(queryExpr, charIndex, queryExpr.charAt(charIndex));
    }

    static int findTermClosingCharIndex(String queryExpr, int start, char openChar) {
        int i = start + 1;
        char closingChar = termEnclosingCharMap.get(openChar); // NPE if bad openChar
        if (openChar == '"') { // for quote, can ignore all chars except quote and backslash
            int backslashCount = 0;
            while (i < queryExpr.length()) {
                char c = queryExpr.charAt(i);
                if (c == '"' && !isQueryCharEscaped(c, backslashCount)) {
                    return i;
                } else if (c == '\\') {
                    backslashCount += 1;
                } else {
                    backslashCount = 0;
                }
                i++;
            }
        } else { // for parenthesis, must be careful about nested paren AND quotess
            int backslashCount = 0;
            while (i < queryExpr.length()) {
                char c = queryExpr.charAt(i);
                if (c == '\\') {
                    backslashCount += 1;
                } else {
                    if (!isQueryCharEscaped(c, backslashCount)) {
                        if (c == closingChar) {
                            return i;
                        } else if (c == openChar) {
                            int endParenIndex = findTermClosingCharIndex(queryExpr, i, c); // RECURSE
                            if (endParenIndex > i) {
                                i = endParenIndex; // NOTE: gets ++ after
                            } else {
                                return -1; // abort
                            }
                        } else if (c == '"') {
                            int endQuoteIndex = findTermClosingCharIndex(queryExpr, i, c); // RECURSE
                            if (endQuoteIndex > i) {
                                i = endQuoteIndex; // NOTE: gets ++ after
                            } else {
                                return -1; // abort
                            }
                        }
                    }
                    backslashCount = 0;
                }
                i++;
            }
        }
        return -1;
    }

    /**
     * Escapes an identifier for use in a Solr field name, in any part
     * of the name EXCEPT the beginning, with best-effort attempt to preserve
     * identifier uniqueness (best-effort only).
     * <p>
     * This method attempts to make the escaped name part as unique as possible, but
     * because of the restrictive character set, this is NOT guaranteed.
     * For this reason, this method should be used sparingly, and effort should
     * be taken to try to detect duplicates by the caller and warn users if they occur.
     * <p>
     * This assumes the name part will NOT be at the start of the field name;
     * Solr field names must always start with a letter, which is not and cannot be
     * enforced by this method.
     * <p>
     * Solr field name rules (https://lucene.apache.org/solr/guide/6_6/defining-fields.html):
     * "Field names should consist of alphanumeric or underscore characters only
     * and not start with a digit. This is not currently strictly enforced, but
     * other field names will not have first class support from all components and
     * back compatibility is not guaranteed. Names with both leading and trailing
     * underscores (e.g., _version_) are reserved."
     * <p>
     * NOTE: 2018-05-29: This method currently works by URL-encoding the whole string and
     * then replacing the "%" characters with "_".
     * This is SUBJECT TO CHANGE and should not be relied on for permanent value storage.
     * <p>
     * Added 2018-05-29.
     */
    public static String escapeFieldNamePart(String namePart) {
        try {
            namePart = URLEncoder.encode(namePart, "UTF-8");

            // handle special chars not covered by URLEncoder, including %,
            // which gets converted to underscore
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < namePart.length(); i++) {
                char c = namePart.charAt(i);
                switch(c) {
                case '%':
                    sb.append("_");
                    break;
                case '-':
                    sb.append("_2D");
                    break;
                case '.':
                    sb.append("_2E");
                    break;
                case '+':
                    sb.append("_2B");
                    break;
                case '*':
                    sb.append("_2A");
                    break;
                default:
                    sb.append(c);
                }
            }
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            Debug.logError(e, module); // this will never happen
            return namePart;
        }
    }

    /**
     * BEST-EFFORT function that attempts to add a prefix ("+" or "-") to every term in the given
     * queryExpr.
     * TODO: REVIEW: should find a solr expression for this OR will need more work as time goes on...
     */
    public static String addPrefixToAllTerms(String queryExpr, String prefix) {
        return StringUtils.join(SolrExprUtil.addPrefixToAllTerms(extractTopTerms(queryExpr), prefix), " ");
    }

    public static List<String> addPrefixToAllTerms(List<String> terms, String prefix) {
        List<String> newTerms = new ArrayList<>(terms.size());
        Set<Character> noCharPrefix = noPrefixTermCharPrefixes;
        if (prefix.length() == 1) { // optimization
            noCharPrefix = new HashSet<>(noCharPrefix);
            noCharPrefix.add(prefix.charAt(0));
            for(String term : terms) {
                if (!term.isEmpty() && !noPrefixTerms.contains(term) && !noCharPrefix.contains(term.charAt(0))) {
                    newTerms.add(prefix + term);
                } else {
                    newTerms.add(term);
                }
            }
        } else {
            for(String term : terms) {
                if (!term.isEmpty() && !noPrefixTerms.contains(term) && !noCharPrefix.contains(term.charAt(0)) && !term.startsWith(prefix)) {
                    newTerms.add(prefix + term);
                } else {
                    newTerms.add(term);
                }
            }
        }
        return newTerms;
    }

    public enum WildcardMode {
        /**
         * Produces wildcard term in the form: *xxx*
         */
        NGRAM,
        /**
         * Produces wildcard term in the form: xxx*
         */
        EDGE_NGRAM_FRONT,
        /**
         * Produces wildcard term in the form: *xxx
         */
        EDGE_NGRAM_BACK,
        /**
         * Produces wildcard term in the form: xxx* *xxx
         */
        EDGE_NGRAM_BOTH
    }

    /**
     * Transforms all the simple terms in the query into wildcarded terms ORed in parenthesis.
     * Simple pre-parser to implement partial word matching in the query itself, instead of using Ngram filters.
     * <p>
     * Ignores quoted terms.
     * <p>
     * For example, with WildcardMode=EDGE_NGRAM_BOTH, retainNonWild=true, wildWeight="0.5":
     *   "hello world"
     * become:
     *   "(hello OR hello*^0.5 OR *hello^0.5) (world OR world*^0.5 OR *world^0.5)"
     *
     * TODO: currently only supports the edismax syntax elements most used by common users.
     * Missing (not exhaustive): fuzzy/prox search (~), single wildcard (?), range searches...
     *
     * @param queryExpr the input query expression
     * @param wildcardMode
     * @param retainNonWild if true, include non-wildcard expression with optional different weight
     * @param nonWildWeight weight (^) added to the non-wildcard expression, if present
     * @param wildWeight weight (^) added to the wildcard expressions
     * @param minTermSize min term size (do not expand if smaller)
     * @param maxTermSize max term size (do not expand if larger)
     * @param deep whether first level only or also do nested expressions in parenthesis too
     * @return the transformed query expression
     */
    public static String makeWildcardSimpleSearchTerms(String queryExpr, WildcardMode wildcardMode, boolean retainNonWild,
            String nonWildWeight, String wildWeight, Integer minTermSize, Integer maxTermSize, boolean deep) {
        return StringUtils.join(makeWildcardSimpleSearchTerms(extractTopTerms(queryExpr), wildcardMode, retainNonWild, nonWildWeight, wildWeight, minTermSize, maxTermSize, deep), " ");
    }

    public static List<String> makeWildcardSimpleSearchTerms(List<String> terms, WildcardMode wildcardMode, boolean retainNonWild,
            String nonWildWeight, String wildWeight, Integer minTermSize, Integer maxTermSize, boolean deep) {
        if (minTermSize == null) minTermSize = 0;
        if (maxTermSize == null) maxTermSize = Integer.MAX_VALUE;
        List<String> newTerms = new ArrayList<>(terms.size());
        for(String term : terms) {
            String resultTerm = term;
            if (!term.isEmpty() && !noPrefixTerms.contains(term)) {
                String coreTerm = term;
                char first = coreTerm.charAt(0);
                String prefix = "";
                if (first == '+' || first == '-' || first == '!') {
                    prefix = String.valueOf(first);
                    coreTerm = coreTerm.substring(1);
                }
                Matcher m = solrFieldNamePrefixExpr.matcher(coreTerm);
                if (m.matches()) {
                    prefix += m.group(1);
                    coreTerm = m.group(2);
                }
                if (!coreTerm.isEmpty() && (!isSyntaxDelimChar(coreTerm.charAt(0)) || (deep && first == '('))) {
                    // FIXME: missing escape handling (rarely used from public)
                    String[] weightParts = StringUtils.split(coreTerm, "^", 2);
                    String suffix = "";
                    if (weightParts.length == 2) {
                        coreTerm = weightParts[0];
                        suffix = "^" + weightParts[1];
                    }
                    char last = coreTerm.charAt(coreTerm.length() - 1);

                    // if already a wildcard, skip it (leading wildcard counted in isSyntaxDelimChar)
                    if (last != '*') {
                        if (first == '(') {
                            if (last == ')' && coreTerm.length() >= 3) {
                                // recursion for parenthesis (must be well-formed, otherwise don't touch)
                                StringBuilder sb = new StringBuilder(prefix);
                                sb.append("(");

                                String subQueryExpr = coreTerm.substring(1, coreTerm.length() - 1);
                                sb.append(makeWildcardSimpleSearchTerms(subQueryExpr, wildcardMode, retainNonWild, nonWildWeight, wildWeight, minTermSize, maxTermSize, deep));

                                sb.append(")");
                                sb.append(suffix);
                                resultTerm = sb.toString();
                            }
                        } else if (coreTerm.length() >= minTermSize && coreTerm.length() <= maxTermSize) {
                            // replace "term" with "(term term* *term)" or as requested
                            StringBuilder sb = new StringBuilder(prefix);
                            sb.append("(");

                            if (retainNonWild) {
                                sb.append(coreTerm);
                                if (nonWildWeight != null) {
                                    sb.append("^");
                                    sb.append(nonWildWeight);
                                }
                                sb.append(" OR ");
                            }

                            if (wildcardMode == WildcardMode.EDGE_NGRAM_BOTH) {
                                if (wildWeight != null) {
                                    sb.append(coreTerm);
                                    sb.append("*^");
                                    sb.append(wildWeight);
                                    sb.append(" OR *");
                                    sb.append(coreTerm);
                                    sb.append("^");
                                    sb.append(wildWeight);
                                } else {
                                    sb.append(coreTerm);
                                    sb.append("* OR *");
                                    sb.append(coreTerm);
                                }
                            } else if (wildcardMode == WildcardMode.EDGE_NGRAM_FRONT) {
                                if (wildWeight != null) {
                                    sb.append(coreTerm);
                                    sb.append("*^");
                                    sb.append(wildWeight);
                                } else {
                                    sb.append(coreTerm);
                                    sb.append("*");
                                }
                            } else if (wildcardMode == WildcardMode.EDGE_NGRAM_BACK) {
                                sb.append("*");
                                sb.append(coreTerm);
                                if (wildWeight != null) {
                                    sb.append("^");
                                    sb.append(wildWeight);
                                }
                            } else { // NGRAM
                                sb.append("*");
                                sb.append(coreTerm);
                                sb.append("*");
                                if (wildWeight != null) {
                                    sb.append("^");
                                    sb.append(wildWeight);
                                }
                            }

                            sb.append(")");
                            sb.append(suffix);
                            resultTerm = sb.toString();
                        }
                    }
                }
            }
            newTerms.add(resultTerm);
        }
        return newTerms;
    }

    /**
     * Makes an expression to match a category ID for a special category field, whose values
     * are in the format: <code>X/PARENT/CATEGORY</code> (where X is the category depth);
     * assumes the passed category ID is already escaped.
     * NOTE: the field name is not escaped (should be hardcoded).
     */
    public static String makeCategoryIdFieldQueryRaw(String fieldName, String escapedProductCategoryId, boolean includeSubCategories) {
        // can be:
        // */CATID
        // */CATID/*
        // NOTE: at this time, should not be any CATID/*,
        // because there should always be a category depth as first entry (this was the chosen convention)
        StringBuilder sb = new StringBuilder();
        sb.append(fieldName);
        sb.append(":(*\\/");
        sb.append(escapedProductCategoryId);
        if (includeSubCategories) {
            sb.append("* *\\/");
            sb.append(escapedProductCategoryId);
            sb.append("\\/*)");
        } else {
            sb.append(")");
        }
        return sb.toString();
    }

    /**
     * Makes an expression to match a category ID for a special category field, whose values
     * are in the format: <code>X/PARENT/CATEGORY</code>, and automatically escapes the passed category ID.
     * NOTE: the field name is not escaped (should be hardcoded).
     */
    public static String makeCategoryIdFieldQueryEscape(String fieldName, String escapedProductCategoryId, boolean includeSubCategories) {
        return makeCategoryIdFieldQueryRaw(fieldName, escapeTermFull(escapedProductCategoryId), includeSubCategories);
    }

    public enum UserQueryMode {
        USER("user"),
        FULL("full"),
        LITERAL("literal");

        private static final Map<String, UserQueryMode> nameMap;
        static {
            Map<String, UserQueryMode> m = new HashMap<>();
            for(UserQueryMode u : UserQueryMode.values()) {
                m.put(u.getName(), u);
            }
            nameMap = m;
        }

        private final String name;

        private UserQueryMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static UserQueryMode fromName(String name) throws IllegalArgumentException {
            UserQueryMode res = nameMap.get(name);
            if (res == null) throw new IllegalArgumentException("invalid user query mode name: " + name);
            return res;
        }

        public static UserQueryMode fromNameSafe(String name) throws IllegalArgumentException {
            return nameMap.get(name);
        }
    }

    /**
     * Applies extra custom pre-parsing to user queries depending on the mode.
     * This is an extra factoring point for custom pre-parsing at point of input.
     * NOTE: 2017-09-05: The USER mode currently has no effect; it will be done
     * via dismax/edismax.
     */
    public static String preparseUserQuery(String userQuery, UserQueryMode mode) {
        if (UtilValidate.isEmpty(userQuery)) return userQuery;
        if (mode == UserQueryMode.LITERAL) {
            return escapeTermFull(userQuery);
        } else if (mode == UserQueryMode.FULL) {
            // do nothing
            return userQuery;
        } else {
            // leaving to (e)dismax
            return userQuery;
        }
    }

    public static String preparseUserQuery(String userQuery, String mode) throws IllegalArgumentException {
        return preparseUserQuery(userQuery, UtilValidate.isNotEmpty(mode) ? UserQueryMode.fromName(mode) : null);
    }

    public static String makeSortFieldFallbackExpr(List<String> fieldNames) {
        if (fieldNames.size() == 0) return null;
        ListIterator<String> it = fieldNames.listIterator(fieldNames.size());
        String expr = it.previous();
        while(it.hasPrevious()) {
            String fieldName = it.previous();
            expr = makeIfExistsExpr(fieldName, fieldName, expr);
        }
        return expr;
    }

    public static String makeSortFieldFallbackExpr(String... fieldNames) {
        return makeSortFieldFallbackExpr(Arrays.asList(fieldNames));
    }

    public static String makeIfExistsExpr(String existsExpr, String trueExpr, String falseExpr) {
        StringBuilder sb = new StringBuilder();
        appendIfExistsExpr(sb, existsExpr, trueExpr, falseExpr);
        return sb.toString();
    }

    public static void appendIfExistsExpr(StringBuilder sb, String existsExpr, String trueExpr, String falseExpr) {
        sb.append("if(exists(");
        sb.append(existsExpr);
        sb.append("),");
        sb.append(trueExpr);
        sb.append(",");
        sb.append(falseExpr);
        sb.append(")");
    }

    /**
     * Formats a timestamp in Solr-recognized format for insertion in
     * double-quotes or range expression.
     * <p>
     * Currently (2018-05-25) this formats in
     * {@link java.time.format.DateTimeFormatter#ISO_INSTANT} format.
     * <p>
     * Added 2018-05-25.
     */
    public static String formatTimestampForQuote(Date timestamp) {
        return DateTimeFormatter.ISO_INSTANT.format(timestamp.toInstant());
    }

    /**
     * Creates a filter expression that returns documents with the given date field after the passed timestamp
     * or having the field unset.
     */
    public static String makeDateFieldAfterOrUnsetExpr(String fieldName, Date timestamp) {
        return "((*:* NOT " + fieldName + ":*) OR " + fieldName + ":[" + formatTimestampForQuote(timestamp) + " TO *])";
    }

    /**
     * Creates a filter expression that returns documents with the given date field before the passed timestamp
     * or having the field unset.
     */
    public static String makeDateFieldBeforeOrUnsetExpr(String fieldName, Date timestamp) {
        return "((*:* NOT " + fieldName + ":*) OR " + fieldName + ":[* TO " + formatTimestampForQuote(timestamp) + "])";
    }
}
