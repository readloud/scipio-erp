<#include "component://setup/webapp/setup/common/common.ftl">

<#-- FIXME?: either move or find better solution, but @cell works badly here, need float or inline-block -->
<style type="text/css">
  .setupAccounting-selectGL-select, .setupAccounting-importDefaultGL-select, .setupAccounting-selectGL-submit-buttons, .setupAccounting-importDefaultGL-submit-buttons {
    display:inline-block;
  }
  .setupAccounting-selectGL-select, .setupAccounting-importDefaultGL-select {
    max-width: 30%!important;
    width: 30%!important;
    margin-right:0.5em;
  }
  .setupAccounting-selectGL-select, .setupAccounting-importDefaultGL-select, .setupAccounting-selectGL-submit-buttons, .setupAccounting-importDefaultGL-submit-buttons {
    vertical-align:top; <#-- firefox align issue hack -->
  }
  .setupAccounting-preferences {
      clear: right;
  }
  
</style>

<@script>
    jQuery(document).ready(function() {
        var submitSelectAccountingForm = function(cntd) {
            var val = jQuery('#setupAccounting-selectGL-select').val();            
            if (val) {
                if (cntd) {
                    jQuery('#setupAccounting-selectContinueGL-form input[name=topGlAccountId]').val(val);
                    jQuery('#setupAccounting-selectContinueGL-form').submit();
                } else {
                    jQuery('#setupAccounting-selectGL-form').submit();
                } 
            } else {
                jQuery('#setupAccounting-newGL-form').submit();
            }
        };

        jQuery('#setupAccounting-selectGL-submit').click(function() {
            submitSelectAccountingForm(false);
        });
        jQuery('#setupAccounting-selectGL-submit-continue').click(function() {
            submitSelectAccountingForm(true);
        });
    });
</@script>
  <@form method="get" action=makePageUrl("setupAccounting") id="setupAccounting-selectGL-form">
    <#-- TODO: REVIEW: may make a difference later -->
    <@defaultWizardFormFields exclude=["topGlAccountId"]/>
    <#--<@field type="hidden" name="setupContinue" value="N"/> not needed yet-->
    
    <#if accountingGLs?has_content>
        <@field type="generic" label=uiLabelMap.SetupAccountingSelectStandardForSetup>
           <@field type="select" name="topGlAccountId" id="setupAccounting-selectGL-select" class="+setupAccounting-selectGL-select" inline=true>
                   <#-- This will be enabled in the future -->
                <#-- <option value="">[${uiLabelMap.SetupAccountingCreateNewStandard}]</option> -->
                <option value=""<#if accountingGLs?has_content> selected="selected"</#if>>--</option>            
                <#list accountingGLs as accountingGL>
                  <#assign selected = (raw(accountingGL.glAccountId) == raw(topGlAccountId!))>
                  <option value="${accountingGL.glAccountId!}"<#if selected> selected="selected"</#if>>${accountingGL.accountCode!} [${accountingGL.glAccountId!}]</option>
                </#list>
            </@field>
            <@menu type="button" id="setupAccounting-selectGL-submit-buttons" class="+setupAccounting-selectGL-submit-buttons">
              <@menuitem type="link" contentId="setupAccounting-selectGL-submit" href="javascript:void(0);" text=uiLabelMap.CommonSelect class="+${styles.action_run_session!} ${styles.action_update!}"/>
              <@menuitem type="link" contentId="setupAccounting-selectGL-submit-continue" href="javascript:void(0);" text=uiLabelMap.SetupSelectAndContinue class="+${styles.action_run_session!} ${styles.action_continue!} pull-right"/>                            
            </@menu>
        </@field>
    </#if>     
  </@form>
  
  <@form method="get" action=makePageUrl("setupWizard") id="setupAccounting-selectContinueGL-form">
    <#-- TODO: REVIEW: may make a difference later -->
    <@defaultWizardFormFields exclude=["topGlAccountId"]/>
    <#--<@field type="hidden" name="setupContinue" value="Y"/> not needed yet-->
  
    <@field type="hidden" name="topGlAccountId" value=""/>
  </@form>
  
  <#if !accountingGLs?has_content>
      <@alert type="warning">
          ${uiLabelMap.AccountingScipioAccountingStandardsInfo}
          <ul>          
              <#list scipioAcctgStandardAddons.keySet() as addon>
                  <li><a href="${scipioAcctgStandardAddons.get(addon)}">${addon}</a></li>
              </#list>
          </ul>
      </@alert>
      <@section title=uiLabelMap.SetupAccountingImportDefaultGLStandard>
          <@form method="post" action=makePageUrl("importDefaultGL") id="setupAccounting-importDefaultGL-form">
              <@defaultWizardFormFields exclude=["topGlAccountId"]/>
              <@field type="select" name="importPredefinedGL" id="setupAccounting-importDefaultGL-select" class="+setupAccounting-importDefaultGL-select" inline=true>
                  <#list scipioAcctgStandardDefaults as predefinedGL>
                      <option value="${predefinedGL!}">${predefinedGL!}</option>
                  </#list>
              </@field>
              <@menu type="button" id="setupAccounting-importDefaultGL-submit-buttons" class="+setupAccounting-importDefaultGL-submit-buttons">
                <@menuitem type="submit" contentId="setupAccounting-importDefaultGL-submit" href="javascript:void(0);" text=uiLabelMap.CommonImport class="+${styles.action_run_session!} ${styles.action_update!}"/>
            </@menu>
          </@form>
      </@section>
      <hr/>
  </#if>
  
  <#-- 
  <@form method="get" action=makePageUrl("setupAccounting") id="setupAccounting-newGL-form">   
    <@defaultWizardFormFields exclude=["topGlAccountId"]/>    
    <@field type="hidden" name="newGlAccount" value="Y"/>
  </@form>
  -->
  