<@virtualSection name="Global-Header" contains="!$Global-Column-Left, *">
<#-- Applications -->
<#if (requestAttributes.externalLoginKey)??><#assign externalKeyParam = "?externalLoginKey=" + (requestAttributes.externalLoginKey!)></#if>
<#if (externalLoginKey)??><#assign externalKeyParam = "?externalLoginKey=" + (requestAttributes.externalLoginKey!)></#if>
<#assign ofbizServerName = application.getAttribute("_serverId")!"default-server">
<#assign contextPath = request.getContextPath()>
<#if userLogin?has_content>
    <#assign displayApps = Static["org.ofbiz.webapp.control.LoginWorker"].getAppBarWebInfos(security, userLogin, ofbizServerName, "main")>
    <#assign displaySecondaryApps = Static["org.ofbiz.webapp.control.LoginWorker"].getAppBarWebInfos(security, userLogin, ofbizServerName, "secondary")>
    <#assign appModelMenu = Static["org.ofbiz.widget.model.MenuFactory"].getMenuFromLocation(applicationMenuLocation,applicationMenuName)>
</#if>
<#if person?has_content>
  <#assign userName = person.firstName!"" + " " + person.middleName!"" + " " + person.lastName!"">
<#elseif partyGroup?has_content>
  <#assign userName = partyGroup.groupName!>
<#elseif userLogin??>
  <#assign userName = userLogin.userLoginId>
<#else>
  <#assign userName = "">
</#if>
<#if defaultOrganizationPartyGroupName?has_content>
  <#assign orgName = " - " + defaultOrganizationPartyGroupName!>
<#else>
  <#assign orgName = "">
</#if>
<#macro generalMenu>
    <#if userLogin??>
        <#--
        <#if layoutSettings.topLines?has_content>
          <#list layoutSettings.topLines as topLine>
            <#if topLine.text??>
              <li class="">${topLine.text}<a href="${raw(topLine.url!)}${raw(externalKeyParam)}">${topLine.urlText!}</a></li>
            <#elseif topLine.dropDownList??>
              <li class=""><#include "component://common/webcommon/includes/insertDropDown.ftl"/></li>
            <#else>
              <li class="n">${topLine!}</li>
            </#if>
          </#list>
        <#else>
          <li class="">${userLogin.userLoginId}</li>
        </#if>
        -->
        <li><a href="<@pageUrl>ListLocales</@pageUrl>" class=""><i class="${styles.icon!} fa-language"></i> ${uiLabelMap.CommonLanguageTitle}</a></li>
        <li><a href="<@pageUrl>ListVisualThemes</@pageUrl>" class=""><i class="${styles.icon!} fa-photo"></i> ${uiLabelMap.CommonVisualThemes}</a></li>
    </#if>
    <#-- Disabled Help function for the time being
    <#if parameters.componentName?? && requestAttributes._CURRENT_VIEW_?? && helpTopic??>
        <#include "component://common/webcommon/includes/helplink.ftl" />
        <#assign portalPageParamStr><#if parameters.portalPageId?has_content>&portalPageId=${raw(parameters.portalPageId!)}</#if></#assign>
        <li class=""><@modal label=uiLabelMap.CommonHelp id="help" linkClass=""
            href=makePageUrl("showHelp?helpTopic=${raw(helpTopic!)}${portalPageParamStr}") icon="${styles.icon!} fa-info"></@modal></li>
    </#if>-->
    <#if userLogin??>
        <li class="active"><a href="<@pageUrl>logout?t=${.now?long}</@pageUrl>" class=""><i class="${styles.icon!} fa-power-off"></i> ${uiLabelMap.CommonLogout}</a></li>
    </#if>
</#macro>

