<#--
* 
* Master HTML template include, standard Scipio markup
*
* A set of HTML templating macros, part of the standard Scipio Templating Toolkit 
* for Freemarker language (>>>https://freemarker.apache.org/docs/<<<) - master include.
* Automatically included at all times, unless overridden by properties or themes.
* Intended to be swappable.
* Includes all other default Scipio standard markup macros.
*
* Alongside this master include exists a global variables and styles definition file,
* {{{htmlVariables.groovy}}}, which defines global styles used by most macros and utilities
* (currently has no HTML documentation - mostly relevant to theme writers - see source).
*
* NOTES:
* * Currently targeted toward Foundation CSS.
* * 2016-10-05: Value escaping/encoding behavior has changed and is now generally performed by macros
*   at point-of-use, consistently. See the related section below.
*
* IMPLEMENTATION NOTES: 
* * Macros should almost never use "request" object directly - use setRequestVar/getRequestVar/other.
*
* DEV NOTES: 
* * Some macros use attribs and inlineAttribs args to specify extra HTML attribs.
*   Even though it would be convenient, we can't allow a "attribStr" arg because no way
*   for macro to get attribs out of it if it needs them.
*   FIXME: Not all macros currently properly check attribMap for duplicate attribs
*       of args and inlineAttribs (priority should be: args - inlineAttribs - attribMap).
* * Documentation formatting is sensitive to whitespace, presence and number of asterisks (*),
*   and line endings. Must be spaces-only and LF-only line endings.
*   * In parser, bullets will create HTML lists, text encloding in two bullets (* *) will create
*     a heading, notes with uppercase label and "?:" or ":" prefix will be highlighted (support multi-
*     line by indenting the next line), and indents alone will create preformatted text (usually code).
*   * Small code bits may be surrounded by three curly brackets (like Trac tickets) to delineate code, {{{like this}}}.
*     Indents may be enough for other cases (but indents don't identify as code in HTML). The three curly brackets will also prevent auto-linking.
*   * To prevent auto-linking or other textual formatting (but not structural formatting), wrap in three parenthesis, (((like this))).
*   * Supports limited '''bold''', ''italic'', __underline__ in paragraphs (but not labels)
*   * Make links using three greater-than/less-than signs: >>>utilities.ftl<<<
*     Relative links are assumed to be from the root doc folder, NOT the current file, unless
*     it is prefixed with "./" or "../" in which case it is assumed relative to current file: >>>../utilities.ftl<<<,  >>>./htmlContent.ftl<<<
*     Text starting with http or https protocol are automatically linked.
*     .ftl is changed to .html.
*   * As last resort, short strings of raw HTML can be inserted by wrapping them with
*     three square brackets, [[[<em>like this</em>]]].
*     WARN: HTML should be avoided in these docs because it badly affects readability
*         of the plain text! Use sparingly. Acceptable for basic image (img elem) links.
*         An alternative is to use the three brackets to pass user-defined special characters
*         which your doc template can scan for and interpret as new text directives.
*
*
* * Macro Interfaces *
* 
* Scipio standard macros have versatile interfaces. There are template-facing macros (most of which support
* an advanced arguments interface) and a separate set of delegated macros for theme implementation (markup macros).
*
* ''General remarks:''
* * All macros expect to be invoked using named parameters only (always {{{<@row class="my-class">}}}, never {{{<@row "my-class">}}})
* * Functions in Freemarker only support positional arguments, but some Scipio functions support
*   an "args" argument as a map, which emulates named arguments. This is also done
*   as part of the advanced args interface (documented below).
* * Many macros have advanced, low-level open/close controls for esoteric structure control where the macro 
*   markup open and close tags and logic needs to be split or skipped. Generally their defaults are true; must specify
*     <@macroname open=true close=false /> 
*   for open-only and 
*     <@macroname close=true open=false /> 
*   for close-only.
*   Use of these in templates is discouraged but occasionally forcefully needed.
* * Patterns described here are often workarounds for limitations in the FTL language.
* * The advanced args pattern described below is primarily designed to be simple to use from templates, almost
*   indistinguishable from regular macro calls, but more powerful.
*   It also provides more power for theme overrides; however, theme overrides must be careful in their use of it;
*   it's better for themes to use simple markup overrides instead, which do not use the advanced pattern.
*
* ''Template-facing macros (advanced args pattern):''
* * These macros such as @field, @row, @heading, etc. are meant to be
*   used in templates and can also be overridden directly by themes (though not preferred method).
*   Most of these use a versatile args pattern that looks like: 
*     <#macro macroname args={} inlineArgs...>
* * When called from templates, these macros accept regular inlined parameters as well as a map of parameters
*   using the args map parameter. Intuitively, inline args have priority over args passed in the args map and in most cases simply override them.
*   This pattern is especially needed for themes to override the templating-facing macros cleanly and to provide a way
*   for template code to pass map content as arguments (not supported by Freemarker). It can be exploited 
*   in a large number of ways (pass maps from groovy, callback macros, simplifying repeat calls, etc.) otherwise not possible.
*   Some non-template-facing macros also use this pattern, and some functions partly use it as well (but
*   Freemarker functions do not support named parameters at this time, so only partial pattern).
*   Some macros with this pattern also accept additional arbitrary inlined parameters to be used as extra HTML attributes (that can be passed
*   both in args map or inlineArgs, but usually it's used to emulate HTML with macros, thus inlineArgs); 
*   these are not declared in the default argument lists; see individual macro documentation;
*   they are equivalent in usage to regular FTL interface macros that take extra attrbs as varargs
*   e.g. 
*     <#macro myhtmlmacro (...) inlineAttribs...> 
*   IMPL NOTE: Extra attributions are handled by a system that records args names in a "allArgNames" member in 
*       the args map through the mergeArgMaps function. See mergeArgMaps function in utilities library for more details.
* * Interface:
*     <#assign name_defaultArgs = { (...), "passArgs":{} }>
*     <#macro macroname args={} inlineArgs...>
*   * {{{args}}}: Macro args map. Map of parameters to pass to the macro. It can be a bean-wrapped map (from groovy/widgets) or simple FTL hash.
*     IMPL NOTE: The implementation should pass this to mergeArgMaps or equivalent function (see examples).
*   * {{{inlineArgs...}}}: Macro inline args. Map of parameters passed inline to the macro via usual macro call syntax.
*     these have priority over args and generally will replace entries in the args map, with rare exceptions where noted.
*     IMPL NOTE: The implementation should pass this to mergeArgMaps or equivalent function (see examples).
*   * {{{passArgs}}}: Pass-through args. Map of args that should be passed along the major calls made by this macro or in other words
*     passed through the whole call stack. It allows arguments to pass through from the templating-facing macro 
*     to the internal implementations such as markup macros, similar to a context. This is needed especially to allow theme overrides 
*     to communicate with their markup macros without the use of globals, but it may be used for any other purpose.
*     Be careful about using sufficiently unique names.
*     IMPL NOTE: Overriding macros should avoid overriding this map; 
*         always add new members to it instead. e.g. 
*       <@somemacro passArgs=(passArgs + {"myParam":"myValue")>
*
* ''Markup macros (theme overrides):''
* * These macros such as @row_markup, @heading_markup, etc. containing
*   the "_markup" name are overridable by themes to provide alternative HTML and sometimes javascript markup.
*   This is the simplest and preferred way to provide alternate markup.
*   They do not have a versatile interface like the template-facing macros and are intentionally kept
*   simple.
*   * Nevertheless, they have some requirements: these macros should always end their parameter list with
*     a varargs catch-all parameter "catchArgs..." so that future changes do not backwards break compability
*     with themes.
* * Interface:
*     <#macro macroname_markup (...) origArgs={} passArgs={} catchArgs...>
*   * {{{origArgs}}}: Original caller's args. map of complex parameters usually roughly as they were received by the calling macro. Rarely-used and should be
*     avoided in favor of the other simpler macro arguments passed by the caller, which are usually very similar. Is 
*     needed in rare cases where the simpler macro arguments are too simplistic or don't provide all the information needed.
*     NOTE: In general orig args do not come from a template-facing macro but from an intermediate macro
*         (such as @fieldset_core or @field_input_widget). This is the intention, as the former would break 
*         abstraction too much. In many cases however, the calling macro may happen to be 
*         a template-facing macro. Do not rely on this while using origArgs.
*   * {{{passArgs}}}: Pass-through args. Map of args that are passed through from the template-facing macro to the parent/caller macro to 
*     this macro or in other words passed through the whole call stack, similar to a context. This is needed especially to allow theme overrides 
*     to communicate with their markup macros without the use of globals, but it may be used for any other purpose.
*     Be careful about using sufficiently unique names.
*   * {{{catchArgs}}}: Catch-all args. Simply catches all the parameters the macro doesn't need to handle
*     NOTE: The previous parameters may be omitted and caught with catchArgs if unused.
*
*
* * Value escaping *
*
* ''2016-10-12: Added for 1.14.2.''

* Macros now generally implement html escaping, javascript string value escaping, and other escaping
* for strings at point-of-use in their markup implementations, across the board, irrespective
* of and in addition to automatic html escaping performed by the ofbiz freemarker renderer.  
* Callers can expect that string parameters will ''consistently'' be escaped for html by macros (either as element markup ("htmlmarkup") or attributes ("html")) and, when, 
* applicable and possible, javascript ("js"), and some others, except where otherwise noted.

* This does ''not'' apply to nested content ({{{#nested}}}) which receives no extra 
* escaping, nor to parameters that emulate nested content (usually named {{{xxxContent}}} or so), nor to parameters 
* that contain whole blocks of javascript code (read below). Nested content escaping must be handled by the caller, which
* in most cases for html is done automatically by the renderer's automatic html escaping. Exceptions will be noted in macro docs.
* 
* Implementation and manipulation of escaping behavior is mainly done through the functions #escapeVal, #rawString and #wrapAsRaw.
*
* ''Details and features:''
* * ''Double-escaping prevention'': Values affected by screen renderer auto-escaping can be passed as-is to macros, and the
*   macros will prevent double-escaping automatically. 
*   So the following (majority of cases) will work:
*     <@somemacro value=screenVar1/> <#- good; here, screenVar1 came from a groovy script, screen widget field assignment, or a call through {{{Static[""]}}} ->
*   ''However'', if a screen context variable is composed in or coerced to string before being passed to the macro, such as: 
*     <@somemacro value="${screenVar1}: ${screenVar2}"/> <#- bad ->
*     <@somemacro value=screenVar1?string/> <#- bad ->
*     <#assign macroValue>${screenVar1}: ${screenVar2}</#assign> <#- bad ->
*     <@somemacro value=macroValue/>
*   then double-escaping will occur because the coercion causes escaping to happen before the macro receives the value, and all type information is lost. 
*   The caller must prevent this using #raw (#rawString) (or equivalent):
*     <@somemacro value="${raw(screenVar1)}: ${raw(screenVar2)}"/> <#- good ->
*     <@somemacro value=raw(screenVar1)?string/> <#- works, but do not use; completely redundant ->
*     <#assign macroValue>${raw(screenVar1)}: ${raw(screenVar2)}</#assign> <#- good ->
*     <@somemacro value=macroValue/>
*   Note that none of these constructs will allow passing html markup; it is all escaped by the macro (unrelated to #rawString).
* * ''Bypassing and markup'': If the caller needs to pass preformed html markup to an escaped parameter, then #wrapAsRaw can be used.
*   For example, to add some html elements:
*     <@somemacro value=wrapAsRaw("<strong>${screenVar1}</strong>: <em>${screenVar2}</em>", 'htmlmarkup')/> <#- note the absence of rawString; var auto-escape wanted here ->
*   However, this form of call limits the macro by force-feeding it data in html format only. A better way is to specify ''both'' {{{htmlmarkup}}} and {{{raw}}} string 
*   so that the macro can select the most appropriate variant as needed, which future-proofs it (e.g. against switch to javascript or javascript additions):
*     <@somemacro value=wrapAsRaw({
*       "htmlmarkup": "<strong>${screenVar1}</strong>: <em>${screenVar2}</em>",
*       "raw": "${raw(screenVar1)}: ${raw(screenVar2)}"
*     })/>
*   Practical example (login.ftl):
*     <#assign labelUsername><i class="${styles.icon!} ${styles.icon_user!}"></i></#assign>
*     <@field ... label=wrapAsRaw({'htmlmarkup':labelUsername, 'raw':rawLabel('CommonUsername')})/>
*   NOTE: Using #rawString (or equivalent) alone is simpler and so preferable to #wrapAsRaw when possible (no markup needed), 
*       but both will be safe as long  as #wrapAsRaw specifies the correct, exact language(s) escaped. 
*       When #wrapAsRaw contains a "raw" alternative, it is as good as #rawString; but omitting "raw" can hurt the compatibility 
*       and future of templates that use the macros.
*   NOTE: For html, there is both a "htmlmarkup" and a "html" language identifier: 
*       "htmlmarkup" can be passed markup such as elements, as it will be inserted only in html code body where such is safe; but it will
*       not be used in html attributes.
*       "html" (normally escaped using freemarker's ?html or equivalent) on the other hand must be safe to insert into html attributes, 
*       so caller '''must not''' pass markup elements in it. Its use is more limited and only really useful for rare cases
*       where data was escaped as html too early (such that #rawString has no effect).
*       See the related functions (#wrapAsRaw, #escapeVal) for further details.
* * {{{attribs/inlineAttribs}}}: Macros that accept extra arbitrary attribs will automatically escape the values for html attributes.
*   ''However'', if the attribs contain any javascript, the macros cannot be aware of this, and the caller must escape the javascript.
*     <@somemacro attribs={"somejsattrib": "javascript:someFunction('${escapeVal(screenVar1, 'js')}');"}/> <#- (recommended) ->
*     <@somemacro attribs={"somejsattrib": "javascript:someFunction('${raw(screenVar1)?js_string}');"}/> <#- (also works, but not recommended) ->
*   This also applies to javascript html attributes in general, such as events.
* * ''Javascript'': Note that escaping javascript typically means escaping the values inserted as string literals, and never the whole javascript code or html attribute (e.g. events).
*   Arbitrary javascript code ''cannot'' be escaped safely; only strings and text within string literals ({{{""}}} {{{''}}}) can be. 
*   Therefore, macros which accept entire pieces of javascript code cannot escape it and 
*   the caller is responsible for escaping the strings inserted within it (using #escapeVal or {{{?js_string}}}).
* * ''URL macro parameters'': URL macro parameters have some extra special handling and are escaped by macros as full URLs. 
*   See #escapeFullUrl for details.
*
-->

<#-- 
*************************************
* EXTERNAL IMPORTS AND INCLUDES *
*************************************
* NOTE: Assumes utilities.ftl and htmlVariables.groovy included.
-->

<#-- (currently none) -->

<#-- As of dependencies rework, scipio libs should have no dependencies on the stock macro libraries.
    The stock macros are implemented using scipio libraries.
    There should be no circular dependencies.
<#if !(screenlib?? && screenlib?is_hash)>
  <#import "component://widget/templates/htmlScreenMacroLibrary.ftl" as screenlib>
</#if>
<#if !(formlib?? && formlib?is_hash)>
  <#import "component://widget/templates/htmlFormMacroLibrary.ftl" as formlib>
</#if>
<#if !(menulib?? && menulib?is_hash)>
  <#import "component://widget/templates/htmlMenuMacroLibrary.ftl" as menulib>
</#if>
<#if !(treelib?? && treelib?is_hash)>
  <#import "component://widget/templates/htmlTreeMacroLibrary.ftl" as treelib>
</#if>-->


<#-- 
*************************************
* API TEMPLATE MACROS AND INCLUDES *
*************************************
* Code this file is responsible for defining. Intended for use in production templates.
*
* DEV NOTES: 
* * Categories are loosely based on Foundation organization.
* * There may be circular dependencies between these includes. 
*   May not be avoidable without complicating further. 
-->

<#include "htmlScript.ftl">
<#include "htmlStructure.ftl">
<#include "htmlInfo.ftl">
<#include "htmlNav.ftl">
<#include "htmlContent.ftl">
<#include "htmlForm.ftl">

<#-- After everything included, create a copy of the namespace so that macros can access 
     their global variables without possibility of override (sometimes needed)
     NOTE: overriding themes will also make use of this. this variable name must be unique. -->
<#assign scipioStdTmplLib = copyObject(.namespace)>