<#macro primaryAppsMenu>
  <#assign appCount = 0>
  <#assign firstApp = true>
  <#--  <li class=""><label>${uiLabelMap["CommonPrimaryApps"]}</label></li>-->
  <#list displayApps as display>
        <#assign thisApp = display.getContextRoot()>
        <#assign selected = false>
        <#if thisApp == contextPath || contextPath + "/" == thisApp>
          <#assign selected = true>
        </#if>
        <#assign servletPath = Static["org.ofbiz.webapp.WebAppUtil"].getControlServletPathSafeSlash(display)!"">
        <#assign thisURL = raw(servletPath)>
        <#if thisApp != "/">
          <#if servletPath?has_content>
            <#assign thisURL = thisURL + "main">
          <#else>
            <#assign thisURL = thisApp>
          </#if>
        </#if>
        <#if layoutSettings.suppressTab?? && display.name == layoutSettings.suppressTab>
          <#-- do not display this component-->
        <#else>
            <li class="<#if selected> active</#if>">
                <a href="${thisURL}${raw(externalKeyParam)}" class="dropdown-item<#if selected> active</#if>"
                <#if uiLabelMap??> title="${uiLabelMap[display.description]}">
                    <#if styles.app_icon[display.name]?has_content><i class="${styles.icon!} ${styles.app_icon[display.name]}"></i> </#if>${uiLabelMap[display.title]}
                <#else> title="${display.description}">
                    ${display.title}
                </#if>
                </a>
            </li>
            <#assign appCount = appCount + 1>
        </#if>
  </#list>
</#macro>

<#macro secondaryAppsMenu>
    <#--<li class=""><label>${uiLabelMap["CommonSecondaryApps"]}</label></li>-->
    <#list displaySecondaryApps as display>
        <#assign thisApp = display.getContextRoot()>
        <#assign selected = false>
        <#if thisApp == contextPath || contextPath + "/" == thisApp>
          <#assign selected = true>
        </#if>
          <#assign servletPath = Static["org.ofbiz.webapp.WebAppUtil"].getControlServletPathSafeSlash(display)!"">
          <#assign thisURL = raw(servletPath)>
          <#if thisApp != "/">
            <#if servletPath?has_content>
              <#assign thisURL = thisURL + "main">
            <#else>
              <#assign thisURL = thisApp>
            </#if>
          </#if>
          <li class="<#if selected> active</#if>">      
            <a href="${thisURL}${raw(externalKeyParam)}" class="dropdown-item <#if selected> active</#if>"
                <#if uiLabelMap??> title="${uiLabelMap[display.description]}">
                    <#if styles.app_icon[display.name]?has_content><i class="${styles.icon!} ${styles.app_icon[display.name]}"></i> </#if>${uiLabelMap[display.title]}
                <#else> title="${display.description}">
                    ${display.title}
                </#if>
            </a>
            <#assign appCount = appCount + 1>
          </li>
    </#list>
</#macro>

<#macro notificationsMenu>
<ul class="">
        <li><div class="">${uiLabelMap["CommonLastSytemNotes"]}</div></li>
        <#list systemNotifications as notification>
            <li class="media-preview">
                <#if notification.url?has_content><#assign notificationUrl=addParamsToUrl(notification.url,{"scipioSysMsgId":notification.messageId})></#if>
                <a href="${notificationUrl!"#"}"  class="dropdown-item">
                    <div class="message_wrap <#if notification.isRead?has_content && notification.isRead=='Y'>message_isread</#if>">
                        <#--<div class="message_status">
                            <#if notification.fromPartyId?has_content> <span class="message_user"><small>${notification.fromPartyId!""}</small></span></#if>
                        </div>-->
                        <div class="message_header">
                            ${notification.title!getLabel("CommonNoTitle", "CommonUiLabels")!} <span class="message_time float-right">${notification.createdStamp?string.short}</span>
                        </div>
                        <div class="message_body">${notification.description!""}</div>
                    </div>
                </a>
            </li>
        </#list>
        <#-- 
        <li class="message-footer">
            <a href="#">
                <div>View All</div>
            </a>
        </li>-->
</ul>
</#macro>

<#-- in theory there is a transform that converts the selected menu to a proper list on these screens. It is never used by any of the other ofbiz screens, however and poorly documented
so for now we have to split the screens in half and rely on the menu widget renderer to get the same effect
<#macro currentAppMenu>
    <#if appModelMenu?has_content>
        <li class="has-dropdown not-click active"><a href="#">${title!"TEST"}</a>
            <ul class="dropdown">
                
            </ul>
        </li>
    </#if>
</#macro>-->

<#macro logoMenu hasLink=true isSmall=false>
    <#if layoutSettings.headerImageUrl??>
        <#assign headerImageUrl = layoutSettings.headerImageUrl>
    <#elseif layoutSettings.commonHeaderImageUrl??>
        <#assign headerImageUrl = layoutSettings.commonHeaderImageUrl>
    <#elseif layoutSettings.VT_HDR_IMAGE_URL??>
        <#assign headerImageUrl = layoutSettings.VT_HDR_IMAGE_URL.get(0)>
    </#if>
    <#if headerImageUrl??>
        <#if organizationLogoLinkURL?has_content>
            <#if hasLink><a href="<@pageUrl>${logoLinkURL}</@pageUrl>" class="navbar-brand"></#if><#if hasLink></a></#if>
            <#else><#if hasLink><a href="<@pageUrl>${logoLinkURL}</@pageUrl>" class="navbar-brand"></#if><#if hasLink></a></#if>
        </#if>
        <#else>
        <a href="<@pageUrl>${logoLinkURL!""}</@pageUrl>" class="navbar-brand"></a>
    </#if>
</#macro>

  <@scripts output=true> <#-- ensure @script elems here will always output -->

    <title>${layoutSettings.companyName}<#if title?has_content>: ${title}<#elseif titleProperty?has_content>: ${uiLabelMap[titleProperty]}</#if></title>
    
    <#if layoutSettings.shortcutIcon?has_content>
      <#assign shortcutIcon = layoutSettings.shortcutIcon/>
    <#elseif layoutSettings.VT_SHORTCUT_ICON?has_content>
      <#assign shortcutIcon = layoutSettings.VT_SHORTCUT_ICON.get(0)/>
    </#if>
    <#if shortcutIcon?has_content>
      <link rel="shortcut icon" href="<@contentUrl>${raw(shortcutIcon)}</@contentUrl>" />
    </#if>
    
    <#if layoutSettings.styleSheets?has_content>
        <#--layoutSettings.styleSheets is a list of style sheets. So, you can have a user-specified "main" style sheet, AND a component style sheet.-->
        <#list layoutSettings.styleSheets as styleSheet>
            <link rel="stylesheet" href="<@contentUrl>${raw(styleSheet)}</@contentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.VT_STYLESHEET?has_content>
        <#list layoutSettings.VT_STYLESHEET as styleSheet>
            <link rel="stylesheet" href="<@contentUrl>${raw(styleSheet)}</@contentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.rtlStyleSheets?has_content && langDir == "rtl">
        <#--layoutSettings.rtlStyleSheets is a list of rtl style sheets.-->
        <#list layoutSettings.rtlStyleSheets as styleSheet>
            <link rel="stylesheet" href="<@contentUrl>${raw(styleSheet)}</@contentUrl>" type="text/css"/>
        </#list>
    </#if>
    <#if layoutSettings.VT_RTL_STYLESHEET?has_content && langDir == "rtl">
        <#list layoutSettings.VT_RTL_STYLESHEET as styleSheet>
            <link rel="stylesheet" href="<@contentUrl>${raw(styleSheet)}</@contentUrl>" type="text/css"/>
        </#list>
    </#if>
    
    <#-- VT_TOP_JAVASCRIPT must always come before all others and at top of document -->
    <#if layoutSettings.VT_TOP_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_TOP_JAVASCRIPT)/>
        <#list layoutSettings.VT_TOP_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>

    <#-- VT_PRIO_JAVASCRIPT should come right before javaScripts (always move together with javaScripts) -->
    <#if layoutSettings.VT_PRIO_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_PRIO_JAVASCRIPT)/>
        <#list layoutSettings.VT_PRIO_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.javaScripts?has_content>
        <#--layoutSettings.javaScripts is a list of java scripts. -->
        <#-- use a Set to make sure each javascript is declared only once, but iterate the list to maintain the correct order -->
        <#assign javaScriptsSet = toSet(layoutSettings.javaScripts)/>
        <#list layoutSettings.javaScripts as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.VT_HDR_JAVASCRIPT?has_content>
        <#assign javaScriptsSet = toSet(layoutSettings.VT_HDR_JAVASCRIPT)/>
        <#list layoutSettings.VT_HDR_JAVASCRIPT as javaScript>
            <#if javaScriptsSet.contains(javaScript)>
                <#assign nothing = javaScriptsSet.remove(javaScript)/>
                <@script src=makeContentUrl(javaScript) />
            </#if>
        </#list>
    </#if>
    <#if layoutSettings.VT_EXTRA_HEAD?has_content>
        <#list layoutSettings.VT_EXTRA_HEAD as extraHead>
            ${extraHead}
        </#list>
    </#if>
    <#if lastParameters??><#assign parametersURL = "&amp;" + lastParameters></#if>
    <#if layoutSettings.WEB_ANALYTICS?has_content>
      <@script>
        <#list layoutSettings.WEB_ANALYTICS as webAnalyticsConfig>
          ${raw(webAnalyticsConfig.webAnalyticsCode!)}
        </#list>
      </@script>
    </#if>

  </@scripts>
</head>
<#if layoutSettings.headerImageLinkUrl??>
  <#assign logoLinkURL = "${layoutSettings.headerImageLinkUrl}">
<#else>
  <#assign logoLinkURL = "${layoutSettings.commonHeaderImageLinkUrl}">
</#if>
<#assign organizationLogoLinkURL = "${layoutSettings.organizationLogoLinkUrl!}">
<body class=" <#if activeApp?has_content>app-${activeApp}</#if><#if parameters._CURRENT_VIEW_?has_content> page-${parameters._CURRENT_VIEW_!}</#if> <#if userLogin??>sidebar-fixed page-auth<#else>page-noauth</#if>">
    <!-- Navigation -->
    <header class="">
        
            <button class="" type="button">&#9776;</button>
            <@logoMenu isSmall=true/>
            
            <!-- Top Menu Items -->
            <ul class="">
                <#if userLogin??>
                <li class="nav-item">
                     <a class="" href="#">&#9776;</a>
                </li>
                <li class="">
                    <a href="javascript:;" class="">${uiLabelMap["CommonPrimaryApps"]}</a>
                    <ul id="menuPrimary" class="">
                        <@primaryAppsMenu/>
                    </ul>
                </li>
                <li class="">
                    <a href="javascript:;" class="">${uiLabelMap["CommonSecondaryApps"]}</a>
                    <ul id="menuSecondary" class="">
                        <@secondaryAppsMenu/>
                    </ul>
                </li>
                </#if>
             </ul>
             
             <ul class="">
                <#if systemNotifications?has_content>
                      <li class=""><a href="#" class=""><#if systemNotificationsCount?has_content> <span class="label label-primary">${systemNotificationsCount}</span> <b class="caret"></b> </#if></a>
                        <@notificationsMenu />
                      </li>
                </#if>
                <li class="">
                    <#if userLogin??><a href="#" class="">${userLogin.userLoginId}</a><#else><a href="<@pageUrl>${checkLoginUrl}</@pageUrl>">${uiLabelMap.CommonLogin}</a></#if>
                    <ul class="">
                        <@generalMenu />
                    </ul>
                </li>
            </ul>
     </header>
</@virtualSection>